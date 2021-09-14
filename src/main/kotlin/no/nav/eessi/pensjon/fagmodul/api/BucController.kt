package no.nav.eessi.pensjon.fagmodul.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.v3.oas.annotations.Operation
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedSubject
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.SubjectFnr
import no.nav.eessi.pensjon.fagmodul.eux.ValidBucAndSed
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
    @Value("\${NAIS_NAMESPACE}") val nameSpace: String,
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
    private val innhentingService: InnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(BucController::class.java)
    private val validBucAndSed = ValidBucAndSed()
    private lateinit var bucDetaljer: MetricsHelper.Metric
    private lateinit var bucDetaljerVedtak: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkel: MetricsHelper.Metric
    private lateinit var bucDetaljerGjenlev: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        bucDetaljer = metricsHelper.init("BucDetaljer", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerVedtak = metricsHelper.init("BucDetaljerVedtak", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerEnkel = metricsHelper.init("BucDetaljerEnkel", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDetaljerGjenlev  = metricsHelper.init("BucDetaljerGjenlev", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @Operation(description = "henter liste av alle tilgjengelige BuC-typer")
    @GetMapping("/bucs/{sakId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucs(@PathVariable(value = "sakId", required = false) sakId: String? = "") = validBucAndSed.initSedOnBuc().keys.map { it }.toList()

    @Operation(description = "Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}")
    fun getBuc(@PathVariable(value = "rinanr", required = true) rinanr: String): Buc {
        auditlogger.log("getBuc")
        logger.debug("Henter ut hele Buc data fra rina via eux-rina-api")
        return euxInnhentingService.getBuc(rinanr)
    }

    @Operation(description = "Viser prosessnavnet (f.eks P_BUC_01) på den valgte BUCen")
    @GetMapping("/{rinanr}/name",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getProcessDefinitionName(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut definisjonsnavn (type type) på valgt Buc")
        return euxInnhentingService.getBuc(rinanr).processDefinitionName
    }

    @Operation(description = "Henter opp den opprinelige inststusjon på valgt caseid (type)")
    @GetMapping("/{rinanr}/creator",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCreator(@PathVariable(value = "rinanr", required = true) rinanr: String): Creator? {

        logger.debug("Henter ut Creator på valgt Buc")
        return euxInnhentingService.getBuc(rinanr).creator
    }

    @Operation(description = "Henter BUC deltakere")
    @GetMapping("/{rinanr}/bucdeltakere",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucDeltakere(@PathVariable(value = "rinanr", required = true) rinanr: String): String {
        auditlogger.log("getBucDeltakere")
        logger.debug("Henter ut Buc deltakere data fra rina via eux-rina-api")
        return mapAnyToJson(euxInnhentingService.getBucDeltakere(rinanr))
    }

    @Operation(description = "Henter alle gyldige sed på valgt rinanr")
    @GetMapping("/{rinanr}/allDocuments",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllDocuments(@PathVariable(value = "rinanr", required = true) rinanr: String): List<DocumentsItem> {
        auditlogger.logBuc("getAllDocuments", rinanr)
        logger.debug("Henter ut documentId på alle dokumenter som finnes på valgt type")
        val buc = euxInnhentingService.getBuc(rinanr)
        return BucUtils(buc).getAllDocuments()
    }

    @Operation(description = "Henter opp mulige aksjon(er) som kan utføres på valgt buc")
    @GetMapping("/{rinanr}/aksjoner")
    fun getMuligeAksjoner(@PathVariable(value = "rinanr", required = true) rinanr: String): List<SedType> {
        logger.debug("Henter ut muligeaksjoner på valgt buc med rinanummer: $rinanr")
        val bucUtil = BucUtils(euxInnhentingService.getBuc(rinanr))
        return bucUtil.filterSektorPandRelevantHorizontalSeds(bucUtil.getSedsThatCanBeCreated())
    }

    @Operation(description = "Henter ut en liste over saker på valgt aktoerid. ny api kall til eux")
    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<Rinasak> {
        auditlogger.log("getRinasaker", aktoerId)
        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")

        val norskIdent = innhentingService.hentFnrfraAktoerService(aktoerId)
        val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraMetaData(aktoerId)

        return euxInnhentingService.getRinasaker(norskIdent, aktoerId, rinaSakIderFraJoark)
    }

    @Operation(description = "Henter ut liste av Buc meny struktur i json format for UI på valgt aktoerid")
    @GetMapping("/detaljer/{aktoerid}",
        "/detaljer/{aktoerid}/{sakid}",
        "/detaljer/{aktoerid}/{sakid}/{euxcaseid}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucogSedView(@PathVariable("aktoerid", required = true) aktoerid: String,
                        @PathVariable("sakid", required = false) sakid: String? = "",
                        @PathVariable("euxcaseid", required = false) euxcaseid: String? = ""): List<BucAndSedView> {
        auditlogger.log("getBucogSedView", aktoerid)

        return bucDetaljer.measure {
            logger.debug("Prøver å dekode aktoerid: $aktoerid til fnr.")

            val fnr = innhentingService.hentFnrfraAktoerService(aktoerid)
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraMetaData(aktoerid)

            val rinasakIdList = try {
                val rinasaker = euxInnhentingService.getRinasaker(fnr, aktoerid, rinaSakIderFraJoark)
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

    @Operation(description = "Henter ut liste av Buc meny struktur i json format for UI")
    @GetMapping("/detaljer/{aktoerid}/vedtak/{vedtakid}",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucogSedViewVedtak(@PathVariable("aktoerid", required = true) gjenlevendeAktoerid: String,
                              @PathVariable("vedtakid", required = true) vedtakid: String): List<BucAndSedView> {
        return bucDetaljerVedtak.measure {
            //Hente opp pesysservice. hente inn vedtak pensjoninformasjon..

            val pensjonsinformasjon = try {
                innhentingService.hentMedVedtak(vedtakid)
            } catch (ex: Exception) {
                logger.warn("Feiler ved henting av pensjoninformasjon, forsetter uten.")
                null
            }

            val avdod = pensjonsinformasjon?.let { peninfo -> innhentingService.hentGyldigAvdod(peninfo) }

            return@measure if (avdod != null && (pensjonsinformasjon.person.aktorId == gjenlevendeAktoerid)) {
                logger.info("Henter buc for gjenlevende ved vedtakid: $vedtakid")
                avdod.map { avdodFnr -> getBucogSedViewGjenlevende(gjenlevendeAktoerid, avdodFnr) }.flatten()
            } else {
                logger.info("Henter buc for bruker: $gjenlevendeAktoerid")
                getBucogSedView(gjenlevendeAktoerid)
            }
        }
    }

    @Operation(description = "Henter ut liste av Buc meny struktur i json format for UI på valgt aktoerid")
    @GetMapping("/detaljer/{aktoerid}/avdod/{avdodfnr}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucogSedViewGjenlevende(@PathVariable("aktoerid", required = true) aktoerid: String,
                                   @PathVariable("avdodfnr", required = true) avdodfnr: String): List<BucAndSedView> {

        return bucDetaljerGjenlev.measure {
            logger.info("Prøver å dekode aktoerid: $aktoerid til gjenlevende fnr.")
            val fnrGjenlevende = innhentingService.hentFnrfraAktoerService(aktoerid)
            logger.debug("gjenlevendeFnr: $fnrGjenlevende samt avdødfnr: $avdodfnr")

            //hente BucAndSedView på avdød
            val avdodBucAndSedView = try {
                // Henter rina saker basert på fnr
                logger.debug("henter avdod BucAndSedView fra avdød (P_BUC_02)")
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

            logger.debug("buclist size: ${list.size}")
            logger.debug("----------------------- slutt buclist ----------------------")
            return@measure list

        }
    }


    @Operation(description = "Henter ut enkel Buc meny struktur i json format for UI på valgt euxcaseid")
    @GetMapping("/enkeldetalj/{euxcaseid}")
    fun getSingleBucogSedView(@PathVariable("euxcaseid", required = true) euxcaseid: String): BucAndSedView {
        auditlogger.log("getSingleBucogSedView")

        return bucDetaljerEnkel.measure {
            logger.debug(" prøver å hente ut en enkel buc med euxCaseId: $euxcaseid")
            return@measure euxInnhentingService.getSingleBucAndSedView(euxcaseid)
        }
    }
}
