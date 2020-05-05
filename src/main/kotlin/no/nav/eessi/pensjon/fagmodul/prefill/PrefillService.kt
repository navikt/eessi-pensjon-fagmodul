package no.nav.eessi.pensjon.fagmodul.prefill

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.ValidationException
import no.nav.eessi.pensjon.fagmodul.sedmodel.InstitusjonX005
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class PrefillService(private val factory: PrefillSEDService,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(PrefillService::class.java)

    private lateinit var PrefillSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        PrefillSed = metricsHelper.init("PrefillSed")
    }

    //preutfylling av sed fra TPS, PESYS, AAREG o.l skjer her..
    @Throws(ValidationException::class)
    fun prefillSed(dataModel: PrefillDataModel): SED {
        return PrefillSed.measure {

            logger.info("******* Starter med preutfylling *******\nSED: ${dataModel.getSEDid()} aktoerId: ${dataModel.bruker.aktorId} sakNr: ${dataModel.penSaksnummer}")

            val startTime = System.currentTimeMillis()
            val sed = factory.prefill(dataModel)
            val endTime = System.currentTimeMillis() - startTime

            logger.info("******* Prefill SED tok $endTime ms. *******")

            sed
        }
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
                val datax005 = PrefillDataModel(penSaksnummer = data.penSaksnummer, bruker = data.bruker, avdod = null).apply {
                    sed = SED(SEDType.X005.name)
                    euxCaseID = data.euxCaseID
                    institusjonX005 = institusjon
                }

                factory.prefill(datax005)
            }
}
