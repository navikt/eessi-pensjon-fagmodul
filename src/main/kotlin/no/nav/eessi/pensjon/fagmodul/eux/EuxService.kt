package no.nav.eessi.pensjon.fagmodul.eux

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.annotations.VisibleForTesting
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.client.SafClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import javax.annotation.PostConstruct

@Service
class EuxService (private val euxKlient: EuxKlient,
                  private val safClient: SafClient,
                  private val statistikk: StatistikkHandler,
                  @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(EuxService::class.java)

    // Vi trenger denne no arg konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(EuxKlient(RestTemplate()),
        SafClient(RestTemplate(), RestTemplate()),
        StatistikkHandler("Q2", KafkaTemplate(DefaultKafkaProducerFactory(emptyMap())), ""))

    private lateinit var opprettSvarSED: MetricsHelper.Metric
    private lateinit var opprettSED: MetricsHelper.Metric
    private val validbucsed = ValidBucAndSed()

    @PostConstruct
    fun initMetrics() {
        opprettSvarSED = metricsHelper.init("OpprettSvarSED")
        opprettSED = metricsHelper.init("OpprettSED")
    }

    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSvarJsonSedOnBuc(jsonSed: String, euxCaseId: String, parentDocumentId: String, vedtakId: String?): BucSedResponse {
        val bucSedResponse = euxKlient.opprettSvarSed(
            jsonSed,
            euxCaseId,
            parentDocumentId,
            "Feil ved opprettSvarSed", opprettSvarSED
        )

        statistikk.produserSedOpprettetHendelse(euxCaseId, bucSedResponse.documentId, vedtakId)
        return bucSedResponse
    }

    /**
     * Ny SED på ekisterende type
     */
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettJsonSedOnBuc(jsonNavSED: String, sedType: SEDType, euxCaseId: String, vedtakId: String?): BucSedResponse {
        logger.info("Forsøker å opprette en $sedType på rinasakId: $euxCaseId")
        logger.debug("Logger ut $jsonNavSED")
        val bucSedResponse  = euxKlient.opprettSed(jsonNavSED, euxCaseId, opprettSED, "Feil ved opprettSed: $sedType, med rinaId: $euxCaseId")
        statistikk.produserSedOpprettetHendelse(euxCaseId, bucSedResponse.documentId, vedtakId)

        return bucSedResponse
    }


    /**
     * Henter ut sed fra rina med bucid og documentid
     */
    fun getSedOnBuc(euxCaseId: String, sedType: SEDType?): List<SED> {
        logger.info("Prøver å hente ut en BucUtils for type $euxCaseId")
        val docid = getBuc(euxCaseId).documents ?: throw NoSuchFieldException("Fant ikke DocumentsItem")

        if (sedType == null) return emptyList()

        val sedlist = docid.filter { it.type == sedType }
                .mapNotNull { it.id?.let { id ->  getSedOnBucByDocumentId(euxCaseId, id) } }

        logger.info("return liste av SED for type: $sedType listSize: ${sedlist.size}")
        return sedlist
    }

    fun getBuc(euxCaseId: String): Buc {
        val body = euxKlient.getBucJson(euxCaseId)
        logger.debug("mapper buc om til BUC objekt-model")
        val buc: Buc = mapJsonToAny(body, typeRefs())
        return buc.also { logBucContent(it) }
    }

    private fun logBucContent(buc: Buc) {
        val aksjoner = buc.actions?.map {
            mapOf(
                    "displayName" to it.displayName,
                    "documentType" to it.documentType,
                    "name" to it.name,
                    "operation" to it.operation)
        }
        val dokumenter = buc.documents?.map {
            mapOf(
                    "type" to it.type,
                    "typeVersion" to it.typeVersion,
                    "status" to it.status,
                    "direction" to it.direction,
                    "isSendExecuted" to it.isSendExecuted.toString()
            )
        }
        logger.info("Buc-inneholder: " +
                "[{\"actions\": ${aksjoner?.let { jacksonObjectMapper().writeValueAsString(it) }} }, " +
                "{\"documents\": ${dokumenter?.let { jacksonObjectMapper().writeValueAsString(it) }}}]")
    }

    @Throws(EuxServerException::class, SedDokumentIkkeLestException::class)
    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJson(euxCaseId, documentId)
        return SED.fromJson(json)
    }

    /**
     * Henter ut Kravtype og Fnr fra P2100 og P15000
     * TODO hvor brukes denne nå?
     */
    fun hentFnrOgYtelseKravtype(euxCaseId: String, documentId: String): PinOgKrav {
        val sed = getSedOnBucByDocumentId(euxCaseId, documentId)

        //validere om SED er virkelig en P2100 eller P15000
        if (SEDType.P2100 == sed.type) {
            return PinOgKrav(
                    fnr = getFnrMedLandkodeNO(sed.pensjon?.gjenlevende?.person?.pin),
                    krav = sed.nav?.krav ?: Krav()
            )
        }
        //P15000 sjekke om det er 02 Gjenlevende eller ikke
        if (SEDType.P15000 == sed.type) {
            val krav = sed.nav?.krav ?: Krav()
            return if ("02" == krav.type) {
                PinOgKrav(
                        fnr = getFnrMedLandkodeNO(sed.pensjon?.gjenlevende?.person?.pin),
                        krav = krav
                )
            } else {
                PinOgKrav(
                        fnr = getFnrMedLandkodeNO(sed.nav?.bruker?.person?.pin),
                        krav = sed.nav?.krav ?: Krav()
                )
            }
        }
        throw SedDokumentIkkeGyldigException("SED gyldig SED av type P2100 eller P15000")
    }

    fun getFnrMedLandkodeNO(pinlist: List<PinItem>?): String? =
            pinlist?.firstOrNull { it.land == "NO" }?.identifikator

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

    fun addInstitution(euxCaseID: String, nyeInstitusjoner: List<String>) {
        logger.debug("Prøver å legge til Deltaker/Institusions på buc samt prefillSed og sende inn til Rina ")
        logger.info("X005 finnes ikke på buc, legger til Deltakere/Institusjon på vanlig måte")
        euxKlient.putBucMottakere(euxCaseID, nyeInstitusjoner)
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
    @VisibleForTesting
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
                "P_BUC_02" -> bucutil.getDocumentByType(SEDType.P2100)
                "P_BUC_10" -> bucutil.getDocumentByType(SEDType.P15000)
                "P_BUC_05" -> bucutil.getDocumentByType(SEDType.P8000)
                else -> bucutil.getDocumentByType(SEDType.P6000)
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

    private fun filterPinGjenlevendePin(gjenlevende: Person?, sedType: SEDType, rinaidAvdod: String): String? {
        val pin = gjenlevende?.pin?.firstOrNull { it.land == "NO" }
        return if (pin == null) {
            logger.warn("Ingen fnr funnet på gjenlevende. ${sedType}, rinaid: $rinaidAvdod")
            null
        } else {
            pin.identifikator
        }
    }

    //** hente rinasaker fra RINA og SAF
    fun getRinasaker(fnr: String, aktoerId: String): List<Rinasak> {
        // henter rina saker basert på tilleggsinformasjon i journalposter
        val rinaSakIderMetadata = safClient.hentRinaSakIderFraDokumentMetadata(aktoerId)
        logger.debug("hentet rinasaker fra documentMetadata size: ${rinaSakIderMetadata.size}")

        // Henter rina saker basert på fnr
        val rinaSakerMedFnr = euxKlient.getRinasaker(fnr)
        logger.debug("hentet rinasaker fra eux-rina-api size: ${rinaSakerMedFnr.size}")

        // Filtrerer vekk saker som allerede er hentet som har fnr
        val rinaSakIderMedFnr = hentRinaSakIder(rinaSakerMedFnr)
        val rinaSakIderUtenFnr = rinaSakIderMetadata.minus(rinaSakIderMedFnr)

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

    fun createBuc(buctype: String): String {
        val euxCaseId = euxKlient.createBuc(buctype)

        statistikk.produserBucOpprettetHendelse(euxCaseId, null)
        return euxCaseId
    }

    /**
     * Returnerer en distinct liste av rinaSakIDer
     *  @param rinaSaker liste av rinasaker fra EUX datamodellen
     */
    fun hentRinaSakIder(rinaSaker: List<Rinasak>) = rinaSaker.map { it.id!! }
}

data class BucOgDocumentAvdod(
        val rinaidAvdod: String,
        val buc: Buc,
        var dokumentJson: String = ""
)