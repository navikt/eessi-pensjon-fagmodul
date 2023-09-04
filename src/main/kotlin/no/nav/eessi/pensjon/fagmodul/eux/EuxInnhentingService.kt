package no.nav.eessi.pensjon.fagmodul.eux

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.ForbiddenException
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.InstitusjonDetalj
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.*
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.X009
import no.nav.eessi.pensjon.fagmodul.config.INSTITUTION_CACHE
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.shared.api.PrefillDataModel
import no.nav.eessi.pensjon.utils.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.IOException

@Service
class EuxInnhentingService (@Value("\${ENV}") private val environment: String,
                            @Autowired private val euxKlient: EuxKlientAsSystemUser,
                            @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private lateinit var SEDByDocumentId: MetricsHelper.Metric
    private lateinit var BUCDeltakere: MetricsHelper.Metric
    private lateinit var GetKodeverk: MetricsHelper.Metric
    private lateinit var CreateBUC: MetricsHelper.Metric
    private lateinit var HentRinasaker: MetricsHelper.Metric
    private lateinit var PutDocument: MetricsHelper.Metric
    private lateinit var PingEux: MetricsHelper.Metric
    @PostConstruct
    fun initMetrics(){
        SEDByDocumentId = metricsHelper.init("SEDByDocumentId", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        BUCDeltakere = metricsHelper.init("BUCDeltakere", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        CreateBUC = metricsHelper.init("CreateBUC", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        HentRinasaker = metricsHelper.init("HentRinasaker", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        PutDocument = metricsHelper.init("PutDocument", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        PingEux = metricsHelper.init("PingEux")
        GetKodeverk = metricsHelper.init("GetKodeverk", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    companion object { // TODO - finn et bedre sted
        val bucTyperSomKanHaAvdod: List<BucType> = listOf(P_BUC_02, P_BUC_05, P_BUC_06, P_BUC_10)
    }

    private val logger = LoggerFactory.getLogger(EuxInnhentingService::class.java)

    @Retryable(
        exclude = [IOException::class],
        backoff = Backoff(delayExpression = "@euxKlientRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["euxKlientRetryLogger"]
    )
    fun getBuc(euxCaseId: String) = mapJsonToAny<Buc>(euxKlient.getBucJsonAsNavIdent(euxCaseId))

    //hent buc for Pesys/tjeneste kjør som systembruker
    fun getBucAsSystemuser(euxCaseId: String): Buc {
        val body =  euxKlient.getBucJsonAsSystemuser(euxCaseId)
        logger.debug("mapper buc om til BUC objekt-model")
        return mapJsonToAny(body)
    }

    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val json = SEDByDocumentId.measure { euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(euxCaseId, documentId, listOf(HttpStatus.PRECONDITION_FAILED))}
        return SED.fromJsonToConcrete(json)
    }

    /**
     * henter ut korrekt url til Rina fra eux-rina-api
     */
    @Retryable(
        exclude = [IOException::class],
        backoff = Backoff(delayExpression = "@euxKlientRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["euxKlientRetryLogger"]
    )
    fun getRinaUrl() = euxKlient.getRinaUrl()

    fun getSedOnBucByDocumentIdAsSystemuser(euxCaseId: String, documentId: String): SED {
        val json = SEDByDocumentId.measure { euxKlient.getSedOnBucByDocumentIdAsSystemuser(euxCaseId, documentId) }
        return try {
            SED.fromJsonToConcrete(json)
        } catch (ex: Exception) {
            logger.error("Feiler ved mapping av kravSED. Rina: $euxCaseId, documentid: $documentId")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Feiler ved mapping av kravSED. Rina: $euxCaseId, documentid: $documentId")
        }
    }

    fun getSingleBucAndSedView(euxCaseId: String): BucAndSedView {
        return try {
            BucAndSedView.from(getBuc(euxCaseId))
        } catch (ex: Exception) {
            logger.error("Feiler ved utlevering av enkel bucandsedview ${ex.message}", ex)
            BucAndSedView.fromErr(ex.message)
        }
    }

    fun getBucAndSedViewWithBuc(bucs: List<Buc>, gjenlevndeFnr: String, avdodFnr: String): List<BucAndSedView> {
        return bucs
                .map { buc ->
                    try {
                        BucAndSedView.from(buc, gjenlevndeFnr, avdodFnr)
                    } catch (ex: Exception) {
                        logger.error(ex.message, ex)
                        BucAndSedView.fromErr(ex.message)
                    }
                }
    }

    fun getBucAndSedView(rinasaker: List<String>): List<BucAndSedView> {
        val startTime = System.currentTimeMillis()
        val list = rinasaker.map { rinaid ->
                    try {
                        BucAndSedView.from(getBuc(rinaid))
                    } catch (ex: Exception) {
                        val errormsg = if (ex is ForbiddenException) {
                            "${HttpStatus.FORBIDDEN}. En eller flere i familierelasjon i saken har diskresjonskode.\nBare saksbehandlere med diskresjonstilganger kan se de aktuelle BUC og SED i EESSI-Pensjon eller i RINA."
                        } else {
                            ex.message
                        }
                        logger.error(ex.message, ex)
                        BucAndSedView.fromErr(errormsg)
                    }
                }
                .sortedByDescending { it.startDate }

        logger.debug(" tiden tok ${System.currentTimeMillis() - startTime} ms.")
        return list
    }


    @Cacheable(cacheNames = [INSTITUTION_CACHE], cacheManager = "fagmodulCacheManager")
    @Retryable(
        exclude = [IOException::class],
        backoff = Backoff(delayExpression = "@euxKlientRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["euxKlientRetryLogger"]
    )
    fun getInstitutions(bucType: String, landkode: String? = ""): List<InstitusjonItem> {
        logger.info("henter institustion for bucType: $bucType, land: $landkode")
        val detaljList: List<InstitusjonDetalj> = euxKlient.getInstitutions(bucType, landkode)

        val institusjonListe = detaljList.asSequence()
            .filter { institusjon ->
                institusjon.tilegnetBucs.any { tilegnetBucsItem ->
                    tilegnetBucsItem.institusjonsrolle == "CounterParty"
                            && tilegnetBucsItem.eessiklar
                            && tilegnetBucsItem.bucType == bucType
                }
            }
            .map { institusjon ->
                InstitusjonItem(institusjon.landkode, institusjon.id, institusjon.navn, institusjon.akronym)
            }
            .sortedBy { it.institution }
            .sortedBy { it.country }
            .toList()

        return institusjonListe
    }

    /**
     * Sjekker om rinasak er relevant for visning i EP
     */
    fun erRelevantForVisningIEessiPensjon(rinasak: Rinasak) =
        rinasak.status != "archived"
                && rinasak.processDefinitionId in relevanteBucTyperForVisningIEessiPensjon()
                && !MissingBuc.checkForMissingBuc(rinasak.id!!)

    private fun relevanteBucTyperForVisningIEessiPensjon() =
        ValidBucAndSed.pensjonsBucer() + mutableListOf("H_BUC_07", "R_BUC_01", "R_BUC_02", "M_BUC_02", "M_BUC_03a", "M_BUC_03b")

    fun getBucDeltakere(euxCaseId: String): List<Participant> {
        return BUCDeltakere.measure {  euxKlient.getBucDeltakere(euxCaseId) }
    }

    fun getPdfContents(euxCaseId: String, documentId: String): PreviewPdf {
        return euxKlient.getPdfJson(euxCaseId, documentId )
    }

    /**
     * filtere ut gyldig buc fra gjenlevende og avdød
     */
    fun filterGyldigBucGjenlevendeAvdod(listeAvSedsPaaAvdod: List<BucOgDocumentAvdod>, fnrGjenlevende: String): List<Buc> {
        return listeAvSedsPaaAvdod
                .filter { docs -> filterGjenlevende(docs, fnrGjenlevende) }
                .map { docs -> docs.buc }
                .sortedBy { it.id }
    }

    private fun filterGjenlevende(docs: BucOgDocumentAvdod, fnrGjenlevende: String): Boolean {
        val sedjson = docs.dokumentJson
        if (sedjson.isBlank()) return false
        val sed = mapJsonToAny<SED>(sedjson)
        return filterGjenlevendePinNode(sed, docs.rinaidAvdod) == fnrGjenlevende ||
                filterAnnenPersonPinNode(sed, docs.rinaidAvdod) == fnrGjenlevende
    }

    /**
     * Henter inn sed fra eux fra liste over sedid på avdod
     */
    fun hentDocumentJsonAvdod(bucdocumentidAvdod: List<BucOgDocumentAvdod>): List<BucOgDocumentAvdod> {
        return bucdocumentidAvdod.map { docs ->
            val bucutils = BucUtils(docs.buc)
            val bucType = bucutils.getProcessDefinitionName()
            logger.info("henter documentid fra buc: ${docs.rinaidAvdod} bucType: $bucType")

            val shortDoc: DocumentsItem? = when (bucType) {
                "P_BUC_02" -> bucutils.getDocumentByType(SedType.P2100)
                "P_BUC_10" -> bucutils.getDocumentByType(SedType.P15000)
                "P_BUC_05" -> {
                    val document = bucutils.getAllDocuments()
                        .filterNot { it.status in listOf("draft", "empty") }
                        .filter { it.type == SedType.P8000  }
                    val docout = document.firstOrNull { it.direction == "OUT" }
                    val docin = document.firstOrNull { it.direction == "IN" }
                    docout ?: docin
                }
                else -> korrektDokumentAvdodPbuc06(bucutils)
            }

            logger.debug("Henter sedJson fra document: ${shortDoc?.type}, ${shortDoc?.status}, ${shortDoc?.id}")
            val sedJson = shortDoc?.let {
                SEDByDocumentId.measure { euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(docs.rinaidAvdod, it.id!!) }
            }
            docs.dokumentJson = sedJson ?: ""
            docs
        }
    }

    private fun korrektDokumentAvdodPbuc06(bucUtils: BucUtils): DocumentsItem? {
        logger.debug("henter ut korrekte SED fra P_BUC_06. ${bucUtils.getBuc().documents?.toJsonSkipEmpty()}")

        return bucUtils.getAllDocuments()
            .filterNot { it.status in listOf("draft", "empty") }
            .filter { it.type in listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000) }
            .firstOrNull { it.status in listOf("received", "new", "sent") }
    }

    /**
     * Henter buc og sedid på p2100 på avdøds fnr
     */
    fun hentBucOgDocumentIdAvdod(filteredRinaIdAvdod: List<String>): List<BucOgDocumentAvdod> {
        return filteredRinaIdAvdod.map {
            rinaIdAvdod -> BucOgDocumentAvdod(rinaIdAvdod, getBuc(rinaIdAvdod))
        }
    }

    /**
     * json filter uthenting av pin på gjenlevende (p2100)
     */
    private fun filterGjenlevendePinNode(sed: SED, rinaidAvdod: String): String? {
        val gjenlevende = sed.pensjon?.gjenlevende?.person
        return filterPinGjenlevendePin(gjenlevende, sed.type, rinaidAvdod)
    }

    /**
     * json filter uthenting av pin på annen person (gjenlevende) (p8000)
     */
    private fun filterAnnenPersonPinNode(sed: SED, rinaidAvdod: String): String? {
        val annenperson = sed.nav?.annenperson?.person
        val rolle = annenperson?.rolle
        val type = sed.pensjon?.kravDato?.type
        return if (type == "02" || rolle == "01") {
            filterPinGjenlevendePin(annenperson, sed.type, rinaidAvdod)
        } else {
            null
        }
    }

    private fun filterPinGjenlevendePin(gjenlevende: Person?, sedType: SedType, rinaidAvdod: String): String? {
        val pin = gjenlevende?.pin?.firstOrNull { it.land == "NO" }
        return if (pin == null) {
            logger.warn("Ingen fnr funnet på gjenlevende. ${sedType}, rinaid: $rinaidAvdod")
            null
        } else {
            pin.identifikator
        }
    }


    fun hentBucViewBruker(fnr: String, aktoerId: String, pesysSaksnr: String): List<BucView> {
        val start = System.currentTimeMillis()

        return HentRinasaker.measure {  euxKlient.getRinasaker(fnr = fnr, euxCaseId = null)
            .filter { erRelevantForVisningIEessiPensjon(it) }
            .map { rinasak ->
                BucView(
                    rinasak.id!!,
                    BucType.from(rinasak.processDefinitionId)!!,
                    aktoerId,
                    pesysSaksnr,
                    null,
                    BucViewKilde.BRUKER
                )
            }.also {
                val end = System.currentTimeMillis()
                logger.info("hentBucViewBruker tid ${end - start} i ms")
            }
        }
    }

    fun hentBucViews(aktoerId: String, pesysSaksnr: String, rinaSakIder: List<String>, rinaSakIdKilde: BucViewKilde): List<BucView> {
        val start = System.currentTimeMillis()

        return rinaSakIder
            .map { getBuc(it) }
            .filter { it.processDefinitionName in relevanteBucTyperForVisningIEessiPensjon() }
            .filter { !MissingBuc.checkForMissingBuc(it.id!!) }
            .map { buc ->
                BucView(
                    buc.id!!,
                    BucType.from(buc.processDefinitionName)!!,
                    aktoerId,
                    pesysSaksnr,
                    null,
                    rinaSakIdKilde
                )
            }.also {
                val end = System.currentTimeMillis()
                logger.info("hentBucViews tid: ${end - start} ms")
            }
    }

    @Retryable(
        exclude = [IOException::class],
        backoff = Backoff(delayExpression = "@euxKlientRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["euxKlientRetryLogger"]
    )
    fun hentBucer(aktoerId: String, pesysSaksnr: String, rinaSakIder: List<String>): List<Buc> {
        val start = System.currentTimeMillis()

        return rinaSakIder
            .map { getBuc(it) }
            .filter { it.processDefinitionName in relevanteBucTyperForVisningIEessiPensjon() }
            .filter { !MissingBuc.checkForMissingBuc(it.id!!) }
            .also {
                val end = System.currentTimeMillis()
                logger.info("hentBucer tid: ${end - start} ms")
            }
    }


    fun hentBucViewAvdod(avdodFnr: String, aktoerId: String, pesysSaksnr: String): List<BucView> {
        val start = System.currentTimeMillis()

        return HentRinasaker.measure {
            euxKlient.getRinasaker(fnr = avdodFnr, euxCaseId = null)
                .filter { rinasak -> rinasak.processDefinitionId in bucTyperSomKanHaAvdod.map { it.name } }
                .filter { erRelevantForVisningIEessiPensjon(it) }
                .map { rinasak ->
                    BucView(
                        rinasak.id!!,
                        BucType.from(rinasak.processDefinitionId)!!,
                        aktoerId,
                        pesysSaksnr,
                        avdodFnr,
                        BucViewKilde.AVDOD
                    )
                }.also {
                    val end = System.currentTimeMillis()
                    logger.info("hentBucViewAvdod tid ${end - start} i ms")
                }
        }
    }

    //** hente rinasaker fra RINA og SAF
    @Retryable(
        exclude = [IOException::class],
        backoff = Backoff(delayExpression = "@euxKlientRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["euxKlientRetryLogger"]
    )
    fun getRinasaker(fnr: String, rinaSakIderFraJoark: List<String>): List<Rinasak> {

        val rinaSakerMedFnr = HentRinasaker.measure { euxKlient.getRinasaker(fnr = fnr, euxCaseId = null) }
        logger.debug("hentet rinasaker fra eux-rina-api size: ${rinaSakerMedFnr.size}")

        // Filtrerer vekk saker som allerede er hentet som har fnr
        val rinaSakIderMedFnr = hentRinaSakIder(rinaSakerMedFnr)
        val rinaSakIderUtenFnr = rinaSakIderFraJoark.minus(rinaSakIderMedFnr)

        // Henter rina saker som ikke har fnr
        val rinaSakerUtenFnr = HentRinasaker.measure {
            rinaSakIderUtenFnr
                .map { euxCaseId -> euxKlient.getRinasaker(euxCaseId = euxCaseId) }
                .flatten()
                .distinctBy { it.id }
        }
        logger.info("henter rinasaker ut i fra saf documentMetadata, antall: ${rinaSakerUtenFnr.size}")

        return rinaSakerMedFnr.plus(rinaSakerUtenFnr).also {
            logger.info("Totalt antall rinasaker å hente: ${it.size}")
        }
    }

    /**
     * Returnerer en distinct liste av rinaSakIDer
     *  @param rinaSaker liste av rinasaker fra EUX datamodellen
     */
    fun hentRinaSakIder(rinaSaker: List<Rinasak>) = rinaSaker.map { it.id!! }

    fun kanSedOpprettes(dataModel: PrefillDataModel): BucUtils {

        logger.info("******* Hent BUC sjekk om sed kan opprettes *******")
        return BucUtils(getBuc(dataModel.euxCaseID)).also { bucUtil ->
            //sjekk for om deltakere alt er fjernet med x007 eller x100 sed
            bucUtil.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(dataModel.getInstitutionsList())
            //gyldig sed kan opprettes
            bucUtil.checkIfSedCanBeCreated(dataModel.sedType, dataModel.penSaksnummer)
        }
    }

    fun updateSedOnBuc(euxcaseid: String, documentid: String, sedPayload: String): Boolean {
        logger.info("Oppdaterer eksisterende sed på rina: $euxcaseid. docid: $documentid")
        return PutDocument.measure { euxKlient.updateSedOnBuc(euxcaseid, documentid, sedPayload) }
    }


    fun checkForP7000AndAddP6000(request: ApiRequest): ApiRequest {
        return if (request.sed?.name == SedType.P7000.name) {
            //hente payload from ui
            val docitems = request.payload?.let { mapJsonToAny<List<P6000Dokument>>(it) }
            logger.info("P6000 payload size: ${docitems?.size}")
            //hente p6000sed fra rina legge på ny payload til prefill
            val seds = docitems?.map { Pair<P6000Dokument, SED>(it, getSedOnBucByDocumentId(it.bucid, it.documentID)) }
            //ny json payload til prefull
            request.copy(payload = seds?.let { mapAnyToJson(it) })
        } else request
    }

    //TODO fjern env for q2 når dette funker..
    fun checkForX010AndAddX009(request: ApiRequest, parentId: String): ApiRequest {
        return if (environment == "q2" &&  request.sed?.name == SedType.X010.name && request.euxCaseId != null) {
            logger.info("Legger ved X009 som payload for prefill X010")
            val x009 = getSedOnBucByDocumentId(request.euxCaseId, parentId) as X009
            request.copy(payload = x009.toJson())
        } else request
    }
    /**
     * Utvalgt informasjon om en rinasak/Buc.
     */
    data class BucView(
        val euxCaseId: String,
        val buctype: BucType?,
        val aktoerId: String,
        val saknr: String,
        val avdodFnr: String? = null,
        val kilde: BucViewKilde
    )

    enum class BucViewKilde{
        BRUKER,
        SAF,
        AVDOD;
    }
}

@Profile("!retryConfigOverride")
@Component
data class EuxKlientRetryConfig(val initialRetryMillis: Long = 20000L)

@Component
class EuxKlientRetryLogger : RetryListener {
    private val logger = LoggerFactory.getLogger(EuxKlientRetryLogger::class.java)
    override fun <T : Any?, E : Throwable?> onError(context: RetryContext?, callback: RetryCallback<T, E>?, throwable: Throwable?) {
        logger.warn("Feil under henting fra EUX - try #${context?.retryCount } - ${throwable?.toString()}", throwable)
    }
}
