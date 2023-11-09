package no.nav.eessi.pensjon.api.gjenny

import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/gjenny")
class GjennyController (
    private val euxInnhentingService: EuxInnhentingService,
    private val innhentingService: InnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(GjennyController::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private lateinit var bucerForGjenny: MetricsHelper.Metric
    private lateinit var bucerForAvdodGjenny: MetricsHelper.Metric


    init {
        bucerForGjenny = metricsHelper.init("bucerForGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerForAvdodGjenny = metricsHelper.init("bucerForAvdodGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @GetMapping("/rinasaker/{aktoerId}/avdodfnr/{avdodfnr}")
    fun getGjenlevendeRinasakerAvdodGjenny(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("avdodfnr", required = true) avdodfnr: String,
    ): List<EuxInnhentingService.BucView> {
        return bucerForAvdodGjenny.measure {
            val start = System.currentTimeMillis()

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId")

            val avdodesSakerFraRina = euxInnhentingService.hentBucViewAvdod(avdodfnr, aktoerId)

            if (avdodesSakerFraRina.isEmpty()) {
                return@measure emptyList<EuxInnhentingService.BucView>()
                    .also {
                        logger.info("""
                            Total view size is zero
                            GetGjenlevendeRinasakerVedtak -> BrukerRinasaker total tid: ${System.currentTimeMillis() - start} i ms
                            """.trimIndent())
                    }
            }

            //brukersaker fra Joark/saf
            val brukerRinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)

            //filter avdodview for match på filterBrukersakerRina
            val avdodViewSaf = avdodesSakerFraRina
                .filter { view -> view.euxCaseId in brukerRinaSakIderFraJoark }
                .map { view ->
                    view.copy(kilde = EuxInnhentingService.BucViewKilde.SAF)
                }

            //avdod saker view uten saf
            val avdodViewUtenSaf = avdodesSakerFraRina.filterNot { view -> view.euxCaseId in avdodViewSaf.map { it.euxCaseId  } }

            //liste over saker fra saf som kan hentes
            val filterAvdodRinaSakIderFraJoark = brukerRinaSakIderFraJoark.filterNot { rinaid -> rinaid in avdodesSakerFraRina.map { it.euxCaseId }  }

            //saker fra saf og eux/rina
            val safView = euxInnhentingService.lagBucViews(
                aktoerId,
                null,
                filterAvdodRinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            )

            //saf filter mot avdod
            val safViewAvdod = safView
                .filter { view -> view.buctype in EuxInnhentingService.bucTyperSomKanHaAvdod }
                .map { view -> view.copy(avdodFnr = avdodfnr) }
                .also { if (avdodesSakerFraRina.size == 2) logger.warn("finnes 2 avdod men valgte første, ingen koblinger")}

            //saf filter mot bruker
            val safViewBruker = safView
                .filterNot { view -> view.euxCaseId in safViewAvdod.map { it.euxCaseId } }

            //samkjøre til megaview
            val viewTotal = avdodViewSaf + avdodViewUtenSaf + safViewAvdod + safViewBruker  // avdødSaf + avdødUtenSaf + avdødsaf + safBruker

            logger.info("""getGjenlevendeRinasakerAvdodGjenny GJENNY resultat: 
                avdodView : ${avdodesSakerFraRina.size}
                brukerRinaSakIderFraJoark: ${brukerRinaSakIderFraJoark.size}
                avdodViewUtenSaf: ${avdodViewUtenSaf.size}
                filterAvodRinaSakIderFraJoark: ${filterAvdodRinaSakIderFraJoark.size}
                safView: ${safView.size}
                safViewAvdod: ${safViewAvdod.size}
                safViewBruker: ${safViewBruker.size}
                view : ${viewTotal.size}
            """.trimMargin())

            //return med sort og distinct (avdodfnr og caseid)
            return@measure viewTotal.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also { logger.info("getGjenlevendeRinasakerAvdodGjenny: view size: ${it.size}, total tid: ${System.currentTimeMillis()-start} i ms") }
        }
    }
//    export const BUC_GET_BUCSLIST_WITH_AVDODFNR_URL = BUC_URL + '/rinasaker/%(aktoerId)s/saknr/%(sakId)s/avdod/%(avdodFnr)s'




}