package no.nav.eessi.pensjon.fagmodul.prefill

import no.nav.eessi.pensjon.fagmodul.sedmodel.InstitusjonX005
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.models.*
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSED
import no.nav.eessi.pensjon.fagmodul.prefill.model.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrefillService(private val prefillSED: PrefillSED) {

    private val logger = LoggerFactory.getLogger(PrefillService::class.java)

    //preutfylling av sed fra TPS, PESYS, AAREG o.l skjer her..
    @Throws(ValidationException::class)
    fun prefillSed(dataModel: PrefillDataModel): PrefillDataModel {

        val startTime = System.currentTimeMillis()
        val data = prefillSED.prefill(dataModel)
        val endTime = System.currentTimeMillis()
        logger.debug("PrefillSED tok ${endTime - startTime} ms.")

        prefillSED.validate(data)

        return data
    }


    @Throws(ValidationException::class)
    fun prefillEnX005ForHverInstitusjon(nyeDeltakere: List<InstitusjonItem>, data: PrefillDataModel) =
            nyeDeltakere.map {
                logger.debug("Legger til Institusjon på X005 ${it.institution}")
                // ID og Navn på X005 er påkrevd må hente innn navn fra UI.
                val institusjon = InstitusjonX005(
                        id = it.checkAndConvertInstituion(),
                        navn = it.name ?: it.checkAndConvertInstituion()
                )
                val datax005 = PrefillDataModel().apply {
                    sed = SED(SEDType.X005.name)
                    penSaksnummer = data.penSaksnummer
                    personNr = data.personNr
                    euxCaseID = data.euxCaseID
                    institusjonX005 = institusjon
                }

                val x005 = prefillSED.prefill(datax005)

                x005
            }
}