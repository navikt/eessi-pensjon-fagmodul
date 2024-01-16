package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.fagmodul.eux.*
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.utils.toJson
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
@Protected
@RestController
@RequestMapping("/buc")
class BucController(
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
    private val innhentingService: InnhentingService,
    private val gcpStorageService: GcpStorageService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(BucController::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    private lateinit var bucDetaljerEnkel: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkelGjenlevende: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkelavdod: MetricsHelper.Metric
    private lateinit var bucViewForVedtak: MetricsHelper.Metric
    private lateinit var bucView: MetricsHelper.Metric
    private lateinit var bucerJoark: MetricsHelper.Metric
    private lateinit var bucViewRina: MetricsHelper.Metric
    private lateinit var getBUC: MetricsHelper.Metric
    init {
        bucDetaljerEnkel = metricsHelper.init("BucDetaljerEnkel", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkelGjenlevende = metricsHelper.init("bucDetaljerEnkelGjenlevende", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkelavdod = metricsHelper.init("bucDetaljerEnkelavdod", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucViewForVedtak = metricsHelper.init("bucViewForVedtak", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucView = metricsHelper.init("BucView", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerJoark = metricsHelper.init("BucerJoark", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucViewRina = metricsHelper.init("BucViewRina", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        getBUC = metricsHelper.init("GetBUC", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @GetMapping("/bucs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucsUtenSaksId() = ValidBucAndSed.pensjonsBucer()

    @GetMapping("/{rinanr}")
    fun getBuc(@PathVariable(value = "rinanr", required = true) rinanr: String): Buc =
        getBUC.measure {
            auditlogger.log("getBuc")
            logger.debug("Henter ut hele Buc data fra rina via eux-rina-api")
            return@measure euxInnhentingService.getBuc(rinanr)
        }

    @GetMapping("/enkeldetalj/{euxcaseid}")
    fun hentSingleBucAndSedView(@PathVariable("euxcaseid") euxcaseid: String): BucAndSedView =
        bucDetaljerEnkel.measure {
            auditlogger.log("hentSingleBucAndSedView")
            logger.debug(" prøver å hente ut en enkel buc med euxCaseId: $euxcaseid")
            return@measure euxInnhentingService.getSingleBucAndSedView(euxcaseid)
        }

    @Deprecated("Utgår til fordel for hentBucerMedJournalforteSeder og getRinasakerFraRina")
    @GetMapping("/rinasaker/{aktoerId}/saknr/{saknr}")
    fun getRinasakerBrukerkontekst(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("saknr", required = false) pensjonSakNummer: String
    ): List<EuxInnhentingService.BucView> {
        return bucView.measure {
            val start = System.currentTimeMillis()
            val timeTracking = mutableListOf<String>()

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, på saknr: $pensjonSakNummer")
            val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerId)
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)
                .also { timeTracking.add("rinaSakIderFraJoark tid: ${System.currentTimeMillis()-start} i ms") }

            //bruker saker fra eux/rina
            val brukerView = gjenlevendeFnr?.let { euxInnhentingService.hentBucViewBruker(it.id, aktoerId, pensjonSakNummer) }.also {
                timeTracking.add("hentBucViewBruker, gjenlevendeFnr tid: ${System.currentTimeMillis()-start} i ms")
            }?: emptyList()

            //filtert bort brukersaker fra saf
            val filterBrukerRinaSakIderFraJoark = rinaSakIderFraJoark.filterNot { rinaid -> rinaid in brukerView.map { it.euxCaseId }  }

            //saker fra saf og eux/rina
            val safView = euxInnhentingService.lagBucViews(
                aktoerId,
                pensjonSakNummer,
                filterBrukerRinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            ).also {timeTracking.add("hentBucViews tid: ${System.currentTimeMillis()-start} i ms")}

            val view = (brukerView + safView).also { logger.info("Antall for brukerview+safView: ${it.size}") }
            //rinaIder inneholder bucer som ikke er gjenny bucer
            val rinaIder = brukerView.map { it.euxCaseId }.filter { gcpStorageService.eksisterer(it) }.also { logger.info("Det finnes ${it.size} SED som kommer fra GJENNY") }

            //return med sort og distict (avdodfnr og caseid)
            return@measure view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                //Viser ep-bucer som ikke er gjenny-bucer
                .filterNot { rinaIder.contains(it.euxCaseId) }
                .also {
                    logger.info("Tidsbruk for getRinasakerBrukerkontekst: \n"+timeTracking.joinToString("\n").trimIndent())
                }
        }
    }

    /**
     * Liste med buc'er for aktør & pesys-sakssnr - som det finnes journalførte dokumenter på
     */
    @GetMapping("/joark/aktoer/{aktoerId}/pesyssak/{saknr}")
    fun hentBucerMedJournalforteSeder(
            @PathVariable("aktoerId", required = true) aktoerId: String,
            @PathVariable("saknr", required = false) pensjonSakNummer: String
    ): List<Buc> {
        return bucerJoark.measure {
            val start = System.currentTimeMillis()

            logger.info("henter journalførte bucer for valgt aktoerid: $aktoerId, på saknr: $pensjonSakNummer")

            val joarkstart = System.currentTimeMillis()
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)
            logger.debug("rinaSakIderFraJoark : ${rinaSakIderFraJoark.toJson()}")
            logger.info("hentBucerMedJournalforteSeder tid: ${System.currentTimeMillis()-joarkstart} i ms")

            //saker fra saf og eux/rina
            val bucer = euxInnhentingService.hentBucer(
                aktoerId,
                pensjonSakNummer,
                rinaSakIderFraJoark
            )
            logger.debug("bucer : ${bucer.toJson()}")

            //return med sort og distict (avdodfmr og caseid)
            return@measure bucer
                .also {
                    logger.info("""
                        Buc count: ${it.size}
                        HentBucerMedJournalforteSeder: buc count: ${it.size} - total tid: ${System.currentTimeMillis()-start} ms
                        """.trimIndent())
                }
        }
    }

    /**
     * Åpne Rina-saker via EUX (her finnes ikke lukkede - bruk Joark-kallet for disse)
     */
    @GetMapping("/rinasaker/euxrina/{aktoerId}/pesyssak/{saknr}")
    fun getRinasakerFraRina(
            @PathVariable("aktoerId", required = true) aktoerId: String,
            @PathVariable("saknr", required = false) pensjonSakNummer: String
    ): List<EuxInnhentingService.BucView> {
        return bucViewRina.measure {
            val start = System.currentTimeMillis()

            //Når vi ikke finner noe fnr så feiler denne med 404 NOT_FOUND
            val fnr = innhentingService.hentFnrEllerNpidfraAktoerService(AktoerId(aktoerId))
            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, på saknr: $pensjonSakNummer")

            //Her kreves fnr fra kallet over, kan vi sjekke om vi kan bruke npid i stedet?
            val rinaSaker = euxInnhentingService.hentBucViewBruker(fnr.id, aktoerId, pensjonSakNummer)
            logger.info("brukerView : ${rinaSaker.toJson()}")

            val rinaIder = rinaSaker.map { it.euxCaseId }.filter { gcpStorageService.eksisterer(it) }


            //return med sort og distict (avdodfnr og caseid)
            return@measure rinaSaker.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                // Viser ikke Gjenny bucer
                .filterNot { rinaIder.contains(it.euxCaseId) }
                .also {
                    logger.info("""
                        Total view size: ${it.size}
                        GetRinasakerFraRina -> BrukerRinasaker total tid: ${System.currentTimeMillis()-start} i ms
                    """.trimMargin())
                }
        }
    }

    @GetMapping("/rinasaker/{aktoerId}/saknr/{saknr}/vedtak/{vedtakid}")
    fun getGjenlevendeRinasakerVedtak(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("saknr", required = false) sakNr: String,
        @PathVariable("vedtakid", required = false) vedtakId: String? = null
    ): List<EuxInnhentingService.BucView> {
        return bucViewForVedtak.measure {
            val start = System.currentTimeMillis()

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, saknr: $sakNr, vedtaksId:$vedtakId")

            //api
            val avdodeFraPesysVedtak = hentAvdodFraVedtak(vedtakId, sakNr)

            if (avdodeFraPesysVedtak.isEmpty()) {
                return@measure emptyList<EuxInnhentingService.BucView>()
                    .also { loggTimeAndViewSize("GjenlevendeRinasakerVedtak", start, 0) }
            }

            //api: brukersaker fra Joark/saf
            val brukerRinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)
                .also { loggTimeAndViewSize("brukerRinaSakIderFraJoark", start, 0) }

            //api: avdødSaf + avdødUtenSaf + avdødsaf + safBruker
            val view = euxInnhentingService.hentViewsForSafOgRinaForAvdode(
                avdodeFraPesysVedtak,
                aktoerId,
                sakNr,
                brukerRinaSakIderFraJoark
            )

            val rinaIder = view.map { it.euxCaseId }.filter { gcpStorageService.eksisterer(it) }.also { logger.info("Det finnes ${it.size} SED som kommer fra GJENNY") }

            //return med sort og distinct (avdodfnr og caseid)
            return@measure view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .filterNot { rinaIder.contains(it.euxCaseId) }
                .also { logger.info("GjenlevendeRinasakerVedtak: view size: ${it.size}, total tid: ${System.currentTimeMillis()-start} i ms") }
        }
    }

    private fun loggTimeAndViewSize(servicename: String, start: Long, viewsize: Long = 0) {
        logger.info("""
                Total view size is $viewsize
                $servicename -> total tid: ${System.currentTimeMillis() - start} in ms
                """.trimIndent()
        )
    }

    private fun hentAvdodFraVedtak(vedtakId: String?, sakNr: String): List<String> {
        if (vedtakId == null || vedtakId.all { char -> !char.isDigit() }) return emptyList()

        val start = System.currentTimeMillis()
        val pensjonsinformasjon = try {
            innhentingService.hentPensjoninformasjonVedtak(vedtakId)
        } catch (ex: Exception) {
            logger.warn("Feiler ved henting av pensjoninformasjon (saknr: $sakNr, vedtak: $vedtakId), forsetter uten.")
            null
        }
        val avdodlist = pensjonsinformasjon?.let { peninfo -> innhentingService.hentAvdodeFnrfraPensjoninformasjon(peninfo) } ?: emptyList()
        return avdodlist.also {
            val end = System.currentTimeMillis()
            logger.debug("Hent avdod fra vedtak tid: ${end-start} i ms, med ${avdodlist.size} avdode") }
    }

    @GetMapping("/rinasaker/{aktoerId}/saknr/{saknr}/avdod/{avdodfnr}")
    fun getAvdodRinaSak(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("saknr", required = true) sakNr: String,
        @PathVariable("avdodfnr", required = true) avdodfnr : String
    ): List<EuxInnhentingService.BucView> {
        logger.info("Henter rinasaker på avdod: $aktoerId, saknr: $sakNr")

        return euxInnhentingService.hentBucViewAvdod(avdodfnr, aktoerId, sakNr)

    }

    @Deprecated("Fjernes når vi bytter til endepunktet ->", replaceWith = ReplaceWith("hentSingleBucAndSedView(euxcaseid)"))
    @GetMapping("/enkeldetalj/{euxcaseid}/aktoerid/{aktoerid}/saknr/{saknr}/kilde/{kilde}")
    fun getSingleBucogSedView(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("aktoerid", required = true) aktoerid: String,
        @PathVariable("saknr", required = true) saknr: String,
        @PathVariable("kilde", required = true) kilde: EuxInnhentingService.BucViewKilde
    ): BucAndSedView {
        return bucDetaljerEnkel.measure {
            logger.info("Henter ut en enkel buc med euxCaseId: $euxcaseid, saknr: $saknr, kilde: $kilde")
            euxInnhentingService.getSingleBucAndSedView(euxcaseid)
        }
    }

    @GetMapping("/enkeldetalj/{euxcaseid}/aktoerid/{aktoerid}/saknr/{saknr}/avdodfnr/{avdodfnr}/kilde/{kilde}")
    fun getSingleBucogSedViewMedAvdod(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("aktoerid", required = true) aktoerid: String,
        @PathVariable("saknr", required = true) saknr: String,
        @PathVariable("avdodfnr", required = true) avdodFnr: String,
        @PathVariable("kilde", required = true) kilde: EuxInnhentingService.BucViewKilde
    ): BucAndSedView {
        logger.info("Henter ut en enkel buc for gjenlevende")

        val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerid)
        return if (kilde == EuxInnhentingService.BucViewKilde.SAF) {
            bucDetaljerEnkelGjenlevende.measure {
                logger.info("saf euxCaseId: $euxcaseid, saknr: $saknr")
                euxInnhentingService.getSingleBucAndSedView(euxcaseid)
                .copy(subject = BucAndSedSubject(SubjectFnr(gjenlevendeFnr?.id), SubjectFnr(avdodFnr)))
            }
        } else {
            bucDetaljerEnkelavdod.measure {
                logger.info("avdod med euxCaseId: $euxcaseid, saknr: $saknr")
                val bucOgDocAvdod = euxInnhentingService.hentBucOgDocumentIdAvdod(listOf(euxcaseid))
                val listeAvSedsPaaAvdod = euxInnhentingService.hentDocumentJsonAvdod(bucOgDocAvdod)
                val gyldigeBucs = gjenlevendeFnr?.let { euxInnhentingService.filterGyldigBucGjenlevendeAvdod(listeAvSedsPaaAvdod, it.id) }

                val rinasaker = gyldigeBucs?.map { it.id }?.filter { gcpStorageService.eksisterer(it!!) }

                val gjenlevendeBucAndSedView = gyldigeBucs?.let { euxInnhentingService.getBucAndSedViewWithBuc(it, gjenlevendeFnr.id, avdodFnr) }
                    ?.filterNot { rinasaker?.contains(it.caseId) == true }
                gjenlevendeBucAndSedView?.firstOrNull() ?: BucAndSedView.fromErr("Ingen Buc Funnet!")
            }
        }

    }

}
