package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.MissingBuc
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.X009
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.PreviewPdf
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonDetalj
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.shared.api.PrefillDataModel
import no.nav.eessi.pensjon.utils.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import jakarta.annotation.PostConstruct

@Service
class EuxInnhentingService (@Value("\${ENV}") private val environment: String,
                            @Qualifier("fagmodulEuxKlient") private val euxKlient: EuxKlient,
                            @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private lateinit var RinaUrl: MetricsHelper.Metric
    private lateinit var SEDByDocumentId: MetricsHelper.Metric
    private lateinit var GetBUC: MetricsHelper.Metric
    private lateinit var BUCDeltakere: MetricsHelper.Metric
    private lateinit var GetKodeverk: MetricsHelper.Metric
    private lateinit var Institusjoner: MetricsHelper.Metric
    private lateinit var CreateBUC: MetricsHelper.Metric
    private lateinit var HentRinasaker: MetricsHelper.Metric
    private lateinit var PutDocument: MetricsHelper.Metric
    private lateinit var PingEux: MetricsHelper.Metric
    @PostConstruct
    fun initMetrics(){
        RinaUrl = metricsHelper.init("RinaUrl")
        SEDByDocumentId = metricsHelper.init("SEDByDocumentId", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        GetBUC = metricsHelper.init("GetBUC", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        BUCDeltakere = metricsHelper.init("BUCDeltakere", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        Institusjoner = metricsHelper.init("Institusjoner", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
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

    fun getBuc(euxCaseId: String): Buc {
        val body = euxKlient.getBucJsonAsNavIdent(euxCaseId, GetBUC)
        return mapJsonToAny(body)
    }

    //hent buc for Pesys/tjeneste kjør som systembruker
    fun getBucAsSystemuser(euxCaseId: String): Buc {
        val body = euxKlient.getBucJsonAsSystemuser(euxCaseId, GetBUC)
        logger.debug("mapper buc om til BUC objekt-model")
        return mapJsonToAny(body)
    }

    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJson(euxCaseId, documentId, SEDByDocumentId)
        return SED.fromJsonToConcrete(json)
    }

    //henter ut korrekt url til Rina fra eux-rina-api
    fun getRinaUrl() = euxKlient.getRinaUrl(RinaUrl)

    fun getSedOnBucByDocumentIdAsSystemuser(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJsonAndAsSystemuser(euxCaseId, documentId, SEDByDocumentId)
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

    fun getInstitutions(bucType: String, landkode: String? = ""): List<InstitusjonItem> {
        logger.info("henter institustion for bucType: $bucType, land: $landkode")
        val detaljList: List<InstitusjonDetalj> =  euxKlient.getInstitutions(bucType, landkode, Institusjoner)

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
    fun erRelevantForVisningIEessiPensjon(rinasak: EuxKlient.Rinasak) =
        rinasak.status != "archived"
                && rinasak.processDefinitionId in relevanteBucTyperForVisningIEessiPensjon()
                && !MissingBuc.checkForMissingBuc(rinasak.id!!)

    private fun relevanteBucTyperForVisningIEessiPensjon() =
        ValidBucAndSed.pensjonsBucer() + mutableListOf("H_BUC_07", "R_BUC_01", "R_BUC_02", "M_BUC_02", "M_BUC_03a", "M_BUC_03b")

    fun getBucDeltakere(euxCaseId: String): List<ParticipantsItem> {
        return euxKlient.getBucDeltakere(euxCaseId, BUCDeltakere)
    }

    fun getPdfContents(euxCaseId: String, documentId: String): PreviewPdf {
        return euxKlient.getPdfJson(euxCaseId, documentId, GetBUC)
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
                euxKlient.getSedOnBucByDocumentIdAsJson(docs.rinaidAvdod, it.id!!, SEDByDocumentId)
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

        return euxKlient.getRinasaker(fnr = fnr, euxCaseId = null, metric = HentRinasaker)
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

        return euxKlient.getRinasaker(fnr = avdodFnr, euxCaseId = null, metric = HentRinasaker)
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

    //** hente rinasaker fra RINA og SAF
    fun getRinasaker(fnr: String, rinaSakIderFraJoark: List<String>): List<EuxKlient.Rinasak> {
        // Henter rina saker basert på fnr
        val rinaSakerMedFnr = euxKlient.getRinasaker(fnr = fnr, euxCaseId = null, metric = HentRinasaker)
        logger.debug("hentet rinasaker fra eux-rina-api size: ${rinaSakerMedFnr.size}")

        // Filtrerer vekk saker som allerede er hentet som har fnr
        val rinaSakIderMedFnr = hentRinaSakIder(rinaSakerMedFnr)
        val rinaSakIderUtenFnr = rinaSakIderFraJoark.minus(rinaSakIderMedFnr)

        // Henter rina saker som ikke har fnr
        val rinaSakerUtenFnr = rinaSakIderUtenFnr
                .map { euxCaseId -> euxKlient.getRinasaker(euxCaseId =  euxCaseId, metric = HentRinasaker) }
                .flatten()
                .distinctBy { it.id }
        logger.info("henter rinasaker ut i fra saf documentMetadata, antall: ${rinaSakerUtenFnr.size}")

        return rinaSakerMedFnr.plus(rinaSakerUtenFnr).also {
            logger.info("Totalt antall rinasaker å hente: ${it.size}")
        }
    }

    /**
     * Returnerer en distinct liste av rinaSakIDer
     *  @param rinaSaker liste av rinasaker fra EUX datamodellen
     */
    fun hentRinaSakIder(rinaSaker: List<EuxKlient.Rinasak>) = rinaSaker.map { it.id!! }

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
        return euxKlient.updateSedOnBuc(euxcaseid, documentid, sedPayload, PutDocument)
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