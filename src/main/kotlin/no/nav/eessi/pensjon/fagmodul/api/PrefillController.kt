package no.nav.eessi.pensjon.fagmodul.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Protected
@RestController
class PrefillController(
    @Value("\${NAIS_NAMESPACE}") val nameSpace: String,
    private val euxPrefillService: EuxPrefillService,
    private val euxInnhentingService: EuxInnhentingService,
    private val innhentingService: InnhentingService,
    private val prefillService: PrefillService,
    private val auditlogger: AuditLogger,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(PrefillController::class.java)

    private lateinit var addInstutionAndDocument: MetricsHelper.Metric
    private lateinit var addDocumentToParent: MetricsHelper.Metric
    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric
    private lateinit var addDocumentToParentBucUtils: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        addInstutionAndDocument =
            metricsHelper.init("AddInstutionAndDocument", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParent =
            metricsHelper.init("AddDocumentToParent", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocumentBucUtils =
            metricsHelper.init("AddInstutionAndDocumentBucUtils", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParentBucUtils =
            metricsHelper.init("AddDocumentToParentBucUtils", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
    }

    @ApiOperation("Generer en Nav-Sed (SED), viser en oppsumering av SED (json). Før evt. innsending til EUX/Rina")
    @PostMapping("sed/prefill", consumes = ["application/json"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun prefillDocument(
        @RequestBody request: ApiRequest,
        @PathVariable("filter", required = false) filter: String? = null
    ): String {
        auditlogger.log("previewDocument", request.aktoerId ?: "", request.toAudit())
        logger.info("Prefiller : ${request.sed}")
        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, innhentingService.getAvdodAktoerIdPDL(request))

        val personcollection = innhentingService.hentPersonData(dataModel)
        return prefillService.prefillSedtoJson(dataModel, "4.2", personcollection).sed
    }

    @ApiOperation("Oppretter ny tom BUC i RINA via eux-api. ny api kall til eux")
    @PostMapping("buc/{buctype}")
    fun createBuc(@PathVariable("buctype", required = true) buctype: String): BucAndSedView {
        auditlogger.log("createBuc")
        logger.info("Prøver å opprette en ny BUC i RINA av type: $buctype")

        //rinaid
        val euxCaseId = euxPrefillService.createBuc(buctype)
        logger.info("Mottatt følgende euxCaseId(RinaID): $euxCaseId")

        //wait 5 sec before getBuc metadata to UI
        try {
            TimeUnit.SECONDS.sleep(12)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        //create bucDetail back from newly created buc call eux-rina-api to get data.
        val buc = euxInnhentingService.getBuc(euxCaseId)

        logger.info("Føgende bucdetalj er hentet: ${buc.processDefinitionName}, id: ${buc.id}")

        return BucAndSedView.from(buc)
    }

    @ApiOperation("Legge til Deltaker(e) og SED på et eksisterende Rina document. kjører preutfylling, ny api kall til eux")
    @PostMapping("sed/add")
    fun addInstutionAndDocument(@RequestBody request: ApiRequest): DocumentsItem? {
        auditlogger.log("addInstutionAndDocument", request.aktoerId ?: "", request.toAudit())
        logger.info("Legger til institusjoner og SED for rinaId: ${request.euxCaseId} bucType: ${request.buc} sedType: ${request.sed} aktoerId: ${request.aktoerId} sakId: ${request.sakId} vedtak: ${request.vedtakId}")
        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, innhentingService.getAvdodAktoerIdPDL(request))

        //Hente metadata for valgt BUC
        val bucUtil = euxInnhentingService.kanSedOpprettes(dataModel)
        val personData = innhentingService.hentPersonData(dataModel)

        //Preutfyll av SED, pensjon og personer samt oppdatering av versjon
        val sedAndType = prefillService.prefillSedtoJson(
            dataModel,
            bucUtil.getProcessDefinitionVersion(),
            personData
        )

        //Sjekk og opprette deltaker og legge sed på valgt BUC
        return addInstutionAndDocument.measure {
            logger.info("******* Legge til ny SED - start *******")

            val sedType = sedAndType.sedType
            val sedJson = sedAndType.sed

            val nyeInstitusjoner = bucUtil.findNewParticipants(dataModel.getInstitutionsList())
            val x005Liste = prefillService.prefillEnX005ForHverInstitusjon(nyeInstitusjoner, dataModel, personData)

            //sjekk og evt legger til deltakere
            euxPrefillService.checkAndAddInstitution(dataModel, bucUtil, x005Liste)

            logger.info("Prøver å sende SED: ${dataModel.sedType} inn på BUC: ${dataModel.euxCaseID}")
            val docresult = euxPrefillService.opprettJsonSedOnBuc(sedJson, sedType, dataModel.euxCaseID, request.vedtakId)

            logger.info("Opprettet ny SED med dokumentId: ${docresult.documentId}")
            val result = bucUtil.findDocument(docresult.documentId)
            if (dataModel.melding != null || dataModel.melding != "") {
                result?.message = dataModel.melding
            }

            val documentItem = fetchBucAgainBeforeReturnShortDocument(dataModel.buc, docresult, result)
            logger.info("******* Legge til ny SED - slutt *******")
            documentItem
        }

    }

    @ApiOperation("Oppretter en Sed som svar på en forespørsel-Sed")
    @PostMapping("sed/replysed/{parentid}")
    fun addDocumentToParent(
        @RequestBody(required = true) request: ApiRequest,
        @PathVariable("parentid", required = true) parentId: String
    ): DocumentsItem? {
        auditlogger.log("addDocumentToParent", request.aktoerId ?: "", request.toAudit())
        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, innhentingService.getAvdodAktoerIdPDL(request))

        //Hente metadata for valgt BUC
        val bucUtil = addDocumentToParentBucUtils.measure {
            logger.info("******* Hent BUC sjekk om sed kan opprettes *******")
            BucUtils(euxInnhentingService.getBuc(dataModel.euxCaseID)).also { bucUtil ->
                //sjekk for om deltakere alt er fjernet med x007 eller x100 sed
                bucUtil.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(dataModel.getInstitutionsList())
                //sjekk om en svarsed kan opprettes eller om den alt finnes
                bucUtil.checkIfSedCanBeCreatedEmptyStatus(dataModel.sedType, parentId)
            }
        }

        logger.info("Prøver å prefillSED (svarSED) parentId: $parentId")
        val personcollection = innhentingService.hentPersonData(dataModel)
        val sedAndType =
            prefillService.prefillSedtoJson(dataModel, bucUtil.getProcessDefinitionVersion(), personcollection)

        return addDocumentToParent.measure {
            logger.info("Prøver å sende SED: ${dataModel.sedType} inn på BUC: ${dataModel.euxCaseID}")

            val docresult =
                euxPrefillService.opprettSvarJsonSedOnBuc(sedAndType.sed, dataModel.euxCaseID, parentId, request.vedtakId)

            val parent = bucUtil.findDocument(parentId)
            val result = bucUtil.findDocument(docresult.documentId)

            val documentItem = fetchBucAgainBeforeReturnShortDocument(dataModel.buc, docresult, result)

            logger.info("Buc: (${dataModel.euxCaseID}, hovedSED type: ${parent?.type}, docId: ${parent?.id}, svarSED type: ${documentItem?.type} docID: ${documentItem?.id}")
            logger.info("******* Legge til svarSED - slutt *******")
            documentItem
        }
    }

    fun fetchBucAgainBeforeReturnShortDocument(
        bucType: String,
        bucSedResponse: BucSedResponse,
        orginal: DocumentsItem?
    ): DocumentsItem? {
        return if (bucType == "P_BUC_06") {
            logger.info("Henter BUC på nytt for buctype: $bucType")
            val buc = euxInnhentingService.getBuc(bucSedResponse.caseId)
            val bucUtil = BucUtils(buc)
            bucUtil.findDocument(bucSedResponse.documentId)
        } else {
            orginal
        }
    }

}
