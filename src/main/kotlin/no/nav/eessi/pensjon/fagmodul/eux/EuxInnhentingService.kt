package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.klient.ForbiddenException
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.InstitusjonDetalj
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.buc.MissingBuc
import no.nav.eessi.pensjon.eux.model.buc.PreviewPdf
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.X009
import no.nav.eessi.pensjon.fagmodul.config.INSTITUTION_CACHE
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.shared.api.PrefillDataModel
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
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
class EuxInnhentingService(
    @Value("\${ENV}") private val environment: String,
    private val euxKlient: EuxKlientAsSystemUser,
    private val gcpService: GcpStorageService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private lateinit var sedByDocumentId: MetricsHelper.Metric
    private lateinit var bucDeltakere: MetricsHelper.Metric
    private lateinit var getKodeverk: MetricsHelper.Metric
    private lateinit var createBUC: MetricsHelper.Metric
    private lateinit var hentRinasaker: MetricsHelper.Metric
    private lateinit var putDocument: MetricsHelper.Metric
    private lateinit var pingEux: MetricsHelper.Metric

    init {
        sedByDocumentId = metricsHelper.init("SEDByDocumentId", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucDeltakere = metricsHelper.init("BUCDeltakere", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        createBUC = metricsHelper.init("CreateBUC", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        hentRinasaker = metricsHelper.init("HentRinasaker", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        putDocument = metricsHelper.init("PutDocument", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        pingEux = metricsHelper.init("PingEux")
        getKodeverk = metricsHelper.init("GetKodeverk", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
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
    fun getBuc(euxCaseId: String) = mapJsonToAny<Buc>(euxKlient.getBucJsonAsNavIdent(euxCaseId)!!)

    //hent buc for Pesys/tjeneste kjør som systembruker
    fun getBucAsSystemuser(euxCaseId: String): Buc? {
        val resultat = euxKlient.getBucJsonAsSystemuser(euxCaseId)

        if (resultat == null) {
            logger.error("Kunne ikke hente Buc for euxCaseId: $euxCaseId som systembruker")
            return null
        }
        logger.debug("Mapper Buc string til Buc modell")
        return mapJsonToAny(resultat)
    }

    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val json = sedByDocumentId.measure { euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(euxCaseId, documentId, listOf(HttpStatus.PRECONDITION_FAILED))}
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
        val json = sedByDocumentId.measure { euxKlient.getSedOnBucByDocumentIdAsSystemuser(euxCaseId, documentId) }
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
        ValidBucAndSed.pensjonsBucer() + mutableListOf(H_BUC_07.name, R_BUC_02.name, M_BUC_02.name, M_BUC_03a.name, M_BUC_03b.name)

    fun getPdfContents(euxCaseId: String, documentId: String): PreviewPdf {
        return euxKlient.getPdfJson(euxCaseId, documentId )
    }

    /**
     * filtere ut gyldig buc fra gjenlevende og avdød
     */
    fun filterGyldigBucGjenlevendeAvdod(listeAvSedsPaaAvdod: List<BucOgDocumentAvdod>, fnrGjenlevende: String): List<Buc> {
        return listeAvSedsPaaAvdod
                .map { docs -> docs.buc }
                .sortedBy { it.id }
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
                sedByDocumentId.measure { euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(docs.rinaidAvdod, it.id!!, listOf(HttpStatus.PRECONDITION_FAILED)) }
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

    private fun filterPinGjenlevendePin(gjenlevende: Person?, sedType: SedType, rinaidAvdod: String): String? {
        val pin = gjenlevende?.pin?.firstOrNull { it.land == "NO" }
        return if (pin == null) {
            logger.warn("Ingen fnr funnet på gjenlevende. ${sedType}, rinaid: $rinaidAvdod")
            null
        } else {
            pin.identifikator
        }
    }

    fun hentBucViewBruker(fnr: String, aktoerId: String, pesysSaksnr: String?): List<BucView> {
        val start = System.currentTimeMillis()

        return hentRinasaker.measure {  euxKlient.getRinasaker(fnr = fnr, euxCaseId = null)
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
    fun hentViewsForSafOgRinaForAvdode(
        avdodListe: List<String>,
        aktoerId: String,
        sakNr: String?,
        brukerIdFraJoark: List<String>
    ): List<BucView> {

        //api: hent avdod saker fra eux/rina
        val avdodView = avdodListe.map { avdod -> hentBucViewAvdod(avdod, aktoerId, sakNr) }.flatten()

        //filter: avdodview for match på filterBrukersakerRina
        val avdodViewSaf = avdodView
            .filter { view -> view.euxCaseId in brukerIdFraJoark }
            //TODO: Hvorfor er det nødvendig å sette kilde her?
            .map { view ->
                view.copy(kilde = BucViewKilde.SAF)
            }

        //filter: avdod saker view uten saf
        val avdodViewUtenSaf = avdodView.filterNot { view -> view.euxCaseId in avdodViewSaf.map { it.euxCaseId } }

        //filter: saker fra saf som kan hentes
        val filterAvodRinaSakIderFraJoark =
            brukerIdFraJoark.filterNot { rinaid -> rinaid in avdodView.map { it.euxCaseId } }

        //api: saker fra saf og eux/rina
        val safView = lagBucViews(
            aktoerId,
            sakNr,
            filterAvodRinaSakIderFraJoark,
            BucViewKilde.SAF
        )

        //filter: saf mot avdod
        val safViewAvdod = safView
            .filter { view -> view.buctype in bucTyperSomKanHaAvdod }
            .map { view -> view.copy(avdodFnr = avdodListe.firstOrNull()) }
            .also { if (avdodListe.size == 2) logger.warn("finnes 2 avdod men valgte første, ingen koblinger") }

        //filter: saf mot bruker
        val safViewBruker = safView
            .filterNot { view -> view.euxCaseId in safViewAvdod.map { it.euxCaseId } }

        val view = avdodViewSaf + avdodViewUtenSaf + safViewAvdod + safViewBruker

        logger.info(
            """hentViewsForSafOgRinaForAvdode resultat: 
                    safView: ${safView.size}
                    avdodView : ${avdodView.size}
                    safViewAvdod: ${safViewAvdod.size}
                    safViewBruker: ${safViewBruker.size}
                    avdodViewUtenSaf: ${avdodViewUtenSaf.size}
                    brukerRinaSakIderFraJoark: ${brukerIdFraJoark.size}
                    filterAvodRinaSakIderFraJoark: ${filterAvodRinaSakIderFraJoark.size}
                    totalview : ${view.size}
                """.trimMargin()
        )
        return view
    }

    fun hentBucerGjenny(fnr: String): List<Rinasak> {
        return euxKlient.getRinasaker(fnr)
    }

    fun lagBucViews(aktoerId: String, pesysSaksnr: String?, rinaSakIder: List<String>, rinaSakIdKilde: BucViewKilde): List<BucView> {
        val start = System.currentTimeMillis()

        return rinaSakIder
            .mapNotNull {
                try {
                    getBuc(it)
                } catch (e: Exception) {
                    logger.error("Henting av buc for:$it feiler med melding: ${e.message}")
                    null
                }
            }
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


    fun hentBucViewAvdod(avdodFnr: String, aktoerId: String, pesysSaksnr: String? = null): List<BucView> {
        val start = System.currentTimeMillis()

        return hentRinasaker.measure {
            euxKlient.getRinasaker(fnr = avdodFnr, euxCaseId = null)
                .also { logger.info("hentBucViewAvdod, rinasaker for $aktoerId, size: ${it.size}") }
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

        val rinaSakerMedFnr = hentRinasaker.measure { euxKlient.getRinasaker(fnr = fnr, euxCaseId = null) }
        logger.debug("hentet rinasaker fra eux-rina-api size: ${rinaSakerMedFnr.size}")

        // Filtrerer vekk saker som allerede er hentet som har fnr
        val rinaSakIderMedFnr = hentRinaSakIder(rinaSakerMedFnr)
        val rinaSakIderUtenFnr = rinaSakIderFraJoark.minus(rinaSakIderMedFnr)

        // Henter rina saker som ikke har fnr
        val rinaSakerUtenFnr = hentRinasaker.measure {
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
            //gyldig sed kan opprettes
            bucUtil.checkIfSedCanBeCreated(dataModel.sedType, dataModel.penSaksnummer)
        }
    }

    fun updateSedOnBuc(euxcaseid: String, documentid: String, sedPayload: String): Boolean {
        logger.info("Oppdaterer eksisterende sed på rina: $euxcaseid. docid: $documentid")
        return putDocument.measure { euxKlient.updateSedOnBuc(euxcaseid, documentid, sedPayload) }
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

    fun sendSed(rinaSakId: String, dokumentId: String): Boolean {
        logger.info("Sender sed til Rina: $rinaSakId, sedId: $dokumentId")
        return euxKlient.sendSed(rinaSakId, dokumentId)
    }

    fun sendSedTilMottakere(rinaSakId: String, dokumentId: String, mottakere: List<String>): Boolean {
        logger.info("Sender sed til Rina for mottakere: $rinaSakId, sedId: $dokumentId, mottakere: $mottakere")
        return euxKlient.sendTo(rinaSakId, dokumentId, mottakere)
    }

    fun reSendRinasaker(dokumentListe: String): EuxKlientLib.HentResponseBody? {
        logger.info("Resender seder til Rina")
        return euxKlient.resend(dokumentListe)
    }

    fun reSendeRinasakerMedRinaId(rinasakId: String, dokumentId: String):  EuxKlientLib.HentResponseBody? {
        logger.info("Resender seder til Rina")
        return euxKlient.resendeDokMedrinaId(rinasakId, dokumentId)
    }

    fun lagPdf(pdfJson: String): PreviewPdf? {
        logger.info("Lager pdf fra json")
        return euxKlient.lagPdf(pdfJson)
    }

    /**
     * Utvalgt informasjon om en rinasak/Buc.
     */
    data class BucView(
        val euxCaseId: String,
        val buctype: BucType?,
        val aktoerId: String,
        val saknr: String? = null,
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
