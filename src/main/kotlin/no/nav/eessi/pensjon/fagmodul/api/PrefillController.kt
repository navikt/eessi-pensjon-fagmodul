package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.fagmodul.eux.*
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ActionOperation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
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
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct

@Protected
@RestController
class PrefillController(
    private val euxPrefillService: EuxPrefillService,
    private val euxInnhentingService: EuxInnhentingService,
    private val innhentingService: InnhentingService,
    private val auditlogger: AuditLogger,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(PrefillController::class.java)

    private lateinit var addInstution: MetricsHelper.Metric
    private lateinit var addInstutionAndDocument: MetricsHelper.Metric
    private lateinit var addDocumentToParent: MetricsHelper.Metric
    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric
    private lateinit var addDocumentToParentBucUtils: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
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
        val euxCaseId = euxPrefillService.createBuc(buctype)
        logger.info("Mottatt følgende euxCaseId(RinaID): $euxCaseId")

        //create bucDetail back from newly created buc call eux-rina-api to get data.
        val buc = euxInnhentingService.getBuc(euxCaseId)

        logger.info("Følgende bucdetalj er hentet: ${buc.processDefinitionName}, id: ${buc.id}")

        return BucAndSedView.from(buc)
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
                    //hvis finnes som draft.. kaste bad request til sb..

                    val x005Liste = nyeInstitusjoner.map {
                        logger.debug("Prefiller X005, legger til Institusjon på X005 ${it.institution}")
                        // ID og Navn på X005 er påkrevd må hente innn navn fra UI.
                        val x005request = request.copy(avdodfnr = null, sed = SedType.X005, institutions = listOf(it))
                        mapJsonToAny<X005>(innhentingService.hentPreutyltSed(x005request))
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
                "aktoerId: ${request.aktoerId} " +
                "sakId: ${request.sakId} " +
                "vedtak: ${request.vedtakId}"
        )

        if (request.buc == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler Buc")

        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId)
        val avdodaktoerID = innhentingService.getAvdodId(BucType.from(request.buc.name)!!, request.riktigAvdod())
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, avdodaktoerID)

        //Hente metadata for valgt BUC
        val bucUtil = euxInnhentingService.kanSedOpprettes(dataModel)

        if (bucUtil.getProcessDefinitionName() != request.buc.name) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Rina Buctype og request buctype må være samme")
        }

        logger.debug("bucUtil BucType: ${bucUtil.getBuc().processDefinitionName} apiRequest Buc: ${request.buc}")

        //AddInstitution
        addInstitution(request, dataModel, bucUtil)

        //Preutfyll av SED, pensjon og personer samt oppdatering av versjon
        //sjekk på P7000-- hente nødvendige P6000 sed fra eux.. legg til på request->prefilll
        val sed = innhentingService.hentPreutyltSed(euxInnhentingService.checkForP7000AndAddP6000(request))

        //Sjekk og opprette deltaker og legge sed på valgt BUC
        return addInstutionAndDocument.measure {
            logger.info("******* Legge til ny SED - start *******")

            val sedType = SedType.from(request.sed?.name!!)!!
            logger.info("Prøver å sende SED: $sedType inn på BUC: ${dataModel.euxCaseID}")
            val docresult = euxPrefillService.opprettJsonSedOnBuc(sed, sedType, dataModel.euxCaseID, request.vedtakId)

            logger.info("Opprettet ny SED med dokumentId: ${docresult.documentId}")
            val result = bucUtil.findDocument(docresult.documentId)
            result?.message = dataModel.melding

            logger.info("Har docuemntItem ${result?.id}, er Rina2020 ny buc: ${bucUtil.isNewRina2020Buc()}")

            val documentItem = fetchBucAgainBeforeReturnShortDocument(dataModel.buc, docresult, result, bucUtil.isNewRina2020Buc())
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

        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId)
        val avdodaktoerID = innhentingService.getAvdodId(BucType.from(request.buc.name)!!, request.riktigAvdod())
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, norskIdent, avdodaktoerID)

        //Hente metadata for valgt BUC
        val bucUtil = addDocumentToParentBucUtils.measure {
            logger.info("******* Hent BUC sjekk om svarSed kan opprettes *******")
            BucUtils(euxInnhentingService.getBuc(dataModel.euxCaseID)).also { bucUtil ->
                //sjekk for om deltakere alt er fjernet med x007 eller x100 sed
                bucUtil.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(dataModel.getInstitutionsList())
                //sjekk om en svarsed kan opprettes eller om den alt finnes
                bucUtil.isChildDocumentByParentIdBeCreated(parentId, dataModel.sedType)
            }
        }

        logger.info("Prøver å prefillSED (svarSED) parentId: $parentId")
        val sed = innhentingService.hentPreutyltSed(euxInnhentingService.checkForX010AndAddX009(request, parentId))

        return addDocumentToParent.measure {
            logger.info("Prøver å sende SED: ${dataModel.sedType} inn på BUC: ${dataModel.euxCaseID}")

            val docresult = euxPrefillService.opprettSvarJsonSedOnBuc(
                sed,
                dataModel.euxCaseID,
                parentId,
                request.vedtakId,
                dataModel.sedType
            )

            val parent = bucUtil.findDocument(parentId)
            val result = bucUtil.findDocument(docresult.documentId)

            val documentItem = fetchBucAgainBeforeReturnShortDocument(dataModel.buc, docresult, result, bucUtil.isNewRina2020Buc())

            logger.info("Buc: (${dataModel.euxCaseID}, hovedSED type: ${parent?.type}, docId: ${parent?.id}, svarSED type: ${documentItem?.type} docID: ${documentItem?.id}")
            logger.info("******* Legge til svarSED - slutt *******")
            documentItem
        }
    }

    private fun fetchBucAgainBeforeReturnShortDocument(bucType: BucType, bucSedResponse: EuxKlient.BucSedResponse, orginal: DocumentsItem?, isNewRina2020: Boolean = false): DocumentsItem? {
        return if (bucType == P_BUC_06) {
            logger.info("Henter BUC på nytt for buctype: $bucType")
            Thread.sleep(900)
            val buc = euxInnhentingService.getBuc(bucSedResponse.caseId)
            val bucUtil = BucUtils(buc)
            val document = bucUtil.findDocument(bucSedResponse.documentId)
            document
        } else if (orginal == null && isNewRina2020) {
            logger.info("Henter BUC på nytt for buctype: $bucType")
            Thread.sleep(1000)
            val buc = euxInnhentingService.getBuc(bucSedResponse.caseId)
            val bucUtil = BucUtils(buc)
            bucUtil.findDocument(bucSedResponse.documentId)
        } else {
            orginal
        }
    }

}
