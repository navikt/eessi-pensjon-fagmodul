package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.BrukerInformasjon
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.P10000
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP10000(private val prefillNav: PrefillPDLNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP10000::class.java) }

    fun prefill(penSaksnummer: String,
                bruker: PersonId,
                avdod: PersonId?,
                brukerInformasjon: BrukerInformasjon?,
                personData: PersonDataCollection): P10000 {

        val gjenlevende = try {
            val gjenlevende = avdod?.let {
                logger.info("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
                prefillNav.createBruker(personData.forsikretPerson!!, null, null)
            }
            gjenlevende
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            null
        }

        //henter opp persondata
        val navSed = prefillNav.prefill(
            penSaksnummer = penSaksnummer,
            bruker = bruker,
            avdod = avdod,
            personData = personData,
            brukerInformasjon = brukerInformasjon,
            null
        )

        //Spesielle SED som har etterlette men benyttes av flere BUC
        //Må legge gjenlevende også som nav.annenperson
        if (avdod != null) {
            navSed.annenperson = gjenlevende
            navSed.annenperson?.person?.rolle = "01"  //Claimant - etterlatte
        }
        logger.debug("-------------------| Preutfylling END |------------------- ")

        return P10000(nav = navSed)
    }
}