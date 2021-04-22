package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class EuxInnhentingService (@Qualifier("fagmodulEuxKlient") private val euxKlient: EuxKlient) {

    private val logger = LoggerFactory.getLogger(EuxPrefillService::class.java)

    private val validbucsed = ValidBucAndSed()

    // Vi trenger denne no arg konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(EuxKlient(RestTemplate(), RestTemplate()))

    fun getBuc(euxCaseId: String): Buc {
        val body = euxKlient.getBucJson(euxCaseId)
        logger.debug("mapper buc om til BUC objekt-model")
        return mapJsonToAny(body, typeRefs())
    }

    fun getBucAsSystemuser(euxCaseId: String): Buc {
        val body = euxKlient.getBucJsonAsSystemuser(euxCaseId)
        logger.debug("mapper buc om til BUC objekt-model")
        return mapJsonToAny(body, typeRefs())
    }

    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJson(euxCaseId, documentId)
        return mapToConcreteSedClass(json)
    }

    fun getSedOnBucByDocumentIdAsSystemuser(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJsonAndAsSystemuser(euxCaseId, documentId)
        return mapToConcreteSedClass(json)
    }

    fun mapToConcreteSedClass(sedJson: String): SED {
        val genericSed = SED.fromJson(sedJson)

        return when(genericSed.type) {
            SedType.P4000 -> mapJsonToAny(sedJson, typeRefs<P4000>())
            SedType.P5000 -> mapJsonToAny(sedJson, typeRefs<P5000>())
            SedType.P6000 -> mapJsonToAny(sedJson, typeRefs<P6000>())
            SedType.P7000 -> mapJsonToAny(sedJson, typeRefs<P7000>())
            SedType.P8000 -> mapJsonToAny(sedJson, typeRefs<P8000>())
            else -> genericSed
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
        val list = rinasaker
                .map { rinaid ->
                    try {
                        BucAndSedView.from(getBuc(rinaid))
                    } catch (ex: Exception) {
                        logger.error(ex.message, ex)
                        BucAndSedView.fromErr(ex.message)
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
     * filtert kun gyldige buc-type for visning, returnerer liste av rinaid
     */
    fun getFilteredArchivedaRinasaker(list: List<Rinasak>): List<String> {
        val gyldigBucs = mutableListOf("H_BUC_07", "R_BUC_01", "R_BUC_02", "M_BUC_02", "M_BUC_03a", "M_BUC_03b")
        gyldigBucs.addAll(validbucsed.initSedOnBuc().keys.map { it }.toList())
        return list.asSequence()
                .filterNot { rinasak -> rinasak.status == "archived" }
                .filter { rinasak -> gyldigBucs.contains(rinasak.processDefinitionId) }
                .sortedBy { rinasak -> rinasak.id }
                .map { rinasak -> rinasak.id!! }
                .toList()
    }

    fun getBucDeltakere(euxCaseId: String): List<ParticipantsItem> {
        return euxKlient.getBucDeltakere(euxCaseId)
    }

    fun getBucAndSedViewAvdod(gjenlevendeFnr: String, avdodFnr: String): List<BucAndSedView> {
        // Henter rina saker basert på gjenlevendes fnr
        val validAvdodBucs = listOf("P_BUC_02","P_BUC_05","P_BUC_06","P_BUC_10")
        val rinaSakerMedFnr = validAvdodBucs.map { euxKlient.getRinasaker(avdodFnr, null, it, "\"open\"") }
           .flatten()

        logger.info("rinaSaker total: ${rinaSakerMedFnr.size}")
        val filteredRinaIdAvdod = getFilteredArchivedaRinasaker(rinaSakerMedFnr)

        logger.debug("filterer ut rinasaker og får kun ider tilbake size: ${filteredRinaIdAvdod.size}")

        val bucdocumentidAvdod = hentBucOgDocumentIdAvdod(filteredRinaIdAvdod)

        val listeAvSedsPaaAvdod = hentDocumentJsonAvdod(bucdocumentidAvdod)

        val gyldigeBucs = filterGyldigBucGjenlevendeAvdod(listeAvSedsPaaAvdod, gjenlevendeFnr)

        val gjenlevendeBucAndSedView =  getBucAndSedViewWithBuc(gyldigeBucs, gjenlevendeFnr, avdodFnr)

        logger.debug("TotalRinasaker med avdod og gjenlevende(rina/saf): ${gjenlevendeBucAndSedView.size}")

        return gjenlevendeBucAndSedView
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
            val bucutil = BucUtils(docs.buc)
            val bucType = bucutil.getProcessDefinitionName()
            logger.info("henter documentid fra buc: ${docs.rinaidAvdod} bucType: $bucType")

            val shortDoc = when (bucType) {
                "P_BUC_02" -> bucutil.getDocumentByType(SedType.P2100)
                "P_BUC_10" -> bucutil.getDocumentByType(SedType.P15000)
                "P_BUC_05" -> bucutil.getDocumentByType(SedType.P8000)
                else -> bucutil.getDocumentByType(SedType.P6000)
            }
            val sedJson = shortDoc?.let {
                euxKlient.getSedOnBucByDocumentIdAsJson(docs.rinaidAvdod, it.id!!)
            }
            docs.dokumentJson = sedJson ?: ""
            docs
        }
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

    //** hente rinasaker fra RINA og SAF
    fun getRinasaker(fnr: String,
                     aktoerId: String,
                     rinaSakIderFraJoark: List<String>): List<Rinasak> {
        // Henter rina saker basert på fnr
        val rinaSakerMedFnr = euxKlient.getRinasaker(fnr)
        logger.debug("hentet rinasaker fra eux-rina-api size: ${rinaSakerMedFnr.size}")

        // Filtrerer vekk saker som allerede er hentet som har fnr
        val rinaSakIderMedFnr = hentRinaSakIder(rinaSakerMedFnr)
        val rinaSakIderUtenFnr = rinaSakIderFraJoark.minus(rinaSakIderMedFnr)

        // Henter rina saker som ikke har fnr
        val rinaSakerUtenFnr = rinaSakIderUtenFnr
                .map { euxCaseId ->
                    euxKlient.getRinasaker( euxCaseId =  euxCaseId ) }
                .flatten()
                .distinctBy { it.id }
        logger.debug("henter rinasaker ut i fra saf documentMetadata")

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
        logger.info("Oppdaterer ekisternede sed på rina: $euxcaseid")

        return euxKlient.updateSedOnBuc(euxcaseid, documentid, sedPayload)
    }

}