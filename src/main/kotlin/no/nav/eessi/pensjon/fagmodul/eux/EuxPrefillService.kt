package no.nav.eessi.pensjon.fagmodul.eux

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct

@Service
class EuxPrefillService (private val euxKlient: EuxKlient,
                         private val statistikk: StatistikkHandler,
                         @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(EuxPrefillService::class.java)

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
        logger.info("Legger til Deltakere/Institusjon på vanlig måte, ny Buc")
        euxKlient.putBucMottakere(euxCaseID, nyeInstitusjoner)
    }

    fun createBuc(buctype: String): String {
        val euxCaseId = euxKlient.createBuc(buctype)
        try {
            statistikk.produserBucOpprettetHendelse(euxCaseId, null)
        } catch (ex: Exception) {
            logger.warn("Feiler ved statistikk")
        }
        return euxCaseId
    }

    //sjekk for ekisternede deltakere og nye fra UI. samt legge til deltakere på vanlig måte dersom buc er ny
    //legger til evt. x005 dersom det finens.
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

}


data class BucOgDocumentAvdod(
        val rinaidAvdod: String,
        val buc: Buc,
        var dokumentJson: String = ""
)