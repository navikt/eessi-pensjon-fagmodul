package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.klient.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.ActionOperation
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandController
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.shared.api.PersonInfo
import no.nav.eessi.pensjon.shared.api.PrefillDataModel
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException

@Service
class EuxPrefillService (
    private val euxKlient: EuxKlientLib,
    private val statistikk: StatistikkHandler,
    private val euxInnhentingService: EuxInnhentingService,
    private val innhentingService: InnhentingService,
    private val gcpStorageService: GcpStorageService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
//    @Autowired
//    private lateinit var gcpStorage: Storage
    private val logger = LoggerFactory.getLogger(EuxPrefillService::class.java)

    private lateinit var addInstution: MetricsHelper.Metric
    private lateinit var addInstutionAndDocument: MetricsHelper.Metric
    private lateinit var opprettSvarSED: MetricsHelper.Metric
    private lateinit var opprettSED: MetricsHelper.Metric
    private lateinit var putMottaker: MetricsHelper.Metric
    private lateinit var getBUC: MetricsHelper.Metric
    init {
        addInstutionAndDocument = metricsHelper.init("AddInstutionAndDocument", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstution = metricsHelper.init("AddInstution", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        opprettSvarSED = metricsHelper.init("OpprettSvarSED")
        opprettSED = metricsHelper.init("OpprettSED")
        putMottaker = metricsHelper.init("PutMottaker", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        getBUC = metricsHelper.init("GetBUC", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSvarJsonSedOnBuc(jsonSed: String, euxCaseId: String, parentDocumentId: String, vedtakId: String?, sedType: SedType): BucSedResponse {
        logger.info("Forsøker å opprette (svarsed) en $sedType på rinasakId: $euxCaseId")

        val bucSedResponse = opprettSvarSED.measure {
            euxKlient.opprettSvarSed(
                jsonSed,
                euxCaseId,
                parentDocumentId)
        }

        statistikk.produserSedOpprettetHendelse(euxCaseId, bucSedResponse.documentId, vedtakId, sedType)

        return bucSedResponse
    }

    /**
     * Ny SED på ekisterende type
     */
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettJsonSedOnBuc(jsonNavSED: String, sedType: SedType, euxCaseId: String, vedtakId: String?): BucSedResponse {
        logger.info("Forsøker å opprette en $sedType på rinasakId: $euxCaseId")

        val bucSedResponse = opprettSED.measure { euxKlient.opprettSed(jsonNavSED, euxCaseId) }

        statistikk.produserSedOpprettetHendelse(euxCaseId, bucSedResponse.documentId, vedtakId, sedType)
        return bucSedResponse
    }

    fun addInstitution(euxCaseID: String, nyeInstitusjoner: List<String>) {
        logger.info("Legger til Deltakere/Institusjon på vanlig måte, ny Buc")
        putMottaker.measure { euxKlient.putBucMottakere(euxCaseID, nyeInstitusjoner) }
    }

    fun createdBucForType(buctype: String): String {
        val euxCaseId = getBUC.measure { euxKlient.createBuc(buctype) }
        try {
            statistikk.produserBucOpprettetHendelse(euxCaseId, null)
        } catch (ex: Exception) {
            logger.warn("Feiler ved statistikk")
        }
        return euxCaseId
    }

    /**
     * Ved opprettelse av ny Buc, kan det legges til deltakere uten at man trenger å opprette en X005.
     * Dersom Bucen allerede eksisterer må det opprettes en X005 for å legge til ny institusjon.
     */
    //ny/nye deltakere så må det opprettes en X005 for den nye institusjonen.
    fun checkAndAddInstitution(dataModel: PrefillDataModel, bucUtil: BucUtils, x005Liste: List<X005>, nyeInstitusjoner: List<InstitusjonItem>) {
        val navCaseOwner = bucUtil.getCaseOwner()?.country == "NO"
        logger.debug(
            """
            Hvem er CaseOwner: ${bucUtil.getCaseOwner()?.toJson()} på buc: ${bucUtil.getProcessDefinitionName()}
            Hvem er Deltakere: ${bucUtil.getParticipants().filterNot { it.role == "CaseOwner" }.toJson()}
            x005liste: ${ x005Liste.mapNotNull { it.xnav?.sak }.map{ it.leggtilinstitusjon }.toList().toJson()}
            x005 i buc null: ${bucUtil.findFirstDocumentItemByType(SedType.X005) == null}
            """.trimIndent()
        )

            if (x005Liste.isEmpty()) {
                logger.debug("legger til nyeInstitusjoner på vanlig måte. (ny buc)")
                addInstitution(dataModel.euxCaseID, nyeInstitusjoner.map { it.institution })
            } else {
                //sjekk for CaseOwner
                nyeInstitusjoner.forEach {
                    if (!navCaseOwner && it.country != "NO") {
                        logger.error("NAV er ikke sakseier. Du kan ikke legge til deltakere utenfor Norge")
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "NAV er ikke sakseier. Du kan ikke legge til deltakere utenfor Norge")
                    }
                }
                addInstitutionMedX005(dataModel, bucUtil.getProcessDefinitionVersion(), x005Liste)
            }
    }

    private fun addInstitutionMedX005(
        dataModel: PrefillDataModel,
        bucVersion: String,
        x005Liste: List<SED>
    ) {

        logger.info("X005 finnes på BUC, Sed X005 prefills og sendes inn.")
        var execptionError: Exception? = null

        x005Liste.forEach { x005 ->
            try {
                updateSEDVersion(x005, bucVersion)
                opprettJsonSedOnBuc(x005.toJson(), x005.type, dataModel.euxCaseID, dataModel.vedtakId)
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

    //flyttes til prefill / en eller annen service?
    fun updateSEDVersion(sed: SED, bucVersion: String) {
        when (bucVersion) {
            "v4.2" -> {
                sed.sedVer = "2"
            }
            else -> {
                sed.sedVer = "1"
            }
        }
    }

    fun addInstutionAndDocument(request: ApiRequest) : DocumentsItem? {
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
            request.euxCaseId?.let {gcpStorageService.lagreGjennySak(request.euxCaseId, GjennySak(request.sakId!!, request.sakType!!)) }
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
        ).also { logger.debug("Prefill av SED: $it") }

        //Lagrer P6000 detaljer til GCP Storage
        request.payload?.let { mapJsonToAny<List<P6000Dokument>>(it) }?.let { listeOverP6000 ->
            gcpStorageService.lagretilBackend(
                PensjonsinformasjonUtlandController.P6000Detaljer(
                    request.sakId!!,
                    request.euxCaseId!!,
                    listeOverP6000.map { it.documentID }).toJson(), request.sakId
            )
        }

        //val institusjonerFraRequest = request.institutions
        //Sjekk og opprette deltaker og legge sed på valgt BUC
        return addInstutionAndDocument.measure {
            logger.info("******* Legge til ny SED - start *******")

            val sedType = SedType.from(request.sed?.name!!)!!
            logger.info("Prøver å sende SED: $sedType inn på BUC: ${dataModel.euxCaseID}")

            val bucAndSedResponse = opprettJsonSedOnBuc(sed, sedType, dataModel.euxCaseID, request.vedtakId)

            logger.info("Opprettet ny SED med dokumentId: ${bucAndSedResponse.documentId}")
            val sedDocument = bucUtil.findDocument(bucAndSedResponse.documentId)
            sedDocument?.message = dataModel.melding

            logger.info("Har documentItem ${sedDocument?.id}")

            val documentItem = getBucForPBuc06AndForEmptySed(dataModel.buc, bucUtil.getBuc().documents, bucAndSedResponse, sedDocument)
            logger.info("******* Legge til ny SED - slutt *******")
            documentItem
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
                    checkAndAddInstitution(dataModel, bucUtil, emptyList(), nyeInstitusjoner)
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
                    checkAndAddInstitution(dataModel, bucUtil, x005Liste, nyeInstitusjoner)
                } else if (!bucUtil.isValidSedtypeOperation(SedType.X005, ActionOperation.Create)) { /* nada */  }
            }
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
open class KanIkkeOppretteSedFeilmelding(message: String?) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)



data class BucOgDocumentAvdod(
    val rinaidAvdod: String,
    val buc: Buc,
    var dokumentJson: String = ""
)