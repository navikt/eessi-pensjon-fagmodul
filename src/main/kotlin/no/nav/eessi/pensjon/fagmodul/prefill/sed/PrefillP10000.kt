package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP10000(private val prefillSed: PrefillSed) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP10000::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonDataCollection): SED {

        logger.info("Default SED prefill [${prefillData.sedType}]")

        val sed = prefillSed.prefill(prefillData, personData)
        if (prefillData.avdod != null) {
            logger.info("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
            sed.nav?.annenperson =  sed.pensjon?.gjenlevende
            sed.nav?.annenperson?.person?.rolle = "01"  //Claimant
            sed.pensjon = null
        }
        return sed
    }

}