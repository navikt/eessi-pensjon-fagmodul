package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Protected
@RestController
class PrefillController(
    private val euxPrefillService: EuxPrefillService,
    private val euxInnhentingService: EuxInnhentingService,
    private val gcpStorageService: GcpStorageService,
    private val auditlogger: AuditLogger,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(PrefillController::class.java)

    private lateinit var addInstution: MetricsHelper.Metric
    private lateinit var addInstutionAndDocument: MetricsHelper.Metric

    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric
    private lateinit var addDocumentToParentBucUtils: MetricsHelper.Metric
    init {
        addInstution = metricsHelper.init("AddInstution", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocument = metricsHelper.init("AddInstutionAndDocument", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
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

//    fun createBuc(
//        buctype: String,
//        gjennySak: GjennySak? = null
//    ): BucAndSedView {
//        auditlogger.log("createBuc")
//        logger.info("Prøver å opprette en ny BUC $buctype i RINA med GjennySakId: ${gjennySak?.sakId} med saktype: ${gjennySak?.sakType}.")
//
//        return createBuc(buctype).also {
//            gcpStorageService.lagreGjennySak(it.caseId, GjennySak(gjennySak?.sakId!!, gjennySak.sakType))
//        }
//    }

    @PostMapping("sed/add")
    fun addInstutionAndDocument(@RequestBody request: ApiRequest): DocumentsItem? {
        return addInstutionAndDocument.measure { euxPrefillService.leggTilInstitusjon(request)}
    }

    @PostMapping("sed/replysed/{parentid}")
    fun addDocumentToParent(
        @RequestBody(required = true) request: ApiRequest,
        @PathVariable("parentid", required = true) parentId: String
    ): DocumentsItem? {
        return euxPrefillService.leggDokumentTilBuc(request, parentId)
    }
}
