package no.nav.eessi.pensjon.fagmodul.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedSubject
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.SubjectFnr
import no.nav.eessi.pensjon.fagmodul.eux.ValidBucAndSed
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucView
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucViewKilde
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Creator
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct


@Protected
@RestController
@RequestMapping("/buc")
class BucController(
    @Value("\${ENV}") val environment: String,
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
    private val innhentingService: InnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(BucController::class.java)
    private val validBucAndSed = ValidBucAndSed()
    private lateinit var bucDetaljer: MetricsHelper.Metric
    private lateinit var bucDetaljerVedtak: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkel: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkelavdod: MetricsHelper.Metric
    private lateinit var bucDetaljerGjenlev: MetricsHelper.Metric
    private lateinit var bucView: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        bucDetaljer = metricsHelper.init("BucDetaljer", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerVedtak = metricsHelper.init("BucDetaljerVedtak", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkel = metricsHelper.init("BucDetaljerEnkel", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerGjenlev  = metricsHelper.init("BucDetaljerGjenlev", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkelavdod = metricsHelper.init("BucDetalsjerEnkelAvdod", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucView = metricsHelper.init("BucView", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }


    @GetMapping("/bucs/{sakId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucs(@PathVariable(value = "sakId", required = false) sakId: String? = "") = validBucAndSed.initSedOnBuc().keys.map { it }.toList()

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
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<Rinasak> {
        auditlogger.log("getRinasaker", aktoerId)
        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")

        val norskIdent = innhentingService.hentFnrfraAktoerService(aktoerId)
        val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraMetaData(aktoerId)

        return euxInnhentingService.getRinasaker(norskIdent, rinaSakIderFraJoark)
    }

    @Deprecated("Utgaar snart", ReplaceWith("getGjenlevendeRinasakerVedtak"))
    @GetMapping("/detaljer/{aktoerid}",
        "/detaljer/{aktoerid}/saknr/{saksnr}",
        "/detaljer/{aktoerid}/saknr/{sakid}/{euxcaseid}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucogSedView(@PathVariable("aktoerid", required = true) aktoerid: String,
                        @PathVariable("sakid", required = false) sakid: String? = "",
                        @PathVariable("euxcaseid", required = false) euxcaseid: String? = ""): List<BucAndSedView> {
        auditlogger.log("getBucogSedView", aktoerid)

        return bucDetaljer.measure {
            logger.info("henter opp bucview for aktoerid: $aktoerid, saknr: $sakid")
            val fnr = innhentingService.hentFnrfraAktoerService(aktoerid)

            val rinasakIdList = try {
                val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraMetaData(aktoerid)
                val rinasaker = euxInnhentingService.getRinasaker(fnr, rinaSakIderFraJoark)
                val rinasakIdList = euxInnhentingService.getFilteredArchivedaRinasaker(rinasaker)
                rinasakIdList
            } catch (ex: Exception) {
                logger.error("Feil oppstod under henting av rinasaker på aktoer: $aktoerid", ex)
                throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved henting av rinasaker på borger")
            }

            try {
                return@measure euxInnhentingService.getBucAndSedView(rinasakIdList)
            } catch (ex: Exception) {
                logger.error("Feil ved henting av visning BucSedAndView på aktoer: $aktoerid", ex)
                throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved oppretting av visning over BUC")
            }
        }
    }

    @GetMapping(
        "/detaljer/{aktoerid}/vedtak/{vedtakid}",
        "/detaljer/{aktoerid}/saknr/{saksnr}/vedtak/{vedtakid}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucogSedViewVedtak(@PathVariable("aktoerid", required = true) gjenlevendeAktoerid: String,
                    @PathVariable("vedtakid", required = true) vedtakid: String,
                    @PathVariable("saksnr", required = false) saksnr: String? = null): List<BucAndSedView> {
        return bucDetaljerVedtak.measure {

            val pensjonsinformasjon = try {
                innhentingService.hentPensjoninformasjonVedtak(vedtakid)
            } catch (ex: Exception) {
                logger.warn("Feiler ved henting av pensjoninformasjon (saknr: $saksnr, vedtak: $vedtakid), forsetter uten.")
                null
            }

            val avdod = pensjonsinformasjon?.let { peninfo -> innhentingService.hentAvdodeFnrfraPensjoninformasjon(peninfo) }
            return@measure if (avdod != null && (pensjonsinformasjon.person.aktorId == gjenlevendeAktoerid)) {
                logger.info("Henter bucview for gjenlevende med aktoerid: $gjenlevendeAktoerid, saksnr: $saksnr og vedtakid: $vedtakid")
                avdod.map { avdodFnr -> getBucogSedViewGjenlevende(gjenlevendeAktoerid, avdodFnr) }.flatten()
            } else {
                getBucogSedView(gjenlevendeAktoerid, saksnr)
            }
        }
    }

    @GetMapping(
            "/detaljer/{aktoerid}/avdod/{avdodfnr}",
            "/detaljer/{aktoerid}/avdod/{avdodfnr}/saknr/{saknr}",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucogSedViewGjenlevende(@PathVariable("aktoerid", required = true) aktoerid: String,
                                   @PathVariable("avdodfnr", required = true) avdodfnr: String,
                                   @PathVariable("saknr", required = false) saknr: String? = null): List<BucAndSedView> {
        return bucDetaljerGjenlev.measure {
            val fnrGjenlevende = innhentingService.hentFnrfraAktoerService(aktoerid)

            //hente BucAndSedView på avdød
            val avdodBucAndSedView = try {
                logger.debug("henter avdod BucAndSedView fra avdød")
                euxInnhentingService.getBucAndSedViewAvdod(fnrGjenlevende, avdodfnr)
            } catch (ex: Exception) {
                logger.error("Feiler ved henting av Rinasaker for gjenlevende og avdod", ex)
                throw ResponseStatusException( HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved henting av Rinasaker for gjenlevende")
            }

            val normalBuc = getBucogSedView(aktoerid)
            val normalbucAndSedView = normalBuc.map { bucview ->
                if ( bucview.type == "P_BUC_02" || bucview.type == "P_BUC_05" || bucview.type == "P_BUC_10" || bucview.type == "P_BUC_06" ) {
                    bucview.copy(subject = BucAndSedSubject(SubjectFnr(fnrGjenlevende), SubjectFnr(avdodfnr)))
                } else {
                    bucview
                }
            }.toList()

            //hente BucAndSedView resterende bucs på gjenlevende (normale bucs)
            //logger.info("henter buc normalt")
            //val normalbucAndSedView = getBucogSedView(aktoerid)
            logger.debug("buclist avdød: ${avdodBucAndSedView.size} buclist normal: ${normalbucAndSedView.size}")
            val list = avdodBucAndSedView.plus(normalbucAndSedView).distinctBy { it.caseId }

            logger.debug("bucview size: ${list.size} ------------------ bucview slutt --------------------")
            return@measure list

        }
    }

    @GetMapping("/enkeldetalj/{euxcaseid}")
    @Deprecated("Går ut", ReplaceWith("Denne går ut ny funksjon på vei inn"))
    fun getSingleBucogSedView(@PathVariable("euxcaseid", required = true) euxcaseid: String): BucAndSedView {
        auditlogger.log("getSingleBucogSedView")

        return bucDetaljerEnkel.measure {
            logger.debug(" prøver å hente ut en enkel buc med euxCaseId: $euxcaseid")
            return@measure euxInnhentingService.getSingleBucAndSedView(euxcaseid)
        }
    }

    @GetMapping("/rinasaker/{aktoerId}/saknr/{saknr}",
        "/rinasaker/{aktoerId}/saknr/{saknr}/vedtak/{vedtakid}")
    fun getGjenlevendeRinasakerVedtak(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("saknr", required = false) sakNr: String,
        @PathVariable("vedtakid", required = false) vedtakId: String? = null
    ): List<BucView> {
        return bucView.measure {
            val start = System.currentTimeMillis()
            //buctyper fra saf som kobles til første avdodfnr
            val safAvdodBucList = listOf(BucType.P_BUC_02, BucType.P_BUC_05, BucType.P_BUC_06, BucType.P_BUC_10)

            logger.info("henter rinasaker på valgt aktoerid: $aktoerId, på saknr: $sakNr")
            val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerId)

            val joarkstart = System.currentTimeMillis()
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraMetaData(aktoerId)
            val joarkend = System.currentTimeMillis()
            logger.info("hentRinaSakIderFraMetaData tid: ${joarkend-joarkstart} i ms")

            //bruker saker fra eux/rina
            val brukerView = if (vedtakId == "" || vedtakId == null ) {
                euxInnhentingService.getBucViewBruker(gjenlevendeFnr, aktoerId, sakNr)
            } else {
                emptyList()
            }

            //filtert bort brukersaker fra saf
            val filterBrukerRinaSakIderFraJoark = rinaSakIderFraJoark.filterNot { rinaid -> rinaid in brukerView.map { it.euxCaseId }  }

            //liste over avdodfnr fra vedtak (pesys)
            val avdodlist = avdodFraVedtak(vedtakId, sakNr)

            //hent avdod saker fra eux/rina
            val avdodView = avdodlist.map { avdodfnr ->
                avdodRinasakerView(avdodfnr, aktoerId, sakNr)
            }.flatten()

            //filter avdodview for match på filterBrukersakerRina
            val avdodViewSaf = avdodView
                //.filterNot { view -> view.kilde == BucViewKilde.SAF && view.avdodFnr != null }
                .filter { view -> view.euxCaseId in filterBrukerRinaSakIderFraJoark }
                .map { view ->
                    view.copy(kilde = BucViewKilde.SAF)
                }

            //avdod saker view uten saf
            val avdodViewUtenSaf = avdodView.filterNot { view -> view.euxCaseId in avdodViewSaf.map { it.euxCaseId  } }

            //liste over saker fra saf som kan hentes
            val filterAvodRinaSakIderFraJoark = filterBrukerRinaSakIderFraJoark.filterNot { rinaid -> rinaid in avdodView.map { it.euxCaseId }  }

            //saker fra saf og eux/rina
            val safView = euxInnhentingService.getBucViewBrukerSaf(aktoerId, sakNr, filterAvodRinaSakIderFraJoark)
//            logger.debug("safView : ${safView.toJson()}")

            //saf filter mot avdod
            val safViewAvdod = safView
                .filter { view -> view.buctype in safAvdodBucList }
                .map { view -> view.copy(avdodFnr = avdodlist.firstOrNull()) }
                .also { if (avdodlist.size == 2) logger.warn("finnes 2 avdod men valgte første, ingen koblinger")}
//            logger.debug("safViewAvdod : ${safViewAvdod.toJson()}")

            //saf filter mot bruker
            val safViewBruker = safView
                .filterNot { view -> view.euxCaseId in safViewAvdod.map { it.euxCaseId } }
//            logger.debug("safView Bruker: ${safViewBruker.toJson()}")

            //samkjøre til megaview
            //val view = brukerView + safView + avdodViewSaf + avdodViewUtenSaf
            val view = brukerView + avdodViewSaf + avdodViewUtenSaf + safViewAvdod + safViewBruker

            //return med sort og distict (avdodfmr og caseid)
            return@measure view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also { logger.info("Total view size: ${it.size}") }
                .also { val end = System.currentTimeMillis()
                        if (avdodlist.isEmpty()) {
                            logger.info("BrukerRinasaker total tid: ${end-start} i ms")
                        } else {
                            logger.info("GjenlevendeRinasakerVedtak total tid: ${end-start} i ms")
                        }
                }
        }
    }

    private fun avdodFraVedtak(vedtakId: String?, sakNr: String): List<String> {
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

    private fun avdodRinasakerView(avdodfnr: String, aktoerid: String, sakNr: String) : List<BucView> =  euxInnhentingService.getBucViewAvdod(avdodfnr, aktoerid, sakNr)



    private fun avdodRinasakerList(vedtakId: String?, sakNr: String, aktoerId: String, safView: List<BucView>): List<BucView> {
        if (vedtakId == null) return emptyList()

        val pensjonsinformasjon = try {
            innhentingService.hentPensjoninformasjonVedtak(vedtakId)
        } catch (ex: Exception) {
            logger.warn("Feiler ved henting av pensjoninformasjon (saknr: $sakNr, vedtak: $vedtakId), forsetter uten.")
            null
        }
        val avdod = pensjonsinformasjon?.let { peninfo -> innhentingService.hentAvdodeFnrfraPensjoninformasjon(peninfo) }

        //rinasaker på avdod
        return if (avdod != null && (pensjonsinformasjon.person.aktorId == aktoerId)) {
            val safAvdodBucList = listOf(BucType.P_BUC_02, BucType.P_BUC_05, BucType.P_BUC_06, BucType.P_BUC_10)

            val avdodview = avdod.map { avdodfnr ->
                euxInnhentingService.getBucViewAvdod(avdodfnr, aktoerId, sakNr)
            }.flatten()

            val avdodFnrs = avdodview.map { view -> view.avdodFnr }

            val safAvdodView = safView.filter { sview -> sview.buctype in safAvdodBucList }
                        .map { view ->
                             view.copy(avdodFnr = avdodFnrs.firstOrNull()  )
                        }

            val avdodSafView = avdodview + safAvdodView
            avdodSafView.sortedBy { view -> view.kilde }.distinctBy { view -> view.euxCaseId }

        } else {
            emptyList()
        }

    }

    @GetMapping("/rinasaker/{aktoerId}/saknr/{saknr}/avdod/{avdodfnr}")
    fun getAvdodRinaSak(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("saknr", required = true) sakNr: String,
        @PathVariable("avdodfnr", required = true) avdodfnr : String
    ): List<BucView> {
        logger.info("Henter rinasaker på avdod: $aktoerId, saknr: $sakNr")

        return euxInnhentingService.getBucViewAvdod(avdodfnr, aktoerId, sakNr)

    }

    @GetMapping("/enkeldetalj/{euxcaseid}/aktoerid/{aktoerid}/saknr/{saknr}/kilde/{kilde}")
    fun getSingleBucogSedView(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("aktoerid", required = true) aktoerid: String,
        @PathVariable("saknr", required = true) saknr: String,
        @PathVariable("kilde", required = true) kilde: BucViewKilde
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
        @PathVariable("kilde", required = true) kilde: BucViewKilde): BucAndSedView {
        logger.info("Henter ut en enkel buc for gjenlevende")

        return if (kilde == BucViewKilde.SAF) {
            bucDetaljerEnkel.measure {
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
