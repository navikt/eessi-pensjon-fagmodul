package no.nav.eessi.pensjon.fagmodul.services

import no.nav.eessi.pensjon.fagmodul.models.*
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillSED
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrefillService(private val prefillSED: PrefillSED) {

    private val logger = LoggerFactory.getLogger(PrefillService::class.java)

    private val validator = SedValidator()

    //preutfylling av sed fra TPS, PESYS, AAREG o.l skjer her..
    @Throws(SedValidatorException::class)
    fun prefillSed(dataModel: PrefillDataModel): PrefillDataModel {

        val startTime = System.currentTimeMillis()
        val data = prefillSED.prefill(dataModel)
        val endTime = System.currentTimeMillis()
        logger.debug("PrefillSED tok ${endTime - startTime} ms.")

        if (SEDType.P2000.name == data.getSEDid()) {
            validator.validateP2000(data.sed)
        }

        return data
    }


    @Throws(SedValidatorException::class)
    fun prefillEnX005ForHverInstitusjon(nyeDeltakere: List<InstitusjonItem>, data: PrefillDataModel) =
            nyeDeltakere.map {
                val datax005 = PrefillDataModel().apply {
                    sed = SED.create(SEDType.X005.name)
                    penSaksnummer = data.penSaksnummer
                    personNr = data.personNr
                    euxCaseID = data.euxCaseID
                }

                val x005 = prefillSED.prefill(datax005)

                logger.debug("Legger til Institusjon på X005 ${it.institution}")
                // ID og Navn på X005 er påkrevd må hente innn navn fra UI.
                val institusjonX005 = InstitusjonX005(
                        id = it.checkAndConvertInstituion(),
                        navn = it.name ?: it.checkAndConvertInstituion()
                )
                x005.sed.nav?.sak?.leggtilinstitusjon?.institusjon = institusjonX005
                x005
            }
}