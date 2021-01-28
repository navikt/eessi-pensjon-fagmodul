package no.nav.eessi.pensjon.fagmodul.prefill

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.ValidationException
import no.nav.eessi.pensjon.fagmodul.sedmodel.InstitusjonX005
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class PrefillService(private val prefillSedService: PrefillSEDService,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(PrefillService::class.java)

    private lateinit var PrefillSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        PrefillSed = metricsHelper.init("PrefillSed")
    }

//    fun prefillSedtoJson(dataModel: PrefillDataModel, version: String) = prefillSedtoJson(dataModel, version, null)

    fun prefillSedtoJson(dataModel: PrefillDataModel, version: String, personDataCollection: PersonDataCollection? = null): SedAndType {
        return PrefillSed.measure {
            logger.info("******* Starter med preutfylling ******* $dataModel")
            try {
                val sed = prefillSedService.prefill(dataModel, personDataCollection)
                logger.debug("sedType: ${sed.getType()}")
                val sedType = SEDType.valueOf(sed.getType())

                //synk sed versjon med buc versjon
                updateSEDVersion(sed, version)
                return@measure SedAndType(sedType, sed.toJsonSkipEmpty())

            } catch (ex: Exception) {
                logger.error("Noe gikk galt under prefill: ", ex)
                throw ex
            }
        }
    }

    //flyttes til prefill / en eller annen service?
    private fun updateSEDVersion(sed: SED, bucVersion: String) {
        when(bucVersion) {
            "v4.2" -> {
                sed.sedGVer="4"
                sed.sedVer="2"
            }
            else -> {
                sed.sedGVer="4"
                sed.sedVer="1"
            }
        }
        logger.debug("SED version: v${sed.sedGVer}.${sed.sedVer} + BUC version: $bucVersion")
    }

    /**
     * Prefill for X005 - Legg til ny institusjon
     */
    @Throws(ValidationException::class)
    fun prefillEnX005ForHverInstitusjon(nyeDeltakere: List<InstitusjonItem>, data: PrefillDataModel) =
            nyeDeltakere.map {
                logger.debug("Legger til Institusjon på X005 ${it.institution}")
                // ID og Navn på X005 er påkrevd må hente innn navn fra UI.
                val institusjon = InstitusjonX005(
                        id = it.checkAndConvertInstituion(),
                        navn = it.name ?: it.checkAndConvertInstituion()
                )
                val sedtype = SEDType.X005.name
                val datax005 = data.copy(avdod = null, sedType = sedtype, sed = SED(sedtype), institution = emptyList(), institusjonX005 = institusjon)

                prefillSedService.prefill(datax005)
            }
}

class SedAndType(val sedType: SEDType, val sed: String)