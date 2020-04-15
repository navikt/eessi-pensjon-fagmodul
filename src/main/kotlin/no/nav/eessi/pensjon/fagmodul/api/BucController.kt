package no.nav.eessi.pensjon.fagmodul.api

import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Creator
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@Protected
@RestController
@RequestMapping("/buc")
class BucController(private val euxService: EuxService,
                    private val aktoerService: AktoerregisterService,
                    private val auditlogger: AuditLogger) {

    private val logger = LoggerFactory.getLogger(BucController::class.java)

    @ApiOperation("henter liste av alle tilgjengelige BuC-typer")
    @GetMapping("/bucs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucs() = euxService.initSedOnBuc().keys.map { it }.toList()

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

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (type)")
    @GetMapping("/{rinanr}/allDocuments")
    fun getAllDocuments(@PathVariable(value = "rinanr", required = true) rinanr: String): List<ShortDocumentItem> {
        auditlogger.log("/buc/{$rinanr}/allDocuments", "getAllDocuments")
        logger.debug("Henter ut documentId på alle dokumenter som finnes på valgt type")
        val buc = euxService.getBuc(rinanr)
        return BucUtils(buc).getAllDocuments()
    }

    @ApiOperation("Henter opp mulige aksjon(er) som kan utføres på valgt buc")
    @GetMapping("/{rinanr}/aksjoner")
    fun getMuligeAksjoner(@PathVariable(value = "rinanr", required = true) rinanr: String): List<String> {
        logger.debug("Henter ut muligeaksjoner på valgt buc med rinanummer: $rinanr")
        return BucUtils(euxService.getBuc(rinanr)).getFiltrerteGyldigSedAksjonListAsString()
    }

    @ApiOperation("Henter ut en liste over saker på valgt aktoerid. ny api kall til eux")
    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<Rinasak> {
        auditlogger.log("getRinasaker", aktoerId)
        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")

        val fnr = aktoerService.hentPinForAktoer(aktoerId)
        return euxService.getRinasaker(fnr, aktoerId)
    }

    @ApiOperation("Henter ut liste av Buc meny struktur i json format for UI på valgt aktoerid")
    @GetMapping("/detaljer/{aktoerid}", "/detaljer/{aktoerid}/{sakid}", "/detaljer/{aktoerid}/{sakid}/{euxcaseid}")
    fun getBucogSedView(@PathVariable("aktoerid", required = true) aktoerid: String,
                        @PathVariable("sakid", required = false) sakid: String? = "",
                        @PathVariable("euxcaseid", required = false) euxcaseid: String? = ""): List<BucAndSedView> {
        auditlogger.log("getBucogSedView", aktoerid)
        logger.debug("Prøver å dekode aktoerid: $aktoerid til fnr.")

        val fnr = aktoerService.hentPinForAktoer(aktoerid)
        val rinasaker = euxService.getRinasaker(fnr, aktoerid)
        val rinasakIdList = euxService.getFilteredArchivedaRinasaker(rinasaker)

        return euxService.getBucAndSedView( rinasakIdList )
    }

    @ApiOperation("Henter ut enkel Buc meny struktur i json format for UI på valgt euxcaseid")
    @GetMapping("/enkeldetalj/{euxcaseid}")
    fun getSingleBucogSedView(@PathVariable("euxcaseid", required = true) euxcaseid: String): BucAndSedView {
        auditlogger.log("getSingleBucogSedView")
        logger.debug(" prøver å hente ut en enkel buc med euxCaseId: $euxcaseid")

        return euxService.getSingleBucAndSedView(euxcaseid)
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
