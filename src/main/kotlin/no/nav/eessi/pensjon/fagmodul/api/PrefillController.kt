package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.klient.BucSedResponse
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.ActionOperation
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.sed.X005
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
import no.nav.eessi.pensjon.shared.api.PrefillDataModel
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
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

    private lateinit var addInstution: MetricsHelper.Metric
    private lateinit var addInstutionAndDocument: MetricsHelper.Metric
    private lateinit var addDocumentToParent: MetricsHelper.Metric
    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric
    private lateinit var addDocumentToParentBucUtils: MetricsHelper.Metric
    init {
        addInstution = metricsHelper.init("AddInstution", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocument = metricsHelper.init("AddInstutionAndDocument", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
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
            gcpStorageService.lagre(it.caseId, GjennySak(gjennySak?.sakId!!, gjennySak.sakType))
        }
    }

    private fun addInstitution(request: ApiRequest, dataModel: PrefillDataModel, bucUtil: BucUtils) {
        addInstution.measure {
            logger.info("*** Sjekker og legger til Instiusjoner på BUC eller X005 ***")
            val nyeInstitusjoner = bucUtil.findNewParticipants(dataModel.getInstitutionsList())
            val x005docs = bucUtil.findX005DocumentByTypeAndStatus()

            if (nyeInstitusjoner.isNotEmpty()) {
                logger.info("""
                    eksiterendeInstiusjoner: ${bucUtil.getParticipantsAsInstitusjonItem().toJson()}
                    nyeInstitusjoner: ${nyeInstitusjoner.toJson()}
                """.trimIndent())

                if (x005docs.isEmpty()) {
                    euxPrefillService.checkAndAddInstitution(dataModel, bucUtil, emptyList(), nyeInstitusjoner)
                } else if (x005docs.firstOrNull { it.status == "empty"} != null ) {
                    val x005Liste = nyeInstitusjoner.map { nyeInstitusjonerMap ->
                        logger.debug("Prefiller X005, legger til Institusjon på X005 ${nyeInstitusjonerMap.institution}")
                        // ID og Navn på X005 er påkrevd må hente innn navn fra UI.
                        val x005request = request.copy(avdodfnr = null, sed = SedType.X005, institutions = listOf(nyeInstitusjonerMap))
                        mapJsonToAny<X005>(innhentingService.hentPreutyltSed(
                            x005request,
                            bucUtil.getProcessDefinitionVersion()
                        ))
                    }
                    euxPrefillService.checkAndAddInstitution(dataModel, bucUtil, x005Liste, nyeInstitusjoner)
                } else if (!bucUtil.isValidSedtypeOperation(SedType.X005, ActionOperation.Create)) { /* nada */  }
            }
        }
    }

    @PostMapping("sed/add")
    fun addInstutionAndDocument(@RequestBody request: ApiRequest): DocumentsItem? {

        logger.info("Avdød fnr finnes i requesten: ${request.subject?.avdod?.fnr != null}, Subject finnes: ${request.subject != null}, gjenlevende finnes: ${request.subject?.gjenlevende != null}")
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

        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId) ?: throw HttpClientErrorException(HttpStatus.BAD_REQUEST)
        val avdodaktoerID = innhentingService.getAvdodId(BucType.from(request.buc.name)!!, request.riktigAvdod())
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, PersonInfo(norskIdent.id, request.aktoerId), avdodaktoerID)

        //Hente metadata for valgt BUC
        val bucUtil = euxInnhentingService.kanSedOpprettes(dataModel)

        if (bucUtil.getProcessDefinitionName() != request.buc.name) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Rina Buctype og request buctype må være samme")
        }

        if (request.gjenny){
            request.euxCaseId?.let {gcpStorageService.lagre(request.euxCaseId, GjennySak(request.sakId!!, request.sakType!!)) }
        }

        logger.debug("bucUtil BucType: ${bucUtil.getBuc().processDefinitionName} apiRequest Buc: ${request.buc}")

        //AddInstitution
        addInstitution(request, dataModel, bucUtil)

        //Preutfyll av SED, pensjon og personer samt oppdatering av versjon
        //sjekk på P7000-- hente nødvendige P6000 sed fra eux.. legg til på request->prefilll
        val requestMedGjenlevendeFnr = request.copy(fnr = norskIdent.id)
        logger.debug("***Request med gjenlevende fnr: ${requestMedGjenlevendeFnr.toJson()} ***")
        val sed = innhentingService.hentPreutyltSed(
            euxInnhentingService.checkForP7000AndAddP6000(requestMedGjenlevendeFnr),
            bucUtil.getProcessDefinitionVersion()
        )

        //val institusjonerFraRequest = request.institutions
        //Sjekk og opprette deltaker og legge sed på valgt BUC
        return addInstutionAndDocument.measure {
            logger.info("******* Legge til ny SED - start *******")

            val sedType = SedType.from(request.sed?.name!!)!!
            logger.info("Prøver å sende SED: $sedType inn på BUC: ${dataModel.euxCaseID}")

            val bucAndSedResponse = euxPrefillService.opprettJsonSedOnBuc(sed, sedType, dataModel.euxCaseID, request.vedtakId)

            logger.info("Opprettet ny SED med dokumentId: ${bucAndSedResponse.documentId}")
            val sedDocument = bucUtil.findDocument(bucAndSedResponse.documentId)
            sedDocument?.message = dataModel.melding

            logger.info("Har documentItem ${sedDocument?.id}")

            val documentItem = getBucForPBuc06AndForEmptySed(dataModel.buc, bucUtil.getBuc().documents, bucAndSedResponse, sedDocument)
            logger.info("******* Legge til ny SED - slutt *******")
            documentItem
        }

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
                //sjekk for om deltakere alt er fjernet med x007 eller x100 sed
//                bucUtil.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(dataModel.getInstitutionsList())
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
                gcpStorageService.lagre(request.euxCaseId!!, GjennySak(request.sakId!!, request.sakType!!))
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
