package no.nav.eessi.pensjon.fagmodul.prefill.person

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.slf4j.Logger
import org.slf4j.LoggerFactory

//TODO: Denne klasser vil nok utgå når alle SED er klar med egen Preutfylling..
class PrefillSed(private val prefillNav: PrefillNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillSed::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonData): SED {

        logger.debug("----------------------------------------------------------")
        logger.debug("Preutfylling NAV     : ${prefillNav::class.java} ")
        logger.debug("------------------| Preutfylling START |------------------ ")
        logger.debug("[${prefillData.getSEDType()}] Preutfylling Utfylling Data")

        val sed = prefillData.sed

        //henter opp persondata
        sed.nav = prefillNav.prefill(
                penSaksnummer = prefillData.penSaksnummer,
                bruker = prefillData.bruker,
                avdod = prefillData.avdod,
                personData = personData,
                brukerInformasjon = prefillData.getPersonInfoFromRequestData()
        )
        logger.debug("[${prefillData.getSEDType()}] Preutfylling Utfylling NAV")

        sed.pensjon = try {
            val pensjon = prefillData.avdod?.let {
                logger.info("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
                val gjenlevendetps = prefillNav.createBruker(personData.forsikretPerson, null, null)
                Pensjon(gjenlevende = gjenlevendetps)
            }
            logger.debug("[${prefillData.getSEDType()}] Preutfylling Utfylling Pensjon")
            pensjon
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
             Pensjon()
        }

        //Spesielle SED som har etterlette men benyttes av flere BUC
        //Må legge gjenlevende også som nav.annenperson
        if (prefillData.avdod != null) {
            sed.nav?.annenperson = sed.pensjon?.gjenlevende
            sed.nav?.annenperson?.person?.rolle = "01"  //Claimant - etterlatte
        }

        logger.debug("-------------------| Preutfylling END |------------------- ")
        return prefillData.sed

    }

}

