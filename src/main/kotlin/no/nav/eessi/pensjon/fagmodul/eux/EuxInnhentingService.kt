package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.buc.MissingBuc
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.X009
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucView
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucViewKilde
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.PreviewPdf
import no.nav.eessi.pensjon.fagmodul.models.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.utils.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class EuxInnhentingService (@Value("\${ENV}") private val environment: String, @Qualifier("fagmodulEuxKlient") private val euxKlient: EuxKlient) {

    private val logger = LoggerFactory.getLogger(EuxInnhentingService::class.java)

    fun getBuc(euxCaseId: String): Buc {
        val body = euxKlient.getBucJsonAsNavIdent(euxCaseId)
        return mapJsonToAny(body, typeRefs())
    }

    //hent buc for Pesys/tjeneste kjør som systembruker
    fun getBucAsSystemuser(euxCaseId: String): Buc {
        val body = euxKlient.getBucJsonAsSystemuser(euxCaseId)
        logger.debug("mapper buc om til BUC objekt-model")
        return mapJsonToAny(body, typeRefs())
    }

    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJson(euxCaseId, documentId)
        return SED.fromJsonToConcrete(json)
    }

    //henter ut korrekt url til Rina fra eux-rina-api
    fun getRinaUrl() = euxKlient.getRinaUrl()

    fun getSedOnBucByDocumentIdAsSystemuser(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJsonAndAsSystemuser(euxCaseId, documentId)
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
        logger.debug("henter institustion for bucType: $bucType, land: $landkode")
        return euxKlient.getInstitutions(bucType, landkode)
    }

    /**
     * Returnerer en liste over rinasaker.
     * Metoden velger kun pensjonsbucer samt noen få utvalgte spesialbucer EP aksepterer for visning.
     * Filtrerer også vekk umigrerte og arkiverte saker
     */
    fun getFilteredRinasakerSaker(list: List<Rinasak>): List<Rinasak> {
        val gyldigeBucs = ValidBucAndSed.pensjonsBucer() + mutableListOf("H_BUC_07", "R_BUC_01", "R_BUC_02", "M_BUC_02", "M_BUC_03a", "M_BUC_03b")
        return list.asSequence()
                .filterNot { rinasak -> rinasak.status == "archived" }
                .filter { rinasak -> rinasak.processDefinitionId in gyldigeBucs }
                .sortedBy { rinasak -> rinasak.id }
                .filterNot { MissingBuc.checkForMissingBuc(it.id!!) }
                .toList()
                .also {
                    logger.info(" *** før: ${list.size} etter: ${it.size} *** FilteredArchivedaRinasakerSak")
                }
    }

    fun getBucDeltakere(euxCaseId: String): List<ParticipantsItem> {
        return euxKlient.getBucDeltakere(euxCaseId)
    }

    fun getPdfContents(euxCaseId: String, documentId: String): PreviewPdf {
        return euxKlient.getPdfJsonWithRest(euxCaseId, documentId)
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
        val sed = mapJsonToAny(sedjson, typeRefs<SED>())
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
                euxKlient.getSedOnBucByDocumentIdAsJson(docs.rinaidAvdod, it.id!!)
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


    fun getBucViewBruker(fnr: String, aktoerId: String, sakNr: String): List<BucView> {
        val start = System.currentTimeMillis()
        val rinaSakerMedFnr = euxKlient.getRinasaker(fnr, status = "\"open\"")

        val filteredRinaBruker = getFilteredRinasakerSaker(rinaSakerMedFnr)
        logger.info("rinaSaker total: ${filteredRinaBruker.size}")

        return filteredRinaBruker.map { rinasak ->
            val buc = getBuc(rinasak.id!!)
            BucView(
                    rinasak.id,
                    BucType.from(rinasak.processDefinitionId)!!,
                    aktoerId,
                    sakNr,
                    null,
                    BucViewKilde.BRUKER,
                    buc.internationalId
            )
        }.also {
            val end = System.currentTimeMillis()
            logger.info("BucViewBruker tid ${end-start} i ms")
        }
    }

    fun hentBucViews(aktoerId: String, pesysSaksnr: String, rinaSakIder: List<String>, rinaSakIdKilde: BucViewKilde): List<BucView> {
        val start = System.currentTimeMillis()

        val rinaSaker = rinaSakIder
            .map { id ->
                val buc = getBuc(id)
                Rinasak(id = buc.id, processDefinitionId = buc.processDefinitionName, traits = null, applicationRoleId = null, properties = null, status = "open", internationalId = buc.internationalId)
            }

        val filteredRinasak = getFilteredRinasakerSaker(rinaSaker)

        return filteredRinasak.map { rinasak ->
            BucView(
                    rinasak.id!!,
                    BucType.from(rinasak.processDefinitionId)!!,
                    aktoerId,
                    pesysSaksnr,
                    null,
                    rinaSakIdKilde,
                    rinasak.internationalId
            )
        }.also {
            val end = System.currentTimeMillis()
            logger.info("getBucViews tid: ${end-start} ms")
        }

    }


    fun getBucViewAvdod(avdodFnr: String, aktoerId: String, sakNr: String): List<BucView> {
        val start = System.currentTimeMillis()
        val validAvdodBucs = listOf("P_BUC_02", "P_BUC_05", "P_BUC_06", "P_BUC_10")

        val rinaSakerMedAvdodFnr =  euxKlient.getRinasaker(avdodFnr, status = "\"open\"")
            .filter { rinasak -> rinasak.processDefinitionId in validAvdodBucs }

        val filteredRinaIdAvdod = getFilteredRinasakerSaker(rinaSakerMedAvdodFnr)
        logger.info("rinaSaker avdod total: ${filteredRinaIdAvdod.size}")

        return filteredRinaIdAvdod.map { rinasak ->
            BucView(
                    rinasak.id!!,
                    BucType.from(rinasak.processDefinitionId)!!,
                    aktoerId,
                    sakNr,
                    avdodFnr,
                    BucViewKilde.AVDOD,
                    null
            )
        }.also {
            val end = System.currentTimeMillis()
            logger.info("AvdodViewBruker tid ${end-start} i ms")
        }
    }

    //** hente rinasaker fra RINA og SAF
    fun getRinasaker(fnr: String, rinaSakIderFraJoark: List<String>): List<Rinasak> {
        // Henter rina saker basert på fnr
        val rinaSakerMedFnr = euxKlient.getRinasaker(fnr, status = "\"open\"")
        logger.debug("hentet rinasaker fra eux-rina-api size: ${rinaSakerMedFnr.size}")

        // Filtrerer vekk saker som allerede er hentet som har fnr
        val rinaSakIderMedFnr = hentRinaSakIder(rinaSakerMedFnr)
        val rinaSakIderUtenFnr = rinaSakIderFraJoark.minus(rinaSakIderMedFnr)

        // Henter rina saker som ikke har fnr
        val rinaSakerUtenFnr = rinaSakIderUtenFnr
                .map { euxCaseId -> euxKlient.getRinasaker( euxCaseId =  euxCaseId, status = "\"open\"") }
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
        logger.info("Oppdaterer ekisternede sed på rina: $euxcaseid. docid: $documentid")
        return euxKlient.updateSedOnBuc(euxcaseid, documentid, sedPayload)
    }


    fun checkForP7000AndAddP6000(request: ApiRequest): ApiRequest {
        return if (request.sed == SedType.P7000.name) {
            //hente payload from ui
            val docitems = request.payload?.let { mapJsonToAny(it, typeRefs<List<P6000Dokument>>()) }
            logger.info("P6000 payload size: ${docitems?.size}")
            //hente p6000sed fra rina legge på ny payload til prefill
            val seds = docitems?.map { Pair<P6000Dokument, SED>(it, getSedOnBucByDocumentId(it.bucid, it.documentID)) }
            //ny json payload til prefull
            request.copy(payload = seds?.let { mapAnyToJson(it) })
        } else request
    }

    //TODO fjern env for q2 når dette funker..
    fun checkForX010AndAddX009(request: ApiRequest, parentId: String): ApiRequest {
        return if (environment == "q2" &&  request.sed == SedType.X010.name && request.euxCaseId != null) {
            logger.info("Legger ved X009 som payload for prefill X010")
            val x009 = getSedOnBucByDocumentId(request.euxCaseId, parentId) as X009
            request.copy(payload = x009.toJson())
        } else request
    }


}