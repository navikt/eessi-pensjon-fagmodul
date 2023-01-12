package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.fagmodul.eux.*
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Creator
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapAnyToJson
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
import javax.annotation.PostConstruct


@Protected
@RestController
@RequestMapping("/buc")
class BucController(
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
    private val innhentingService: InnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(BucController::class.java)
    private lateinit var bucDetaljer: MetricsHelper.Metric
    private lateinit var bucDetaljerVedtak: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkel: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkelGjenlevende: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkelavdod: MetricsHelper.Metric
    private lateinit var bucDetaljerGjenlev: MetricsHelper.Metric
    private lateinit var bucViewForVedtak: MetricsHelper.Metric
    private lateinit var bucView: MetricsHelper.Metric
    private lateinit var bucViewJoark: MetricsHelper.Metric
    private lateinit var bucerJoark: MetricsHelper.Metric
    private lateinit var bucViewRina: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        bucDetaljer = metricsHelper.init("BucDetaljer", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerVedtak = metricsHelper.init("BucDetaljerVedtak", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkel = metricsHelper.init("BucDetaljerEnkel", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkelGjenlevende = metricsHelper.init("bucDetaljerEnkelGjenlevende", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerGjenlev  = metricsHelper.init("BucDetaljerGjenlev", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkelavdod = metricsHelper.init("bucDetaljerEnkelavdod", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucViewForVedtak = metricsHelper.init("bucViewForVedtak", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucView = metricsHelper.init("BucView", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucViewJoark = metricsHelper.init("BucViewJoark", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerJoark = metricsHelper.init("BucerJoark", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucViewRina = metricsHelper.init("BucViewRina", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @GetMapping("/bucs/{sakId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucs(@PathVariable(value = "sakId", required = false) sakId: String? = "") = ValidBucAndSed.pensjonsBucer()

    @GetMapping("/{rinanr}")
    fun getBuc(@PathVariable(value = "rinanr", required = true) rinanr: String): Buc {
        auditlogger.log("getBuc")
        logger.debug("Henter ut hele Buc data fra rina via eux-rina-api")
        return euxInnhentingService.getBuc(rinanr)
    }

    @GetMapping("/{rinanr}/name",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getProcessDefinitionName(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut definisjonsnavn (type type) på valgt Buc")
        return euxInnhentingService.getBuc(rinanr).processDefinitionName
    }

    @GetMapping("/{rinanr}/creator",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCreator(@PathVariable(value = "rinanr", required = true) rinanr: String): Creator? {
        logger.debug("Henter ut Creator på valgt Buc")
        return euxInnhentingService.getBuc(rinanr).creator
    }

    @GetMapping("/{rinanr}/bucdeltakere",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucDeltakere(@PathVariable(value = "rinanr", required = true) rinanr: String): String {
        auditlogger.log("getBucDeltakere")
        logger.debug("Henter ut Buc deltakere data fra rina via eux-rina-api")
        return mapAnyToJson(euxInnhentingService.getBucDeltakere(rinanr))
    }

    @GetMapping("/{rinanr}/allDocuments",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllDocuments(@PathVariable(value = "rinanr", required = true) rinanr: String): List<DocumentsItem> {
        auditlogger.logBuc("getAllDocuments", rinanr)
        logger.debug("Henter ut documentId på alle dokumenter som finnes på valgt type")
        val buc = euxInnhentingService.getBuc(rinanr)
        return BucUtils(buc).getAllDocuments()
    }

    @GetMapping("/{rinanr}/aksjoner")
    fun getMuligeAksjoner(@PathVariable(value = "rinanr", required = true) rinanr: String): List<SedType> {
        logger.debug("Henter ut muligeaksjoner på valgt buc med rinanummer: $rinanr")
        val bucUtil = BucUtils(euxInnhentingService.getBuc(rinanr))
        return bucUtil.filterSektorPandRelevantHorizontalAndXSeds(bucUtil.getSedsThatCanBeCreated())
    }

    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<EuxKlient.Rinasak> {
        auditlogger.log("getRinasaker", aktoerId)
        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")

        val norskIdent = innhentingService.hentFnrfraAktoerService(aktoerId)
        val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)

        return euxInnhentingService.getRinasaker(norskIdent, rinaSakIderFraJoark)
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

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, på saknr: $pensjonSakNummer")
            val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerId)

            val joarkstart = System.currentTimeMillis()
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)  //rinasak
            logger.info("hentRinaSakIderFraMetaData tid: ${System.currentTimeMillis()-joarkstart} i ms")
            logger.debug("rinaSakIderFraJoark : ${rinaSakIderFraJoark.toJson()}")

            //bruker saker fra eux/rina
            val brukerView = euxInnhentingService.hentBucViewBruker(gjenlevendeFnr, aktoerId, pensjonSakNummer)
            logger.debug("brukerView : ${brukerView.toJson()}")

            //filtert bort brukersaker fra saf
            val filterBrukerRinaSakIderFraJoark = rinaSakIderFraJoark.filterNot { rinaid -> rinaid in brukerView.map { it.euxCaseId }  }

            //saker fra saf og eux/rina
            val safView = euxInnhentingService.hentBucViews(
                aktoerId,
                pensjonSakNummer,
                filterBrukerRinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            )
            logger.debug("safView : ${safView.toJson()}")

            val view = brukerView + safView

            //return med sort og distict (avdodfmr og caseid)
            return@measure view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also {
                    logger.info("Total view size: ${it.size}")
                    logger.info("BrukerRinasaker total tid: ${System.currentTimeMillis()-start} i ms")
                }
        }
    }

    /**
     * Rina-saker der det finnes journalførte dokumenter
     */
    @Deprecated("Bruk hentBucerMedJournalforteSeder()")
    @GetMapping("/rinasaker/joark/{aktoerId}/pesyssak/{saknr}")
    fun getRinasakerJoark(
            @PathVariable("aktoerId", required = true) aktoerId: String,
            @PathVariable("saknr", required = false) pensjonSakNummer: String
    ): List<EuxInnhentingService.BucView> {
        return bucViewJoark.measure {
            val start = System.currentTimeMillis()

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, på saknr: $pensjonSakNummer")

            val joarkstart = System.currentTimeMillis()
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)
            logger.info("hentRinaSakIderFraMetaData tid: ${System.currentTimeMillis()-joarkstart} i ms")
            logger.debug("rinaSakIderFraJoark : ${rinaSakIderFraJoark.toJson()}")

            //saker fra saf og eux/rina
            val safView = euxInnhentingService.hentBucViews(
                aktoerId,
                pensjonSakNummer,
                rinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            )
            logger.debug("safView : ${safView.toJson()}")

            //return med sort og distict (avdodfmr og caseid)
            return@measure safView.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                    .also {
                        logger.info("Total view size: ${it.size}")
                        logger.info("BrukerRinasaker total tid: ${System.currentTimeMillis()-start} i ms")
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
                        logger.info("Buc count: ${it.size}")
                        logger.info("hentBucerMedJournalforteSeder: buc count: ${it.size} - total tid: ${System.currentTimeMillis()-start} ms")
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

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, på saknr")
            val fnr = innhentingService.hentFnrfraAktoerService(aktoerId)

            val rinaSaker = euxInnhentingService.hentBucViewBruker(fnr, aktoerId, pensjonSakNummer)
            logger.debug("brukerView : ${rinaSaker.toJson()}")

            //return med sort og distict (avdodfmr og caseid)
            return@measure rinaSaker.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                    .also {
                        logger.info("Total view size: ${it.size}")
                        logger.info("BrukerRinasaker total tid: ${System.currentTimeMillis()-start} i ms")
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

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, på saknr: $sakNr")

            val avdodeFraPesysVedtak = hentAvdodFraVedtak(vedtakId, sakNr)

            if (avdodeFraPesysVedtak.isEmpty()) {
                return@measure emptyList<EuxInnhentingService.BucView>()
                    .also {
                        logger.info("Total view size is zero")
                        logger.info("BrukerRinasaker total tid: ${System.currentTimeMillis() - start} i ms")
                    }
            }

            //hent avdod saker fra eux/rina
            val avdodView = avdodeFraPesysVedtak.map { avdod ->
                euxInnhentingService.hentBucViewAvdod(avdod, aktoerId, sakNr)
            }.flatten()

            val joarkstart = System.currentTimeMillis()

            //brukersaker fra Joark/saf
            val brukerRinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)
            logger.info("hentRinaSakIderFraMetaData tid: ${System.currentTimeMillis()-joarkstart} i ms")

            //filter avdodview for match på filterBrukersakerRina
            val avdodViewSaf = avdodView
                .filter { view -> view.euxCaseId in brukerRinaSakIderFraJoark }
                .map { view ->
                    view.copy(kilde = EuxInnhentingService.BucViewKilde.SAF)
                }

            //avdod saker view uten saf
            val avdodViewUtenSaf = avdodView.filterNot { view -> view.euxCaseId in avdodViewSaf.map { it.euxCaseId  } }

            //liste over saker fra saf som kan hentes
            val filterAvodRinaSakIderFraJoark = brukerRinaSakIderFraJoark.filterNot { rinaid -> rinaid in avdodView.map { it.euxCaseId }  }

            //saker fra saf og eux/rina
            val safView = euxInnhentingService.hentBucViews(
                aktoerId,
                sakNr,
                filterAvodRinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            )

            //saf filter mot avdod
            val safViewAvdod = safView
                .filter { view -> view.buctype in EuxInnhentingService.bucTyperSomKanHaAvdod }
                .map { view -> view.copy(avdodFnr = avdodeFraPesysVedtak.firstOrNull()) }
                .also { if (avdodeFraPesysVedtak.size == 2) logger.warn("finnes 2 avdod men valgte første, ingen koblinger")}

            //saf filter mot bruker
            val safViewBruker = safView
                .filterNot { view -> view.euxCaseId in safViewAvdod.map { it.euxCaseId } }

            //samkjøre til megaview
            val view = avdodViewSaf + avdodViewUtenSaf + safViewAvdod + safViewBruker  // avdødSaf + avdødUtenSaf + avdødsaf + safBruker

            logger.info("""getGjenlevendeRinasakerVedtak resultat: 
                avdodView : ${avdodView.size}
                brukerRinaSakIderFraJoark: ${brukerRinaSakIderFraJoark.size}
                avdodViewUtenSaf: ${avdodViewUtenSaf.size}
                filterAvodRinaSakIderFraJoark: ${filterAvodRinaSakIderFraJoark.size}
                safView: ${safView.size}
                safViewAvdod: ${safViewAvdod.size}
                safViewBruker: ${safViewBruker.size}
                view : ${view.size}
            """.trimMargin())

            //return med sort og distinct (avdodfnr og caseid)
            return@measure view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also { logger.info("Total view size: ${it.size}") }
                .also { if (avdodeFraPesysVedtak.isEmpty()) {
                        logger.info("BrukerRinasaker total tid: ${System.currentTimeMillis()-start} i ms")
                    } else {
                        logger.info("GjenlevendeRinasakerVedtak total tid: ${System.currentTimeMillis()-start} i ms")
                    }
                }
        }
    }


    private fun hentAvdodFraVedtak(vedtakId: String?, sakNr: String): List<String> {
        if (vedtakId == null) return emptyList()

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
            logger.debug("Hent avdod fra vedtak tid: ${end-start} i ms") }
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

        return if (kilde == EuxInnhentingService.BucViewKilde.SAF) {
            bucDetaljerEnkelGjenlevende.measure {
                logger.info("saf euxCaseId: $euxcaseid, saknr: $saknr")
                val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerid)
                euxInnhentingService.getSingleBucAndSedView(euxcaseid)
                .copy(subject = BucAndSedSubject(SubjectFnr(gjenlevendeFnr), SubjectFnr(avdodFnr)))
            }
        } else {
            bucDetaljerEnkelavdod.measure {
                logger.info("avdod med euxCaseId: $euxcaseid, saknr: $saknr")
                val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerid)
                val bucOgDocAvdod = euxInnhentingService.hentBucOgDocumentIdAvdod(listOf(euxcaseid))
                val listeAvSedsPaaAvdod = euxInnhentingService.hentDocumentJsonAvdod(bucOgDocAvdod)
                val gyldigeBucs = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(listeAvSedsPaaAvdod, gjenlevendeFnr)
                val gjenlevendeBucAndSedView = euxInnhentingService.getBucAndSedViewWithBuc(gyldigeBucs, gjenlevendeFnr, avdodFnr)
                gjenlevendeBucAndSedView.firstOrNull() ?: BucAndSedView.fromErr("Ingen Buc Funnet!")
            }
        }

    }

}
