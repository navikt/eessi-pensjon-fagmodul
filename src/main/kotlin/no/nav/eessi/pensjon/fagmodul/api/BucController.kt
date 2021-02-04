package no.nav.eessi.pensjon.fagmodul.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedSubject
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.SubjectFnr
import no.nav.eessi.pensjon.fagmodul.eux.ValidBucAndSed
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Creator
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonDataService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct

@Protected
@RestController
@RequestMapping("/buc")
class BucController(
    @Value("\${NAIS_NAMESPACE}") val nameSpace: String,
    private val euxService: EuxService,
    private val auditlogger: AuditLogger,
    private val pensjonsinformasjonClient: PensjonsinformasjonClient,
    private val personDataService: PersonDataService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {



    private val logger = LoggerFactory.getLogger(BucController::class.java)
    private val validBucAndSed = ValidBucAndSed()
    private lateinit var bucDetaljer: MetricsHelper.Metric
    private lateinit var bucDetaljerVedtak: MetricsHelper.Metric
    private lateinit var bucDetaljerEnkel: MetricsHelper.Metric
    private lateinit var bucDetaljerGjenlev: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        bucDetaljer = metricsHelper.init("BucDetaljer")
        bucDetaljerVedtak = metricsHelper.init("BucDetaljerVedtak")
        bucDetaljerEnkel = metricsHelper.init("BucDetaljerEnkel")
        bucDetaljerGjenlev  = metricsHelper.init("BucDetaljerGjenlev")
    }

    @ApiOperation("henter liste av alle tilgjengelige BuC-typer")
    @GetMapping("/bucs/{sakId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucs(@PathVariable(value = "sakId", required = false) sakId: String? = "") = validBucAndSed.initSedOnBuc().keys.map { it }.toList()

    @ApiOperation("Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}")
    fun getBuc(@PathVariable(value = "rinanr", required = true) rinanr: String): Buc {
        auditlogger.log("getBuc")
        logger.debug("Henter ut hele Buc data fra rina via eux-rina-api")
        return euxService.getBuc(rinanr)
    }

    @ApiOperation("Viser prosessnavnet (f.eks P_BUC_01) på den valgte BUCen")
    @GetMapping("/{rinanr}/name")
    fun getProcessDefinitionName(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut definisjonsnavn (type type) på valgt Buc")
        return BucUtils(euxService.getBuc(rinanr)).getProcessDefinitionName()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (type)")
    @GetMapping("/{rinanr}/creator")
    fun getCreator(@PathVariable(value = "rinanr", required = true) rinanr: String): Creator? {

        logger.debug("Henter ut Creator på valgt Buc")
        return BucUtils(euxService.getBuc(rinanr)).getCreator()
    }

    @ApiOperation("Henter BUC deltakere")
    @GetMapping("/{rinanr}/bucdeltakere")
    fun getBucDeltakere(@PathVariable(value = "rinanr", required = true) rinanr: String): String {
        auditlogger.log("getBucDeltakere")
        logger.debug("Henter ut Buc deltakere data fra rina via eux-rina-api")
        return mapAnyToJson(euxService.getBucDeltakere(rinanr))
    }

    @ApiOperation("Henter opp creator countrycode (type)")
    @GetMapping("/{rinanr}/creator/countryCode")
    fun getCreatorCountryCode(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut CountryCode for Creator på valgt Buc")
        return mapAnyToJson(BucUtils(euxService.getBuc(rinanr)).getCreatorContryCode())
    }

    @ApiOperation("Henter opp internationalid på caseid (type)")
    @GetMapping("/{rinanr}/internationalId")
    fun getInternationalId(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut InternationalId på valgt Buc")
        return BucUtils(euxService.getBuc(rinanr)).getInternatinalId()
    }

    @ApiOperation("Henter alle gyldige sed på valgt rinanr")
    @GetMapping("/{rinanr}/allDocuments")
    fun getAllDocuments(@PathVariable(value = "rinanr", required = true) rinanr: String): List<ShortDocumentItem> {
        auditlogger.log("/buc/{$rinanr}/allDocuments", "getAllDocuments")
        logger.debug("Henter ut documentId på alle dokumenter som finnes på valgt type")
        val buc = euxService.getBuc(rinanr)
        return BucUtils(buc).getAllDocuments()
    }

    @ApiOperation("Henter opp mulige aksjon(er) som kan utføres på valgt buc")
    @GetMapping("/{rinanr}/aksjoner")
    fun getMuligeAksjoner(@PathVariable(value = "rinanr", required = true) rinanr: String): List<SEDType> {
        logger.debug("Henter ut muligeaksjoner på valgt buc med rinanummer: $rinanr")
        val bucUtil = BucUtils(euxService.getBuc(rinanr))
        return bucUtil.filterSektorPandRelevantHorizontalSeds(bucUtil.getSedsThatCanBeCreated())
    }

    @ApiOperation("Henter ut en liste over saker på valgt aktoerid. ny api kall til eux")
    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<Rinasak> {
        auditlogger.log("getRinasaker", aktoerId)
        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")

        val norskIdent = hentFnrfraAktoerService(aktoerId, personDataService)

        return euxService.getRinasaker(norskIdent, aktoerId)
    }

    @ApiOperation("Henter ut liste av Buc meny struktur i json format for UI på valgt aktoerid")
    @GetMapping("/detaljer/{aktoerid}", "/detaljer/{aktoerid}/{sakid}", "/detaljer/{aktoerid}/{sakid}/{euxcaseid}")
    fun getBucogSedView(@PathVariable("aktoerid", required = true) aktoerid: String,
                        @PathVariable("sakid", required = false) sakid: String? = "",
                        @PathVariable("euxcaseid", required = false) euxcaseid: String? = ""): List<BucAndSedView> {
        auditlogger.log("getBucogSedView", aktoerid)

        return bucDetaljer.measure {
            logger.debug("Prøver å dekode aktoerid: $aktoerid til fnr.")

            val fnr = hentFnrfraAktoerService(aktoerid, personDataService)

            val rinasakIdList = try {
                val rinasaker = euxService.getRinasaker(fnr, aktoerid)
                val rinasakIdList = euxService.getFilteredArchivedaRinasaker(rinasaker)
                rinasakIdList
            } catch (ex: Exception) {
                logger.error("Feil oppstod under henting av rinasaker på aktoer: $aktoerid", ex)
                throw Exception("Feil ved henting av rinasaker på borger")
            }

            try {
                return@measure euxService.getBucAndSedView(rinasakIdList)
            } catch (ex: Exception) {
                logger.error("Feil ved henting av visning BucSedAndView på aktoer: $aktoerid", ex)
                throw Exception("Feil ved oppretting av visning over BUC")
            }
        }
    }

    @ApiOperation("Henter ut liste av Buc meny struktur i json format for UI")
    @GetMapping("/detaljer/{aktoerid}/vedtak/{vedtakid}")
    fun getBucogSedViewVedtak(@PathVariable("aktoerid", required = true) gjenlevendeAktoerid: String,
                              @PathVariable("vedtakid", required = true) vedtakid: String): List<BucAndSedView> {
        return bucDetaljerVedtak.measure {
            //Hente opp pesysservice. hente inn vedtak pensjoninformasjon..
            val peninfo = pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid)

            logger.debug("Pensjoninfo vedtak avdod :"
                    + "\nAvod " + peninfo.avdod?.avdod
                    + "\nAvdodMor : " + peninfo.avdod?.avdodMor
                    + "\nAvdodFar : " + peninfo.avdod?.avdodFar
                    + "\n")

            val person = peninfo.person
            val avdod = hentGyldigAvdod(peninfo)

            logger.info("vedtak aktoerid: ${person.aktorId}")
            if (avdod != null && person.aktorId == gjenlevendeAktoerid) {
                logger.info("henter buc for gjenlevende ved vedtakid: $vedtakid")
                val gjenlevBucs = avdod.map { avdodFnr -> getBucogSedViewGjenlevende(gjenlevendeAktoerid, avdodFnr) }.flatten()
                return@measure gjenlevBucs
            } else {
                logger.info("Henter buc for bruker: $gjenlevendeAktoerid")
                return@measure getBucogSedView(gjenlevendeAktoerid)
            }
        }
    }

    fun hentGyldigAvdod(peninfo: Pensjonsinformasjon) : List<String>? {
        val avdod = peninfo.avdod
        val avdodMor = avdod?.avdodMor
        val avdodFar = avdod?.avdodFar
        val annenAvdod = avdod?.avdod

        return when {
            annenAvdod != null && avdodFar == null && avdodMor == null -> listOf(annenAvdod)
            annenAvdod == null && avdodFar != null && avdodMor == null -> listOf(avdodFar)
            annenAvdod == null && avdodFar == null && avdodMor != null -> listOf(avdodMor)
            annenAvdod == null && avdodFar != null && avdodMor != null -> listOf(avdodFar, avdodMor)
            annenAvdod == null && avdodFar == null && avdodMor == null -> null
            else -> {
                logger.error("Ukjent feil ved henting av buc detaljer for gjenlevende")
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Ukjent feil ved henting av buc detaljer for gjenlevende")
           }
        }
    }

    @ApiOperation("Henter ut liste av Buc meny struktur i json format for UI på valgt aktoerid")
    @GetMapping("/detaljer/{aktoerid}/avdod/{avdodfnr}")
    fun getBucogSedViewGjenlevende(@PathVariable("aktoerid", required = true) aktoerid: String,
                                   @PathVariable("avdodfnr", required = true) avdodfnr: String): List<BucAndSedView> {

        return bucDetaljerGjenlev.measure {
            logger.info("Prøver å dekode aktoerid: $aktoerid til gjenlevende fnr.")
            val fnrGjenlevende = hentFnrfraAktoerService(aktoerid, personDataService)
            logger.debug("gjenlevendeFnr: $fnrGjenlevende samt avdødfnr: $avdodfnr")

            //hente BucAndSedView på avdød
            val avdodBucAndSedView = try {
                // Henter rina saker basert på fnr
                logger.debug("henter avdod BucAndSedView fra avdød (P_BUC_02)")
                euxService.getBucAndSedViewAvdod(fnrGjenlevende, avdodfnr)
            } catch (ex: Exception) {
                logger.error("Feiler ved henting av Rinasaker for gjenlevende og avdod", ex)
                throw Exception("Feil ved henting av Rinasaker for gjenlevende")
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


    @ApiOperation("Henter ut enkel Buc meny struktur i json format for UI på valgt euxcaseid")
    @GetMapping("/enkeldetalj/{euxcaseid}")
    fun getSingleBucogSedView(@PathVariable("euxcaseid", required = true) euxcaseid: String): BucAndSedView {
        auditlogger.log("getSingleBucogSedView")

        return bucDetaljerEnkel.measure {
            logger.debug(" prøver å hente ut en enkel buc med euxCaseId: $euxcaseid")
            return@measure euxService.getSingleBucAndSedView(euxcaseid)
        }
    }

    @ApiOperation("Oppretter ny tom BUC i RINA via eux-api. ny api kall til eux")
    @PostMapping("/{buctype}")
    fun createBuc(@PathVariable("buctype", required = true) buctype: String): BucAndSedView {
        auditlogger.log("createBuc")
        logger.info("Prøver å opprette en ny BUC i RINA av type: $buctype")

        //rinaid
        val euxCaseId = euxService.createBuc(buctype)
        logger.info("Mottatt følgende euxCaseId(RinaID): $euxCaseId")

        //create bucDetail back from newly created buc call eux-rina-api to get data.
        val buc = euxService.getBuc(euxCaseId)

        return BucAndSedView.from(buc)
    }
}
