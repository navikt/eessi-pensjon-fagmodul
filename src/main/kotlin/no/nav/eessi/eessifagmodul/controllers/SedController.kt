package no.nav.eessi.eessifagmodul.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.services.PrefillService
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.eux.Rinasak
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.BucAndSedView
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ShortDocumentItem
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*


@Protected
@RestController
@RequestMapping("/sed")
class SedController(private val euxService: EuxService,
                    private val prefillService: PrefillService,
                    private val aktoerregisterService: AktoerregisterService) {

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

        logger.info("kaller /buc/${euxCaseId}/sed/${documentid}/send med request: $euxCaseId / $documentid")
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
    fun addDocument(@RequestBody request: ApiRequest): BucSedResponse {
        logger.info("kaller add med request: $request")
        return prefillService.prefillAndAddSedOnExistingCase(buildPrefillDataModelOnExisting(request))

    }

    //** oppdatert i api 18.02.2019
    @ApiOperation("Kjører prosess OpprettBuCogSED på EUX for å få opprette et RINA dokument med en SED, ny api kall til eux")
    @PostMapping("/buc/create")
    fun createDocument(@RequestBody request: ApiRequest): BucSedResponse {
        logger.info("kaller buc/create med request: $request")
        return prefillService.prefillAndCreateSedOnNewCase(buildPrefillDataModelOnNew(request))

    }

    //** oppdatert i api 18.02.2019 -- går ut da den nå likker i BuController
    @ApiOperation("Henter ut en liste av documents på valgt buc. ny api kall til eux")
    @GetMapping("/buc/{euxcaseid}/shortdocumentslist")
    fun getShortDocumentList(@PathVariable("euxcaseid", required = true) euxcaseid: String): List<ShortDocumentItem> {
        logger.info("kaller /buc/${euxcaseid}/documents ")
        return euxService.getBucUtils(euxcaseid).getAllDocuments()
    }

    @ApiOperation("henter ut en liste av SED fra en valgt buc, men bruk av sedType. ny api kall til eux")
    @GetMapping("/{euxcaseid}/{sedtype}/list")
    fun getDocumentlist(@PathVariable("euxcaseid", required = true) euxcaseid: String,
                        @PathVariable("sedtype", required = false) sedType: String?): List<SED> {
        logger.info("kaller /${euxcaseid}/${sedType} ")
        return euxService.getSedOnBuc(euxcaseid, sedType)
    }

    @ApiOperation("Henter ut en liste over registrerte institusjoner innenfor spesifiserte EU-land. ny api kall til eux")
    @GetMapping("/institusjoner/{buctype}", "/institusjoner/{buctype}/{land}")
    fun getEuxInstitusjoner(@PathVariable("buctype", required = true) buctype: String, @PathVariable("land", required = false) landkode: String? = ""): List<String> {
        return euxService.getInstitutions(buctype, landkode).sorted()
    }


    @ApiOperation("Henter ut en liste over saker på valgt aktoerid. ny api kall til eux")
    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<Rinasak> {
        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")
        val fnr = hentAktoerIdPin(aktoerId)
        return euxService.getRinasaker(fnr)
    }

    //ny view call for bucogsed design pr 01.04-01.05)
    @ApiOperation("Henter ut en json struktur for buc og sed menyliste for ui. ny api kall til eux")
    @GetMapping("/{aktoerid}/bucdetaljer/", "/{aktoerid}/{sakid}/bucdetaljer/", "/{aktoerId}/{sakId}/{euxcaseid}/bucdetaljer/")
    fun getBucogSedView(@PathVariable("aktoerid", required = true) aktoerid: String,
                        @PathVariable("sakid", required = false) sakid: String? = "",
                        @PathVariable("euxcaseid", required = false) euxcaseid: String? = ""): List<BucAndSedView> {

        logger.debug("1 prøver å dekode til fnr fra aktoerid: $aktoerid")
        val fnr = hentAktoerIdPin(aktoerid)
        return euxService.getBucAndSedView(fnr, aktoerid, sakid, euxcaseid, euxService)
    }

    //validatate request and convert to PrefillDataModel
    fun buildPrefillDataModelOnExisting(request: ApiRequest): PrefillDataModel {
        return when {
            //request.sakId == null -> throw IkkeGyldigKallException("Mangler Saksnummer")
            request.sed == null -> throw IkkeGyldigKallException("Mangler SED")
            request.aktoerId == null -> throw IkkeGyldigKallException("Mangler AktoerID")
            request.euxCaseId == null -> throw IkkeGyldigKallException("Mangler euxCaseId (RINANR)")

            SEDType.isValidSEDType(request.sed) -> {
                println("ALL SED on existin Rina -> SED: ${request.sed} -> euxCaseId: ${request.sakId}")
                val pinid = hentAktoerIdPin(request.aktoerId)
                PrefillDataModel().apply {
                    penSaksnummer = request.sakId
                    sed = SED.create(request.sed)
                    aktoerID = request.aktoerId
                    personNr = pinid
                    euxCaseID = request.euxCaseId

                    vedtakId = request.vedtakId ?: ""
                    partSedAsJson[request.sed] = request.payload ?: ""
                    skipSedkey = request.skipSEDkey ?: listOf()
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
                println("ALL SED on new RinaCase -> SED: ${request.sed}")
                val pinid = hentAktoerIdPin(request.aktoerId)
                PrefillDataModel().apply {
                    penSaksnummer = request.sakId
                    buc = request.buc
                    rinaSubject = request.subjectArea
                    sed = SED.create(request.sed)
                    aktoerID = request.aktoerId
                    personNr = pinid
                    institution = request.institutions
                    vedtakId = request.vedtakId ?: ""
                    skipSedkey = request.skipSEDkey ?: listOf()
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
                    personNr = hentAktoerIdPin(request.aktoerId)
                    vedtakId = request.vedtakId ?: ""
                    if (request.payload != null) {
                        partSedAsJson[request.sed] = request.payload
                    }
                    skipSedkey = request.skipSEDkey ?: listOf()
                }
            }
            else -> throw IkkeGyldigKallException("Mangler SED, eller ugyldig type SED")
        }
    }

    @Throws(AktoerregisterException::class)
    fun hentAktoerIdPin(aktorid: String): String {
        if (aktorid.isBlank()) throw IkkeGyldigKallException("Mangler AktorId")
        return aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktorid)
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