package no.nav.eessi.pensjon.fagmodul.eux

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct

@Service
class EuxPrefillService (private val euxKlient: EuxKlient,
                         private val statistikk: StatistikkHandler,
                         @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(EuxPrefillService::class.java)

    // Vi trenger denne no arg konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(EuxKlient(RestTemplate()),
        StatistikkHandler("Q2", KafkaTemplate(DefaultKafkaProducerFactory(emptyMap())), ""))

    private lateinit var opprettSvarSED: MetricsHelper.Metric
    private lateinit var opprettSED: MetricsHelper.Metric

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
    fun opprettJsonSedOnBuc(jsonNavSED: String, SedType: SedType, euxCaseId: String, vedtakId: String?): BucSedResponse {
        logger.info("Forsøker å opprette en $SedType på rinasakId: $euxCaseId")
        logger.debug("Logger ut $jsonNavSED")
        val bucSedResponse  = euxKlient.opprettSed(jsonNavSED, euxCaseId, opprettSED, "Feil ved opprettSed: $SedType, med rinaId: $euxCaseId")
        statistikk.produserSedOpprettetHendelse(euxCaseId, bucSedResponse.documentId, vedtakId)

        return bucSedResponse
    }

    fun addInstitution(euxCaseID: String, nyeInstitusjoner: List<String>) {
        logger.debug("Prøver å legge til Deltaker/Institusions på buc samt prefillSed og sende inn til Rina ")
        logger.info("X005 finnes ikke på buc, legger til Deltakere/Institusjon på vanlig måte")
        euxKlient.putBucMottakere(euxCaseID, nyeInstitusjoner)
    }

    fun createBuc(buctype: String): String {
        val euxCaseId = euxKlient.createBuc(buctype)

        statistikk.produserBucOpprettetHendelse(euxCaseId, null)
        return euxCaseId
    }

    fun checkAndAddInstitution(dataModel: PrefillDataModel, bucUtil: BucUtils, x005Liste: List<SED>) {
        logger.info(
            "Hvem er caseOwner: ${
                bucUtil.getCaseOwner()?.toJson()
            } på buc: ${bucUtil.getProcessDefinitionName()}"
        )
        val navCaseOwner = bucUtil.getCaseOwner()?.country == "NO"

        val nyeInstitusjoner = bucUtil.findNewParticipants(dataModel.getInstitutionsList())

        if (nyeInstitusjoner.isNotEmpty()) {
            if (bucUtil.findFirstDocumentItemByType(SedType.X005) == null) {
                addInstitution(dataModel.euxCaseID, nyeInstitusjoner.map { it.institution })
            } else {

                //--gjort noe. ..
                nyeInstitusjoner.forEach {
                    if (!navCaseOwner && it.country != "NO") {
                        logger.error("NAV er ikke sakseier. Du kan ikke legge til deltakere utenfor Norge")
                        throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "NAV er ikke sakseier. Du kan ikke legge til deltakere utenfor Norge"
                        )
                    }
                }
                addInstitutionMedX005(
                    dataModel,
                    nyeInstitusjoner,
                    bucUtil.getProcessDefinitionVersion(),
                    x005Liste
                )
            }
        }
    }

    private fun addInstitutionMedX005(
        dataModel: PrefillDataModel,
        nyeInstitusjoner: List<InstitusjonItem>,
        bucVersion: String,
        x005Liste: List<SED>
    ) {
        logger.info("X005 finnes på buc, Sed X005 prefills og sendes inn: ${nyeInstitusjoner.toJsonSkipEmpty()}")

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
            logger.error(
                "Feiler ved oppretting av X005  (ny institusjon), euxCaseid: ${dataModel.euxCaseID}, sed: ${dataModel.sedType}",
                execptionError
            )
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Feiler ved oppretting av X005 (ny institusjon) for euxCaseId: ${dataModel.euxCaseID}"
            )
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

}


data class BucOgDocumentAvdod(
        val rinaidAvdod: String,
        val buc: Buc,
        var dokumentJson: String = ""
)