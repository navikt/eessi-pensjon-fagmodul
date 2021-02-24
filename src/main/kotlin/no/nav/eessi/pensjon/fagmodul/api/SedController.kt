package no.nav.eessi.pensjon.fagmodul.api

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxConflictException
import no.nav.eessi.pensjon.fagmodul.eux.EuxRinaServerException
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.PinOgKrav
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.MangelfulleInndataException
import no.nav.eessi.pensjon.fagmodul.prefill.PersonDataService
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.CounterHelper
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import javax.annotation.PostConstruct

@Protected
@RestController
@RequestMapping("/sed")
class SedController(
    private val euxService: EuxService,
    private val prefillService: PrefillService,
    private val personService: PersonDataService,
    private val auditlogger: AuditLogger,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()),
    @Autowired(required = false) private val counterHelper: CounterHelper = CounterHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedController::class.java)

    private lateinit var addInstutionAndDocument: MetricsHelper.Metric
    private lateinit var addDocumentToParent: MetricsHelper.Metric
    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric
    private lateinit var addDocumentToParentBucUtils: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        addInstutionAndDocument = metricsHelper.init("AddInstutionAndDocument", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParent = metricsHelper.init("AddDocumentToParent", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocumentBucUtils = metricsHelper.init("AddInstutionAndDocumentBucUtils",  ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParentBucUtils = metricsHelper.init("AddDocumentToParentBucUtils",  ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
    }

    @ApiOperation("Generer en Nav-Sed (SED), viser en oppsumering av SED (json). Før evt. innsending til EUX/Rina")
    @PostMapping( "/prefill",  consumes = ["application/json"],  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun prefillDocument(@RequestBody request: ApiRequest, @PathVariable("filter", required = false) filter: String? = null): String {
        auditlogger.log("previewDocument", request.aktoerId ?: "", request.toAudit())
        logger.info("kaller (previewDocument) rinaId: ${request.euxCaseId} bucType: ${request.buc} sedType: ${request.sed} aktoerId: ${request.aktoerId} sakId: ${request.sakId} vedtak: ${request.vedtakId}")
        logger.debug("request: ${request.toJson()}")

        val norskIdent = hentFnrfraAktoerService(request.aktoerId, personService)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, getAvdodAktoerIdPDL(request))

        logger.debug(
            """
            ---------------------------------------------------------------------------
            har avdød: ${dataModel.avdod != null}, avdød: ${dataModel.avdod?.toJson()}
            søker    : ${dataModel.bruker.toJson()}
            ---------------------------------------------------------------------------
            """.trimIndent())

        val personcollection = personService.hentPersonData(dataModel)
        return prefillService.prefillSedtoJson(dataModel, "4.2", personcollection).sed
    }

    @ApiOperation("henter ut en SED fra et eksisterende Rina document. krever unik dokumentid fra valgt SED, ny api kall til eux")
    @GetMapping("/get/{euxcaseid}/{documentid}")
    fun getDocument(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String
    ): SED {
        auditlogger.logBuc("getDocument", " euxCaseId: $euxcaseid documentId: $documentid")

        logger.info("Prøver å kalle getDocument for /${euxcaseid}/${documentid} ")
        return euxService.getSedOnBucByDocumentId(euxcaseid, documentid)
    }

    @ApiOperation("legge til Deltaker(e) og SED på et eksisterende Rina document. kjører preutfylling, ny api kall til eux")
    @PostMapping("/add")
    fun addInstutionAndDocument(@RequestBody request: ApiRequest): ShortDocumentItem? {
        auditlogger.log("addInstutionAndDocument", request.aktoerId ?: "", request.toAudit())
        logger.info("kaller (addInstutionAndDocument) rinaId: ${request.euxCaseId} bucType: ${request.buc} sedType: ${request.sed} aktoerId: ${request.aktoerId} sakId: ${request.sakId} vedtak: ${request.vedtakId}")
        val norskIdent = hentFnrfraAktoerService(request.aktoerId, personService)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, getAvdodAktoerIdPDL(request))

        logger.debug(
            """
            ---------------------------------------------------------------------------
            har avdød: ${dataModel.avdod != null}, avdød: ${dataModel.avdod?.toJson()}
            søker    : ${dataModel.bruker.toJson()}
            ---------------------------------------------------------------------------
            """.trimIndent())

        //Hente metadata for valgt BUC
        val bucUtil = addInstutionAndDocumentBucUtils.measure {
            logger.info("******* Hent BUC sjekk om sed kan opprettes *******")
            BucUtils(euxService.getBuc(dataModel.euxCaseID)).also { bucUtil ->
                bucUtil.checkIfSedCanBeCreated(dataModel.sedType, dataModel.penSaksnummer)
            }
        }

        //Preutfyll av SED, pensjon og personer samt oppdatering av versjon
        val personcollection = personService.hentPersonData(dataModel)
        val sedAndType = prefillService.prefillSedtoJson(dataModel, bucUtil.getProcessDefinitionVersion(), personcollection)

        //Sjekk og opprette deltaker og legge sed på valgt BUC
        return addInstutionAndDocument.measure {
            logger.info("******* Legge til ny SED - start *******")

            val sedType = sedAndType.sedType
            val sedJson = sedAndType.sed

            //sjekk og evt legger til deltakere
            checkAndAddInstitution(dataModel, bucUtil, personcollection)

            logger.info("Prøver å sende SED: ${dataModel.sedType} inn på BUC: ${dataModel.euxCaseID}")
            val docresult = euxService.opprettJsonSedOnBuc(sedJson, sedType, dataModel.euxCaseID, request.vedtakId)

            logger.info("Opprettet ny SED med dokumentId: ${docresult.documentId}")
            val result = bucUtil.findDocument(docresult.documentId)
            if (dataModel.melding != null || dataModel.melding != "") {
                result?.message = dataModel.melding
            }

            //extra tag metricshelper for sedType, bucType, timeStamp og rinaId.
            counterHelper.count(CounterHelper.MeterNameExtraTag.AddInstutionAndDocument, extraTag = extraTag(dataModel, bucUtil))

            val documentItem = fetchBucAgainBeforeReturnShortDocument(dataModel.buc, docresult, result)
            logger.info("******* Legge til ny SED - slutt *******")
            documentItem
        }

    }

    fun fetchBucAgainBeforeReturnShortDocument(bucType: String, bucSedResponse: BucSedResponse, orginal: ShortDocumentItem?): ShortDocumentItem? {
        return if (bucType == "P_BUC_06") {
            logger.info("Henter BUC på nytt for buctype: $bucType")
            val buc = euxService.getBuc(bucSedResponse.caseId)
            val bucUtil = BucUtils(buc)
            logger.debug("Leter etter shortDocument med documentID: ${bucSedResponse.documentId}")
            bucUtil.findDocument(bucSedResponse.documentId)
        } else {
            logger.debug("Return orginal shortDocument fra første buc")
            orginal
        }
    }

    //flyttes til prefill / en eller annen service?
    fun updateSEDVersion(sed: SED, bucVersion: String) {
        when (bucVersion) {
            "v4.2" -> {
                sed.sedGVer = "4"
                sed.sedVer = "2"
            }
            else -> {
                sed.sedGVer = "4"
                sed.sedVer = "1"
            }
        }
        logger.debug("SED version: v${sed.sedGVer}.${sed.sedVer} + BUC version: $bucVersion")
    }

    fun checkAndAddInstitution(dataModel: PrefillDataModel, bucUtil: BucUtils, personcollection: PersonDataCollection) {
        logger.info("Hvem er caseOwner: ${bucUtil.getCaseOwner()?.toJson()} på buc: ${bucUtil.getProcessDefinitionName()}")
        val navCaseOwner = bucUtil.getCaseOwner()?.country == "NO"

        val nyeInstitusjoner = bucUtil.findNewParticipants(dataModel.getInstitutionsList())

        val x005 = bucUtil.findFirstDocumentItemByType(SEDType.X005)
        if (nyeInstitusjoner.isNotEmpty()) {
            if (x005 == null) {
                euxService.addInstitution(dataModel.euxCaseID, nyeInstitusjoner.map { it.institution })
            } else {
                nyeInstitusjoner.forEach {
                    if (!navCaseOwner && it.country != "NO") {
                        logger.error("NAV er ikke sakseier. Du kan ikke legge til deltakere utenfor Norge")
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "NAV er ikke sakseier. Du kan ikke legge til deltakere utenfor Norge")
                    }
                }
                addInstitutionMedX005(dataModel, nyeInstitusjoner, bucUtil.getProcessDefinitionVersion(), personcollection)
            }
        }
    }

    private fun addInstitutionMedX005(
        dataModel: PrefillDataModel,
        nyeInstitusjoner: List<InstitusjonItem>,
        bucVersion: String,
        personcollection: PersonDataCollection
    ) {
        logger.debug("Prøver å legge til Deltaker/Institusions på buc samt prefillSed og sende inn til Rina ")
        logger.info("X005 finnes på buc, Sed X005 prefills og sendes inn: ${nyeInstitusjoner.toJsonSkipEmpty()}")

        var execptionError: Exception? = null
        val x005Liste = prefillService.prefillEnX005ForHverInstitusjon(nyeInstitusjoner, dataModel, personcollection)

            x005Liste.forEach { x005 ->
                try {
                    updateSEDVersion(x005, bucVersion)
                    euxService.opprettJsonSedOnBuc(x005.toJson(), x005.type, dataModel.euxCaseID, dataModel.vedtakId)
                } catch (eux: EuxRinaServerException) {
                    execptionError = eux
                } catch (exx: EuxConflictException) {
                    execptionError = exx
                } catch (ex: Exception) {
                    execptionError = ex
                }
            }
        if (execptionError != null) {
            logger.error("Feiler ved oppretting av X005  (ny institusjon), euxCaseid: ${dataModel.euxCaseID}, sed: ${dataModel.sedType}", execptionError)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Feiler ved oppretting av X005 (ny institusjon) for euxCaseId: ${dataModel.euxCaseID}")
        }

    }

    private fun extraTag(dataModel: PrefillDataModel, bucUtil: BucUtils): List<Tag> {
        return listOf(
            Tag.of("sedType", dataModel.sedType.name),
            Tag.of("bucType", dataModel.buc),
            Tag.of("rinaId", dataModel.euxCaseID),
            Tag.of("sakNr", dataModel.penSaksnummer),
            Tag.of("land", bucUtil.getParticipantsLand()),
            Tag.of("type", "Opprett"),
            Tag.of("timeStamp", LocalDateTime.now().toString())
        )
    }

    @ApiOperation("Oppretter en Sed som svar på en forespørsel-Sed")
    @PostMapping("/replysed/{parentid}")
    fun addDocumentToParent(
        @RequestBody(required = true) request: ApiRequest,
        @PathVariable("parentid", required = true) parentId: String
    ): ShortDocumentItem? {
        auditlogger.log("addDocumentToParent", request.aktoerId ?: "", request.toAudit())
        val norskIdent = hentFnrfraAktoerService(request.aktoerId, personService)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, getAvdodAktoerIdPDL(request))

        //Hente metadata for valgt BUC
        val bucUtil = addDocumentToParentBucUtils.measure {
            logger.info("******* Hent BUC sjekk om sed kan opprettes *******")
            BucUtils(euxService.getBuc(dataModel.euxCaseID))
            // finnes det en SED i buc med parentid --!!??

            // <-- P8000 --  id P8000.. status= reviced ,;  P9000 -> out - id, parentid = P8000->id . status = empty
            // sedType, Buc, parentId. eux-Rinaid...  STATUS = EMPTY OK! HVIS IKKE badreqest
        }

        logger.info("Prøver å prefillSED (svarSED) parentId: $parentId")
        val personcollection = personService.hentPersonData(dataModel)
        val sedAndType = prefillService.prefillSedtoJson(dataModel, bucUtil.getProcessDefinitionVersion(), personcollection)

        return addDocumentToParent.measure {
            logger.info("Prøver å sende SED: ${dataModel.sedType} inn på BUC: ${dataModel.euxCaseID}")

            val docresult = euxService.opprettSvarJsonSedOnBuc(sedAndType.sed, dataModel.euxCaseID, parentId, request.vedtakId)

            val parent = bucUtil.findDocument(parentId)
            val result = bucUtil.findDocument(docresult.documentId)

            val documentItem = fetchBucAgainBeforeReturnShortDocument(dataModel.buc, docresult, result)

            logger.info("Buc: (${dataModel.euxCaseID}, hovedSED type: ${parent?.type}, docId: ${parent?.id}, svarSED type: ${documentItem?.type} docID: ${documentItem?.id}")
            logger.info("******* Legge til svarSED - slutt *******")
            documentItem
        }
    }

    //TODO endre denne til å gå til denne: /cpi/buc/{RinaSakId}/sedtyper  (.... for benytte seg av egen bucutil)
    @ApiOperation("henter ut en liste av SED fra en valgt type, men bruk av sedType. ny api kall til eux")
    @GetMapping("list/{euxcaseid}/{sedtype}")
    fun getDocumentlist(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("sedtype", required = false) sedType: SEDType?
    ): List<SED> {
        auditlogger.logBuc("getDocumentlist", " euxCaseId: $euxcaseid")
        logger.info("kaller /${euxcaseid}/${sedType} ")
        return euxService.getSedOnBuc(euxcaseid, sedType)
    }

    @ApiOperation("Henter ut en liste over registrerte institusjoner innenfor spesifiserte EU-land. ny api kall til eux")
    @GetMapping("/institutions/{buctype}", "/institutions/{buctype}/{countrycode}")
    fun getEuxInstitusjoner(
        @PathVariable("buctype", required = true) buctype: String,
        @PathVariable("countrycode", required = false) landkode: String? = ""
    ): List<InstitusjonItem> {
        logger.info("Henter ut liste over alle Institusjoner i Rina")
        return euxService.getInstitutions(buctype, landkode)
    }

    @ApiOperation("henter liste over seds som kan opprettes til valgt rinasak")
    @GetMapping("/seds/{buctype}/{rinanr}")
    fun getSeds(
        @PathVariable(value = "buctype", required = true) bucType: String,
        @PathVariable(value = "rinanr", required = true) euxCaseId: String
    ): ResponseEntity<String?> {
        val resultListe = BucUtils(euxService.getBuc(euxCaseId)).getFiltrerteGyldigSedAksjonListAsString()
        logger.debug("Tilgjengelige sed som kan opprettes på buctype: $bucType seds: $resultListe")
        return ResponseEntity.ok().body(resultListe.toJsonSkipEmpty())
    }

    //Hjelpe funksjon for å validere og hente aktoerid for evt. avdodfnr fra UI (P2100) - PDL
    fun getAvdodAktoerIdPDL(request: ApiRequest): String? {
        val buc = request.buc ?: throw MangelfulleInndataException("Mangler Buc")
        return when (buc) {
            "P_BUC_02" -> {
                val norskIdent = request.riktigAvdod() ?: run {
                    logger.error("Mangler fnr for avdød")
                    throw MangelfulleInndataException("Mangler fnr for avdød")
                }
                if (norskIdent.isBlank()) {
                    logger.debug("Ident har tom input")
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ident har tom input-verdi")
                }
                personService.hentIdent(IdentType.AktoerId, NorskIdent(norskIdent)).id
            }
            "P_BUC_05","P_BUC_06","P_BUC_10" -> {
                val norskIdent = request.riktigAvdod() ?: return null
                if (norskIdent.isBlank()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ident har tom input-verdi")
                }
                personService.hentIdent(IdentType.AktoerId, NorskIdent(norskIdent)).id
            }
            else -> null
        }
    }

}
