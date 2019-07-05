package no.nav.eessi.eessifagmodul.controllers

import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.Krav
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.eux.BucUtils
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.eux.RinaAksjon
import no.nav.eessi.eessifagmodul.services.eux.Rinasak
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.BucAndSedView
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Creator
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.eessifagmodul.services.saf.VariantFormat
import no.nav.eessi.eessifagmodul.utils.errorBody
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.successBody
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*


@Protected
@RestController
@RequestMapping("/buc")
class BucController(private val euxService: EuxService,
                    aktoerregisterService: AktoerregisterService) : AktoerIdHelper(aktoerregisterService) {

    private val logger = LoggerFactory.getLogger(BucController::class.java)


    @ApiOperation("Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}")
    fun getBuc(@PathVariable(value = "rinanr", required = true) rinanr: String): Buc {
        logger.debug("Henter ut hele Buc data fra rina via eux-rina-api")
        return euxService.getBuc(rinanr)
    }

    @ApiOperation("Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}/name")
    fun getProcessDefinitionName(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut definisjonsnavn (type type) på valgt Buc")
        return getBucUtils(rinanr).getProcessDefinitionName()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (type)")
    @GetMapping("/{rinanr}/creator")
    fun getCreator(@PathVariable(value = "rinanr", required = true) rinanr: String): Creator? {

        logger.debug("Henter ut Creator på valgt Buc")
        return getBucUtils(rinanr).getCreator()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon landkode på valgt caseid (type)")
    @GetMapping("/{rinanr}/creator/countryCode")
    fun getCreatorCountryCode(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut CountryCode på Creator på valgt Buc")
        return mapAnyToJson(getBucUtils(rinanr).getCreatorContryCode())
    }

    @ApiOperation("Henter opp internationalid på caseid (type)")
    @GetMapping("/{rinanr}/internationalId")
    fun getInternationalId(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut InternationalId på valgt Buc")
        return getBucUtils(rinanr).getInternatinalId()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (type)")
    @GetMapping("/{rinanr}/allDocuments")
    fun getAllDocuments(@PathVariable(value = "rinanr", required = true) rinanr: String): List<ShortDocumentItem> {

        logger.debug("Henter ut documentId på alle dokumenter som finnes på valgt type")
        return getBucUtils(rinanr).getAllDocuments()
    }

    @ApiOperation("Henter opp mulige aksjon(er) som kan utføres på valgt buc")
    @GetMapping("/{rinanr}/aksjoner")
    fun getMuligeAksjoner(@PathVariable(value = "rinanr", required = true) rinanr: String): List<String> {
        logger.debug("Henter ut muligeaksjoner på valgt buc ${rinanr}")
        return getBucUtils(rinanr).getAksjonListAsString()
    }

    @ApiOperation("Henter ut en liste over saker på valgt aktoerid. ny api kall til eux")
    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<Rinasak> {

        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")
        val fnr = hentAktoerIdPin(aktoerId)
        return euxService.getRinasaker(fnr)
    }

    //ny view call for bucogsed design pr 01.04-01.05)
    @ApiOperation("Henter ut en json struktur for type og sed menyliste for ui. ny api kall til eux")
    @GetMapping("/detaljer/{aktoerid}", "/detaljer/{aktoerid}/{sakid}", "/detaljer/{aktoerId}/{sakId}/{euxcaseid}")
    fun getBucogSedView(@PathVariable("aktoerid", required = true) aktoerid: String,
                        @PathVariable("sakid", required = false) sakid: String? = "",
                        @PathVariable("euxcaseid", required = false) euxcaseid: String? = ""): List<BucAndSedView> {


        logger.debug("1 prøver å dekode til fnr fra aktoerid: $aktoerid")
        val fnr = hentAktoerIdPin(aktoerid)
        return euxService.getBucAndSedView(fnr, aktoerid, sakid, euxcaseid, euxService)

    }

    //flyttes to BucController
    @ApiOperation("Oppretter ny tom BUC i RINA via eux-api. ny api kall til eux")
    @PostMapping("/{buctype}")
    fun createBuc(@PathVariable("buctype", required = true) buctype: String): BucAndSedView {
        logger.debug("Prøver å opprette en ny BUC i RINA av type: $buctype")

        //rinaid
        val euxCaseId = euxService.createBuc(buctype)
        logger.info("Mottatt følgende euxCaseId(RinaID): $euxCaseId")

        //create bucDetail back from newly created buc call eux-rina-api to get data.
        return euxService.createBucDetails(euxCaseId, "", euxService)
    }

    @ApiOperation("Oppretter nye deltaker(e) på valgt buc. ny api kall til eux")
    @PutMapping("/deltaker/{euxcaseid}/{deltaker}")
    fun putBucDeltager(@PathVariable("euxcaseid", required = true) euxCaseId: String,
                       @PathVariable("deltaker", required = true) deltaker: List<InstitusjonItem>): Boolean {

        logger.debug("Prøver å legger til deltaker på valgt buc")
        return euxService.addDeltagerInstitutions(euxCaseId, deltaker)
    }

    @ApiOperation("Legger til et vedlegg for det gitte dokumentet")
    @PutMapping("/vedlegg/{aktoerId}/{rinaSakId}/{rinaDokumentId}/{joarkJournalpostId}/{joarkDokumentInfoId}/{variantFormat}")
    fun putVedleggTilDokument(@PathVariable("aktoerId", required = true) aktoerId: String,
                              @PathVariable("rinaSakId", required = true) rinaSakId: String,
                              @PathVariable("rinaDokumentId", required = true) rinaDokumentId: String,
                              @PathVariable("joarkJournalpostId", required = true) joarkJournalpostId: String,
                              @PathVariable("joarkDokumentInfoId", required = true) joarkDokumentInfoId : String,
                              @PathVariable("variantFormat", required = true) variantFormat : VariantFormat) : ResponseEntity<String> {
        logger.debug("Legger til vedlegg: joarkJournalpostId: $joarkJournalpostId, joarkDokumentInfoId $joarkDokumentInfoId, variantFormat: $variantFormat til " +
                "rinaSakId: $rinaSakId, rinaDokumentId: $rinaDokumentId")

        return try {
            euxService.leggTilVedleggPaaDokument(aktoerId,
                    rinaSakId,
                    rinaDokumentId,
                    joarkJournalpostId,
                    joarkDokumentInfoId,
                    variantFormat)
            return ResponseEntity.ok().body(successBody())
        } catch(ex: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(ex.message!!, UUID.randomUUID().toString()))
        }
    }

    private fun getMuligeAksjonerFilter(list: List<RinaAksjon>, filter: String = ""): List<RinaAksjon> {
        return list.filter { it.dokumentType?.startsWith(filter)!! }.toList()
    }

    private fun getBucUtils(rinanr: String): BucUtils {
        return euxService.getBucUtils(rinanr)
    }
}
