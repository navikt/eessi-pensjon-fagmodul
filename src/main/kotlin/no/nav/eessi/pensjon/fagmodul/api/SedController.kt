package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.fagmodul.eux.PinOgKrav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.helper.AktoerIdHelper
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*


@Protected
@RestController
@RequestMapping("/sed")
class SedController(private val euxService: EuxService,
                    private val prefillService: PrefillService,
                    private val aktoerIdHelper: AktoerIdHelper,
                    private val brokerMessagingTemplate: SimpMessagingTemplate) {

    private val logger = LoggerFactory.getLogger(SedController::class.java)

    //** oppdatert i api 18.02.2019
    @ApiOperation("Genereren en Nav-Sed (SED), viser en oppsumering av SED. Før evt. innsending til EUX/Rina")
    @PostMapping("/preview", consumes = ["application/json"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun confirmDocument(@RequestBody request: ApiRequest): SED {
        logger.info("kaller /preview med request: $request")
        return prefillService.prefillSed(ApiRequest.buildPrefillDataModelConfirm(request, aktoerIdHelper.hentPinForAktoer(request.aktoerId))).sed
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
    @GetMapping("/get/{euxcaseid}/{documentid}")
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
    fun addInstitutionAndDocument(@RequestBody request: ApiRequest): ShortDocumentItem {

        logger.info("kaller add (institutions and sed)")

        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, aktoerIdHelper.hentPinForAktoer(request.aktoerId))

        logger.debug("Prøver å legge til Deltaker/Institusions på buc samt prefillSed og sende inn til Rina ")
        val bucUtil = BucUtils(euxService.getBuc(dataModel.euxCaseID))
        val nyeDeltakere = bucUtil.findNewParticipants(dataModel.getInstitutionsList())
        if (nyeDeltakere.isNotEmpty()) {
            logger.debug("DeltakerListe (InstitusjonItem) size: ${nyeDeltakere.size}")
            val bucX005 = bucUtil.findFirstDocumentItemByType("X005")
            if (bucX005 == null) {
                logger.debug("X005 finnes ikke på buc, legger til Deltakere/Institusjon på vanlig måte")
                euxService.addDeltagerInstitutions(dataModel.euxCaseID, nyeDeltakere)
            } else {
                logger.debug("X005 finnes på buc, Sed X005 prefills og sendes inn")
                val x005Liste = prefillService.prefillEnX005ForHverInstitusjon(nyeDeltakere, dataModel)
                x005Liste.forEach { x005 -> euxService.opprettSedOnBuc(x005.sed, x005.euxCaseID) }
            }
        }
        val data = prefillService.prefillSed(dataModel)
        logger.debug("Prøver å sende SED:${dataModel.getSEDid()} inn på buc: ${dataModel.euxCaseID}")
        val docresult = euxService.opprettSedOnBuc(data.sed, data.euxCaseID)
        if (brokerMessagingTemplate != null) {
            brokerMessagingTemplate.convertAndSend("/sed",
                    mapAnyToJson(mapOf("action" to "Create", "payload" to request)))
        }
        return BucUtils(euxService.getBuc(docresult.caseId)).findDocument(docresult.documentId)
    }

    //** oppdatert i api 18.02.2019
    @ApiOperation("legge til SED på et eksisterende Rina document. kjører preutfylling, ny api kall til eux")
    @PostMapping("/addSed")
    fun addDocument(@RequestBody request: ApiRequest): ShortDocumentItem {
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, aktoerIdHelper.hentPinForAktoer(request.aktoerId))
        val data = prefillService.prefillSed(dataModel)
        logger.info("kaller add med request: $request")
        val docresult = euxService.opprettSedOnBuc(data.sed, data.euxCaseID)
        return BucUtils(euxService.getBuc(docresult.caseId)).findDocument(docresult.documentId)

    }


    //** oppdatert i api 18.02.2019
    @ApiOperation("Kjører prosess OpprettBuCogSED på EUX for å få opprette et RINA dokument med en SED, ny api kall til eux")
    @PostMapping("/buc/create")
    fun createDocument(@RequestBody request: ApiRequest): BucSedResponse {

        logger.info("kaller type/create med request: $request")
        val dataModel = ApiRequest.buildPrefillDataModelOnNew(request, aktoerIdHelper.hentPinForAktoer(request.aktoerId))
        val data = prefillService.prefillSed(dataModel)
        val firstInstitution =
                data.institution.firstOrNull() ?: throw ManglendeInstitusjonException("institusjon kan ikke være tom")
        return euxService.opprettBucSed(data.sed, data.buc, firstInstitution.institution, data.penSaksnummer)

    }

    //** oppdatert i api 18.02.2019 -- går ut da den nå likker i BuController
    @ApiOperation("Henter ut en liste av documents på valgt type. ny api kall til eux")
    @GetMapping("/buc/{euxcaseid}/shortdocumentslist")
    fun getShortDocumentList(@PathVariable("euxcaseid", required = true) euxcaseid: String): List<ShortDocumentItem> {

        logger.info("kaller /type/${euxcaseid}/documents ")
        return BucUtils(euxService.getBuc(euxcaseid)).getAllDocuments()
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

        val resultListe = BucUtils(euxService.getBuc(euxCaseId)).getAksjonListAsString()

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

}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class ManglendeInstitusjonException(message: String) : IllegalArgumentException(message)

internal fun List<String>.filterPensionSedAndSort() = this.filter { it.startsWith("P").or( it.startsWith("H12").or( it.startsWith("H07"))) }.filterNot { it.startsWith("P3000") }.sorted()
