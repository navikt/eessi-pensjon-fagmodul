package no.nav.eessi.pensjon.api.gjenny

import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.api.PrefillController
import no.nav.eessi.pensjon.fagmodul.api.SedController
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.ValidBucAndSed
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@Unprotected
@RestController
@RequestMapping("/gjenny")
/**
 * Endepunkter for Gjenny
 */
class GjennyController (
    private val euxInnhentingService: EuxInnhentingService,
    private val innhentingService: InnhentingService,
    private val prefillController: PrefillController,
    private val sedController: SedController,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(GjennyController::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private lateinit var bucerForGjenny: MetricsHelper.Metric
    private lateinit var bucerForAvdodGjenny: MetricsHelper.Metric
    private lateinit var bucViewGjenny: MetricsHelper.Metric

    init {
        bucerForGjenny = metricsHelper.init("bucerForGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerForAvdodGjenny = metricsHelper.init("bucerForAvdodGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucViewGjenny = metricsHelper.init("BucView", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @GetMapping("/bucs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucs() = ValidBucAndSed.pensjonsBucerForGjenny()

    @PostMapping("/buc/{buctype}")
    fun createBuc(@PathVariable("buctype", required = true) buctype: String,
                   @RequestBody(required = true) gjennySak: GjennySak):
        BucAndSedView = prefillController.createBuc(buctype, gjennySak).also { logger.info("Create buc for gjenny: ${it.caseId}, buctype: $buctype") }

    @GetMapping("/rinasaker/{aktoerId}/avdodfnr/{avdodfnr}")
    fun getGjenlevendeRinasakerAvdodGjenny(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("avdodfnr", required = true) avdodfnr: String,
    ): List<EuxInnhentingService.BucView> {
        return bucerForAvdodGjenny.measure {
            val start = System.currentTimeMillis()

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId")

            //api: henter rinasaker fra eux
            val avdodesSakerFraRina = euxInnhentingService.hentBucViewAvdod(avdodfnr, aktoerId)

            if (avdodesSakerFraRina.isEmpty()) {
                return@measure emptyList<EuxInnhentingService.BucView>()
                    .also { loggTimeAndViewSize("Get gjenlevende Rinasaker for Gjenny", start, 0) }
            }
            //api: brukersaker fra Joark/saf
            val brukerRinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(aktoerId)

            //api: avdødSaf + avdødUtenSaf + avdødsaf + safBruker
            val viewTotal = euxInnhentingService.hentViewsForSafOgRinaForAvdode(
                listOf(avdodfnr),
                aktoerId,
                null,
                brukerRinaSakIderFraJoark
            ).filter { it.buctype !in listOf(P_BUC_01, P_BUC_03) }

            return@measure viewTotal.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also { logger.info("getGjenlevendeRinasakerAvdodGjenny: view size: ${it.size}, total tid: ${System.currentTimeMillis()-start} i ms") }
        }
    }

    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasakerBrukerkontekstGjenny(
        @PathVariable("aktoerId", required = true) aktoerId: String
    ): List<EuxInnhentingService.BucView> {
        return bucViewGjenny.measure {
            val start = System.currentTimeMillis()
            val timeTracking = mutableListOf<String>()

            //api: henter fnr fra aktørid
            val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerId)

            //api: henter rinasaker basert på tidligere journalførte saker
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)
                .also { timeTracking.add("rinaSakIderFraJoark tid: ${System.currentTimeMillis()-start} i ms") }

            //api: bruker saker fra eux/rina
            val brukerView = gjenlevendeFnr?.let { euxInnhentingService.hentBucViewBruker(it.id, aktoerId, null) }.also {
                timeTracking.add("hentBucViewBruker, gjenlevendeFnr tid: ${System.currentTimeMillis()-start} i ms")
            }?: emptyList()

            //filter: brukersaker fra saf
            val filterBrukerRinaSakIderFraJoark = rinaSakIderFraJoark.filterNot { rinaid -> rinaid in brukerView.map { it.euxCaseId }  }

            //api: saker fra saf og eux/rina
            val safView = euxInnhentingService.lagBucViews(
                aktoerId,
                null,
                filterBrukerRinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            ).also {timeTracking.add("hentBucViews tid: ${System.currentTimeMillis()-start} i ms")}

            val view = (brukerView + safView)
                .filter { it.buctype !in listOf(P_BUC_01, P_BUC_03) }
                .also { logger.info("Antall for brukerview+safView: ${it.size}") }

            return@measure view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also {
                    logger.info("Tidsbruk for getRinasakerBrukerkontekst: \n"+timeTracking.joinToString("\n").trimIndent())
                }
        }
    }

    @PostMapping("/sed/add")
    fun leggTilInstitusjon(@RequestBody request: ApiRequest): DocumentsItem? {
        return prefillController.addInstutionAndDocument(request.copy(gjenny = true)).also { logger.info("Legg til institusjon fra gjenny for ${request.sed}, rinaid: ${request.euxCaseId}, sedid: ${request.documentid}") }
    }

    @PostMapping("/sed/replysed/{parentid}")
    fun prefillSed(
        @RequestBody(required = true) request: ApiRequest,
        @PathVariable("parentid", required = true) parentId: String
    ): DocumentsItem? = prefillController.addDocumentToParent(request.copy(gjenny = true), parentId).also { logger.info("Prefil fra gjenny for ${request.sed}, rinaid: ${request.euxCaseId}, sedid: ${request.documentid}") }

    @PutMapping("/sed/document/{euxcaseid}/{documentid}")
    fun oppdaterSed(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String,
        @RequestBody sedPayload: String
    ): Boolean = sedController.putDocument(euxcaseid, documentid, sedPayload)


    private fun loggTimeAndViewSize(servicename: String, start: Long, viewsize: Long = 0) {
        logger.info("""
                Total view size is $viewsize
                $servicename -> total tid: ${System.currentTimeMillis() - start} in ms
                """.trimIndent()
        )
    }
}