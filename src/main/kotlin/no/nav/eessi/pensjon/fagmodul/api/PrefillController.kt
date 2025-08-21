package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.klient.BucSedResponse
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.PersonInfo
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException

@Protected
@RestController
class PrefillController(
    private val euxPrefillService: EuxPrefillService,
    private val euxInnhentingService: EuxInnhentingService,
    private val innhentingService: InnhentingService,
    private val gcpStorageService: GcpStorageService,
    private val auditlogger: AuditLogger,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(PrefillController::class.java)

    private lateinit var addDocumentToParent: MetricsHelper.Metric
    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric
    private lateinit var addDocumentToParentBucUtils: MetricsHelper.Metric
    init {
        addDocumentToParent = metricsHelper.init("AddDocumentToParent", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocumentBucUtils = metricsHelper.init("AddInstutionAndDocumentBucUtils", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParentBucUtils = metricsHelper.init("AddDocumentToParentBucUtils", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
    }

    @PostMapping("buc/{buctype}")
    fun createBuc(
        @PathVariable("buctype", required = true) buctype: String
    ): BucAndSedView {
        auditlogger.log("createBuc")
        logger.info("Prøver å opprette en ny BUC i RINA av type: $buctype")

        //rinaid
        val euxCaseId = euxPrefillService.createdBucForType(buctype)
        logger.info("Mottatt følgende euxCaseId(RinaID): $euxCaseId")

        //create bucDetail back from newly created buc call eux-rina-api to get data.
        val buc = euxInnhentingService.getBuc(euxCaseId)

        logger.info("Følgende bucdetalj er hentet: ${buc.processDefinitionName}, id: ${buc.id}")

        return BucAndSedView.from(buc)
    }

    fun createBuc(
        buctype: String,
        gjennySak: GjennySak? = null
    ): BucAndSedView {
        auditlogger.log("createBuc")
        logger.info("Prøver å opprette en ny BUC $buctype i RINA med GjennySakId: ${gjennySak?.sakId} med saktype: ${gjennySak?.sakType}.")

        return createBuc(buctype).also {
            gcpStorageService.lagreGjennySak(it.caseId, GjennySak(gjennySak?.sakId!!, gjennySak.sakType))
        }
    }

    @PostMapping("sed/add")
    fun addInstutionAndDocument(@RequestBody request: ApiRequest): DocumentsItem? {
        return euxPrefillService.addInstutionAndDocument(request)
    }

    @PostMapping("sed/replysed/{parentid}")
    fun addDocumentToParent(
        @RequestBody(required = true) request: ApiRequest,
        @PathVariable("parentid", required = true) parentId: String
    ): DocumentsItem? {
        if (request.buc == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler Buc")

        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId) ?: throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Mangler norsk fnr")
        val avdodaktoerID = innhentingService.getAvdodId(BucType.from(request.buc.name)!!, request.riktigAvdod())
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, PersonInfo(norskIdent.id, request.aktoerId), avdodaktoerID)

        //Hente metadata for valgt BUC
        val bucUtil = addDocumentToParentBucUtils.measure {
            logger.info("******* Hent BUC sjekk om svarSed kan opprettes *******")
            BucUtils(euxInnhentingService.getBuc(dataModel.euxCaseID)).also { bucUtil ->
                //sjekk om en svarsed kan opprettes eller om den alt finnes
                bucUtil.isChildDocumentByParentIdBeCreated(parentId, dataModel.sedType)
            }
        }

        logger.info("Prøver å prefillSED (svarSED) parentId: $parentId")
        val sed = innhentingService.hentPreutyltSed(
            euxInnhentingService.checkForX010AndAddX009(request, parentId),
            bucUtil.getProcessDefinitionVersion()
        )

        return addDocumentToParent.measure {
            logger.info("Prøver å sende SED: ${dataModel.sedType} inn på BUC: ${dataModel.euxCaseID}")

            val docresult = euxPrefillService.opprettSvarJsonSedOnBuc(
                sed,
                dataModel.euxCaseID,
                parentId,
                request.vedtakId,
                dataModel.sedType
            )
            if (request.gjenny) {
                gcpStorageService.lagreGjennySak(request.euxCaseId!!, GjennySak(request.sakId!!, request.sakType!!))
            }

            val parent = bucUtil.findDocument(parentId)
            val result = bucUtil.findDocument(docresult.documentId)

            val documentItem = getBucForPBuc06AndForEmptySed(dataModel.buc, bucUtil.getBuc().documents, docresult, result)

            logger.info("Buc: (${dataModel.euxCaseID}, hovedSED type: ${parent?.type}, docId: ${parent?.id}, svarSED type: ${documentItem?.type} docID: ${documentItem?.id}")
            logger.info("******* Legge til svarSED - slutt *******")
            documentItem
        }
    }

    private fun getBucForPBuc06AndForEmptySed(bucType: BucType, bucDocuments: List<DocumentsItem>?, bucSedResponse: BucSedResponse, orginal: DocumentsItem?): DocumentsItem? {
        logger.info("Henter BUC på nytt for buctype: $bucDocuments")
        Thread.sleep(900)
        return if (bucType == P_BUC_06 || orginal == null && bucDocuments.isNullOrEmpty()) {
            val innhentetBuc = euxInnhentingService.getBuc(bucSedResponse.caseId)
            BucUtils(innhentetBuc).findDocument(bucSedResponse.documentId)
        } else {
            orginal

        }
    }

}
