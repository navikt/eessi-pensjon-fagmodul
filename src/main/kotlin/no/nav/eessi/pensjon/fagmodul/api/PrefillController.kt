package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.pesys.P6000Detaljer
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.PersonInfo
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException

@Protected
@RequestMapping("/prefill")
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
    private val secureLog = LoggerFactory.getLogger("secureLog")

    private  var addInstutionAndDocument: MetricsHelper.Metric
    private  var addDocumentToParent: MetricsHelper.Metric
    private  var addInstutionAndDocumentBucUtils: MetricsHelper.Metric
    private  var addDocumentToParentBucUtils: MetricsHelper.Metric
    init {
        addInstutionAndDocument = metricsHelper.init("AddInstutionAndDocument", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParent = metricsHelper.init("AddDocumentToParent", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocumentBucUtils = metricsHelper.init("AddInstutionAndDocumentBucUtils", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParentBucUtils = metricsHelper.init("AddDocumentToParentBucUtils", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
    }

    @PostMapping("/buc/{buctype}")
    fun createBuc(
        @PathVariable("buctype", required = true) buctype: String
    ): FrontEndResponse<BucAndSedView> {
        auditlogger.log("createBuc")
        logger.info("Prøver å opprette en ny BUC i RINA av type: $buctype")

        //rinaid
        val euxCaseId = euxPrefillService.createdBucForType(buctype)
        logger.info("Mottatt følgende euxCaseId(RinaID): $euxCaseId")

        //create bucDetail back from newly created buc call eux-rina-api to get data.
        val buc = euxInnhentingService.getBuc(euxCaseId)

        logger.info("Følgende bucdetalj er hentet: ${buc.processDefinitionName}, id: ${buc.id}")

        return FrontEndResponse(BucAndSedView.from(buc), HttpStatus.OK.name)
    }

    @PostMapping("/sed/add")
    fun addInstutionAndDocument(@RequestBody request: ApiRequest): FrontEndResponse<DocumentsItem?> {

        logger.info("Avdød fnr finnes i requesten: ${request.subject?.avdod?.fnr != null}, Subject finnes: ${request.subject != null}, gjenlevende finnes: ${request.subject?.gjenlevende != null}")
        secureLog.info("AvdodFnr: ${request.avdodfnr}, avdodFnrManuelt: ${request.avdodfnrManuelt}")
        logger.info("Legger til institusjoner og SED for " +
                "rinaId: ${request.euxCaseId} " +
                "bucType: ${request.buc} " +
                "sedType: ${request.sed} " +
                "aktoerId: ${request.aktoerId?.substring(0,5)} " +
                "sakId: ${request.sakId} " +
                "vedtak: ${request.vedtakId} " +
                "institusjoner: ${request.institutions} " +
                "gjenny: ${request.gjenny}"
        )
        if (request.buc == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler Buc")

        val (norskIdent, dataModel, bucUtil) = euxPrefillService.buildDataModelOgValider(request, false)

        logger.debug("bucUtil BucType: ${bucUtil.getBuc().processDefinitionName} apiRequest Buc: ${request.buc}")

        //AddInstitution
        euxPrefillService.addInstitution(request, dataModel, bucUtil)

        //Preutfyll av SED, pensjon og personer samt oppdatering av versjon
        //sjekk på P7000-- hente nødvendige P6000 sed fra eux.. legg til på request->prefilll
        val requestMedGjenlevendeFnr = request.copy(fnr = norskIdent.id)
        logger.debug("***Request med gjenlevende fnr: ${requestMedGjenlevendeFnr.toJson()} ***")
        val sed = innhentingService.hentPreutyltSed(
            euxInnhentingService.checkForP7000AndAddP6000(requestMedGjenlevendeFnr),
            bucUtil.getProcessDefinitionVersion()
        ).also { secureLog.info("Prefill av SED: $it") }

        //Lagrer P6000 detaljer til GCP Storage
        try {
            if(request.sed == SedType.P7000) {
                logger.info("Lagerer P6000: buc: ${request.buc}, rinaId: ${request.euxCaseId}, sakId: ${request.sakId}, euxCaseId: ${request.documentid}")
                request.payload?.let { mapJsonToAny<List<P6000Dokument>>(it) }?.let { listeOverP6000 ->
                    gcpStorageService.lagretilBackend(
                        P6000Detaljer(
                            request.sakId!!,
                            request.euxCaseId!!,
                            listeOverP6000.map { it.documentID }).toJson(), request.sakId
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
        }

        return addInstutionAndDocument.measure {
            FrontEndResponse(euxPrefillService.opprettSedOgHentDocumentItem(sed, request, dataModel, bucUtil), HttpStatus.OK.name)
        }
    }

    @PostMapping("/sed/replysed/{parentid}")
    fun addDocumentToParent(
        @RequestBody(required = true) request: ApiRequest,
        @PathVariable("parentid", required = true) parentId: String
    ): FrontEndResponse<DocumentsItem?> {
        if (request.buc == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler Buc")

        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId) ?: throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Mangler norsk fnr")
        val avdodaktoerID = innhentingService.getAvdodId(BucType.from(request.buc.name)!!, request.riktigAvdod(), false)
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

            if (request.gjenny) {
                gcpStorageService.lagreGjennySak(request.euxCaseId!!, GjennySak(request.sakId!!, request.sakType!!))
            }

            val parent = bucUtil.findDocument(parentId)
            val documentItem = euxPrefillService.opprettSvarSedOgHentDocumentItem(sed, dataModel, parentId, bucUtil)

            logger.info("Buc: (${dataModel.euxCaseID}, hovedSED type: ${parent?.type}, docId: ${parent?.id}, svarSED type: ${documentItem?.type} docID: ${documentItem?.id}")
            logger.info("******* Legge til svarSED - slutt *******")
            FrontEndResponse(documentItem, HttpStatus.OK.name)
        }
    }


}
