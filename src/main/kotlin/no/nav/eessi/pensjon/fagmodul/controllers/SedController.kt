package no.nav.eessi.pensjon.fagmodul.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.fagmodul.models.*
import no.nav.eessi.pensjon.fagmodul.person.AktoerIdHelper
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.services.PrefillService
import no.nav.eessi.pensjon.fagmodul.services.eux.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.services.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.services.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@Protected
@RestController
@RequestMapping("/sed")
class SedController(private val euxService: EuxService,
                    private val prefillService: PrefillService,
                    private val aktoerIdHelper: AktoerIdHelper) {

    private val logger = LoggerFactory.getLogger(SedController::class.java)

    //** oppdatert i api 18.02.2019
    @ApiOperation("Genereren en Nav-Sed (SED), viser en oppsumering av SED. Før evt. innsending til EUX/Rina")
    @PostMapping("/preview", consumes = ["application/json"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun confirmDocument(@RequestBody request: ApiRequest): SED {
        logger.info("kaller /preview med request: $request")
        return prefillService.prefillSed(buildPrefillDataModelConfirm(request)).sed
    }


    //** oppdatert i api 18.02.2019
    @ApiOperation("Sender valgt NavSed på rina med valgt documentid og bucid, ut til eu/eøs, ny api kall til eux")
    @GetMapping("/send/{euxcaseid}/{documentid}")
    fun sendSed(@PathVariable("euxcaseid", required = true) euxCaseId: String,
                @PathVariable("documentid", required = true) documentid: String): Boolean {

        logger.info("kaller /type/${euxCaseId}/sed/${documentid}/send med request: $euxCaseId / $documentid")
        return euxService.sendDocumentById(euxCaseId, documentid)

    }

    //** oppdatert i api 18.02.2019
    @ApiOperation("henter ut en SED fra et eksisterende Rina document. krever unik dokumentid fra valgt SED, ny api kall til eux")
    @GetMapping("/{euxcaseid}/{documentid}")
    fun getDocument(@PathVariable("euxcaseid", required = true) euxcaseid: String,
                    @PathVariable("documentid", required = true) documentid: String): SED {

        logger.info("kaller /${euxcaseid}/${documentid} ")
        return euxService.getSedOnBucByDocumentId(euxcaseid, documentid)

    }

    //** oppdatert i api 18.02.2019
    @ApiOperation("sletter SED fra et eksisterende Rina document. krever unik dokumentid fra valgt SED, ny api kall til eux")
    @DeleteMapping("/{euxcaseid}/{documentid}")
    fun deleteDocument(@PathVariable("euxcaseid", required = true) euxcaseid: String,
                       @PathVariable("documentid", required = true) documentid: String): Boolean {
        logger.info("kaller delete  /${euxcaseid}/${documentid} ")
        return euxService.deleteDocumentById(euxcaseid, documentid)

    }

    //** oppdatert i api 18.02.2019
    @ApiOperation("legge til SED på et eksisterende Rina document. kjører preutfylling, ny api kall til eux")
    @PostMapping("/add")
    fun addInstutionAndDocument(@RequestBody request: ApiRequest): ShortDocumentItem {

        logger.info("kaller add (institutions and sed)")

        return prefillService.prefillAndAddInstitusionAndSedOnExistingCase(buildPrefillDataModelOnExisting(request))

    }

    //** oppdatert i api 18.02.2019
    @ApiOperation("legge til SED på et eksisterende Rina document. kjører preutfylling, ny api kall til eux")
    @PostMapping("/addSed")
    fun addDocument(@RequestBody request: ApiRequest): ShortDocumentItem {

        logger.info("kaller add med request: $request")
        return prefillService.prefillAndAddSedOnExistingCase(buildPrefillDataModelOnExisting(request))

    }


    //** oppdatert i api 18.02.2019
    @ApiOperation("Kjører prosess OpprettBuCogSED på EUX for å få opprette et RINA dokument med en SED, ny api kall til eux")
    @PostMapping("/buc/create")
    fun createDocument(@RequestBody request: ApiRequest): BucSedResponse {

        logger.info("kaller type/create med request: $request")
        return prefillService.prefillAndCreateSedOnNewCase(buildPrefillDataModelOnNew(request))

    }

    //** oppdatert i api 18.02.2019 -- går ut da den nå likker i BuController
    @ApiOperation("Henter ut en liste av documents på valgt type. ny api kall til eux")
    @GetMapping("/buc/{euxcaseid}/shortdocumentslist")
    fun getShortDocumentList(@PathVariable("euxcaseid", required = true) euxcaseid: String): List<ShortDocumentItem> {

        logger.info("kaller /type/${euxcaseid}/documents ")
        return euxService.getBucUtils(euxcaseid).getAllDocuments()
    }

    @ApiOperation("henter ut en liste av SED fra en valgt type, men bruk av sedType. ny api kall til eux")
    @GetMapping("list/{euxcaseid}/{sedtype}")
    fun getDocumentlist(@PathVariable("euxcaseid", required = true) euxcaseid: String,
                        @PathVariable("sedtype", required = false) sedType: String?): List<SED> {
        logger.info("kaller /${euxcaseid}/${sedType} ")
        return euxService.getSedOnBuc(euxcaseid, sedType)
    }

    @ApiOperation("Henter ut en liste over registrerte institusjoner innenfor spesifiserte EU-land. ny api kall til eux")
    @GetMapping("/institusjoner/{buctype}", "/institusjoner/{buctype}/{land}")
    fun getEuxInstitusjoner(@PathVariable("buctype", required = true) buctype: String, @PathVariable("land", required = false) landkode: String? = ""): List<String> {
        logger.info("Henter ut liste over alle Institusjoner i Rina")
        return euxService.getInstitutions(buctype, landkode).sorted()
    }

    @ApiOperation("henter liste over seds, seds til valgt buc eller seds til valgt rinasak")
    @GetMapping("/seds", "/seds/{buctype}", "/seds/{buctype}/{rinanr}")
    fun getSeds(@PathVariable(value = "buctype", required = false) bucType: String?,
                @PathVariable(value = "rinanr", required = false) euxCaseId: String?): ResponseEntity<String?> {

        if (euxCaseId == null) return ResponseEntity.ok().body(mapAnyToJson(EuxService.getAvailableSedOnBuc(bucType)))

        val resultListe = euxService.getBucUtils(euxCaseId).getAksjonListAsString()

        if (resultListe.isEmpty()) return ResponseEntity.ok().body(mapAnyToJson(EuxService.getAvailableSedOnBuc(bucType)))

        return ResponseEntity.ok().body(mapAnyToJson(resultListe.filterPensionSedAndSort()))
    }

    //ny knall for journalforing app henter ytelsetype ut ifra P15000
    @ApiOperation("Henter ytelsetype fra P15000 på valgt Buc og Documentid")
    @GetMapping("/ytelseKravtype/{rinanr}/sedid/{documentid}")
    fun getPinOgYtelseKravtype(@PathVariable("rinanr", required = true) rinanr: String,
                               @PathVariable("documentid", required = false) documentid: String): PinOgKrav {

        logger.debug("Henter opp ytelseKravType fra P2100 eller P15000, feiler hvis ikke rett SED")
        return euxService.hentFnrOgYtelseKravtype(rinanr, documentid)

    }


    //validatate request and convert to PrefillDataModel
    fun buildPrefillDataModelOnExisting(request: ApiRequest): PrefillDataModel {
        return when {
            //request.sakId == null -> throw IkkeGyldigKallException("Mangler Saksnummer")
            request.sed == null -> throw IkkeGyldigKallException("Mangler SED")
            request.aktoerId == null -> throw IkkeGyldigKallException("Mangler AktoerID")
            request.euxCaseId == null -> throw IkkeGyldigKallException("Mangler euxCaseId (RINANR)")
            request.institutions == null -> throw IkkeGyldigKallException("Mangler Institusjoner")

            SEDType.isValidSEDType(request.sed) -> {
                logger.info("ALL SED on existing Rina -> SED: ${request.sed} -> euxCaseId: ${request.sakId}")
                val pinid = aktoerIdHelper.hentAktoerIdPin(request.aktoerId)
                PrefillDataModel().apply {
                    penSaksnummer = request.sakId
                    sed = SED.create(request.sed)
                    aktoerID = request.aktoerId
                    personNr = pinid
                    euxCaseID = request.euxCaseId
                    institution = request.institutions
                    vedtakId = request.vedtakId ?: ""
                    partSedAsJson[request.sed] = request.payload ?: "{}"
                    skipSedkey = request.skipSEDkey ?: listOf("PENSED") //skipper all pensjon utfylling untatt kravdato
                }
            }
            else -> throw IkkeGyldigKallException("Mangler SED, eller ugyldig type SED")
        }
    }

    //validatate request and convert to PrefillDataModel
    fun buildPrefillDataModelOnNew(request: ApiRequest): PrefillDataModel {
        return when {
            //request.sakId == null -> throw IkkeGyldigKallException("Mangler Saksnummer")
            request.sed == null -> throw IkkeGyldigKallException("Mangler SED")
            request.aktoerId == null -> throw IkkeGyldigKallException("Mangler AktoerID")
            request.buc == null -> throw IkkeGyldigKallException("Mangler BUC")
            request.subjectArea == null -> throw IkkeGyldigKallException("Mangler Subjekt/Sektor")
            request.institutions == null -> throw IkkeGyldigKallException("Mangler Institusjoner")

            //Denne validering og utfylling kan benyttes på SED P2000,P2100,P2200
            SEDType.isValidSEDType(request.sed) -> {
                logger.info("ALL SED on new RinaCase -> SED: ${request.sed}")
                val pinid = aktoerIdHelper.hentAktoerIdPin(request.aktoerId)
                PrefillDataModel().apply {
                    penSaksnummer = request.sakId
                    buc = request.buc
                    rinaSubject = request.subjectArea
                    sed = SED.create(request.sed)
                    aktoerID = request.aktoerId
                    personNr = pinid
                    institution = request.institutions
                    vedtakId = request.vedtakId ?: ""
                    partSedAsJson[request.sed] = request.payload ?: "{}"
                    skipSedkey = request.skipSEDkey ?: listOf("PENSED")
                }
            }
            else -> throw IkkeGyldigKallException("Mangler SED, eller ugyldig type SED")
        }
    }

    //validatate request and convert to PrefillDataModel
    fun buildPrefillDataModelConfirm(request: ApiRequest): PrefillDataModel {
        return when {
            //request.sakId == null -> throw IkkeGyldigKallException("Mangler Saksnummer")
            request.sed == null -> throw IkkeGyldigKallException("Mangler SED")
            request.aktoerId == null -> throw IkkeGyldigKallException("Mangler AktoerID")

            SEDType.isValidSEDType(request.sed) -> {
                PrefillDataModel().apply {
                    penSaksnummer = request.sakId
                    sed = SED.create(request.sed)
                    aktoerID = request.aktoerId
                    personNr = aktoerIdHelper.hentAktoerIdPin(request.aktoerId)
                    vedtakId = request.vedtakId ?: ""
                    partSedAsJson[request.sed] = request.payload ?: "{}"
//                    if (request.payload != null) {
//                        partSedAsJson[request.sed] = request.payload
//                    }
                    skipSedkey = request.skipSEDkey ?: listOf("PENSED")
                }
            }
            else -> throw IkkeGyldigKallException("Mangler SED, eller ugyldig type SED")
        }
    }

    //Samme som SedRequest i frontend-api
    data class ApiRequest(
            val sakId: String,
            val vedtakId: String? = null,
            val kravId: String? = null,
            val aktoerId: String? = null,
            val fnr: String? = null,
            val payload: String? = null,
            val buc: String? = null,
            val sed: String? = null,
            val documentid: String? = null,
            val euxCaseId: String? = null,
            val institutions: List<InstitusjonItem>? = null,
            val subjectArea: String? = null,
            val skipSEDkey: List<String>? = null,
            val mockSED: Boolean? = null
    )

}

internal fun List<String>.filterPensionSedAndSort() = this.filter { it.startsWith("P").or( it.startsWith("H12").or( it.startsWith("H07"))) }.filterNot { it.startsWith("P3000") }.sorted()
