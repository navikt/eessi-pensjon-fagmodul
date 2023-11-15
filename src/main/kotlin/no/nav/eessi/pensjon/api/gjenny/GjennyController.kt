package no.nav.eessi.pensjon.api.gjenny

import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.time.ExperimentalTime

@Unprotected
@RestController
@RequestMapping("/gjenny")
/**
 * Endepunkter for Gjenny
 */
class GjennyController (
    private val euxInnhentingService: EuxInnhentingService,
    private val innhentingService: InnhentingService,
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
                    .also {
                        logger.info("""
                            Total view size is zero
                            GetGjenlevendeRinasakerVedtak -> BrukerRinasaker total tid: ${System.currentTimeMillis() - start} i ms
                            """.trimIndent())
                    }
            }

            //api: brukersaker fra Joark/saf
            val brukerRinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(aktoerId)

            //filter: avdodview for match på filterBrukersakerRina
            val avdodViewSaf = avdodesSakerFraRina
                .filter { view -> view.euxCaseId in brukerRinaSakIderFraJoark }
                .map { view ->
                    view.copy(kilde = EuxInnhentingService.BucViewKilde.SAF)
                }

            //filer: avdod saker view uten saf
            val avdodViewUtenSaf = avdodesSakerFraRina.filterNot { view -> view.euxCaseId in avdodViewSaf.map { it.euxCaseId  } }

            //filter: liste over saker fra saf som kan hentes
            val filterAvdodRinaSakIderFraJoark = brukerRinaSakIderFraJoark.filterNot { rinaid -> rinaid in avdodesSakerFraRina.map { it.euxCaseId }  }

            //api: henter buc fra exu for hver rinasak i brukerRinaSakIderFraJoark listen
            val safView = euxInnhentingService.lagBucViews(
                aktoerId,
                null,
                filterAvdodRinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            )

            //filter: saf saker begrenset til bucTyperSomKanHaAvdod, legger til avdodfnr //TODO Hvorfor legger vi til avdodfnr?
            val safViewAvdod = safView
                .filter { view -> view.buctype in EuxInnhentingService.bucTyperSomKanHaAvdod }
                .map { view -> view.copy(avdodFnr = avdodfnr) }
                .also { if (avdodesSakerFraRina.size == 2) logger.warn("finnes 2 avdod men valgte første, ingen koblinger")}

            //filter: saf for bruker
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

            val view = (brukerView + safView).also { logger.info("Antall for brukerview+safView: ${it.size}") }

            return@measure view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also {
                    logger.info("Tidsbruk for getRinasakerBrukerkontekst: \n"+timeTracking.joinToString("\n").trimIndent())
                }
        }
    }
}