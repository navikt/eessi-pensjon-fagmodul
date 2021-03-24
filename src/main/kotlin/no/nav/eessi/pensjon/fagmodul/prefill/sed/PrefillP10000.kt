package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.BrukerInformasjon
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.P10000
import no.nav.eessi.pensjon.fagmodul.sedmodel.P10000Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP10000(private val prefillNav: PrefillPDLNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP10000::class.java) }

    fun prefill(penSaksnummer: String,
                bruker: PersonId,
                avdod: PersonId?,
                brukerInformasjon: BrukerInformasjon?,
                personData: PersonDataCollection): P10000 {

        val prefillPensjon = try {
            val pensjon = avdod?.let {
                logger.info("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
                val gjenlevendePerson = prefillNav.createBruker(personData.forsikretPerson!!, null, null)
                Pensjon(gjenlevende = gjenlevendePerson)
            }
            pensjon
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            Pensjon()
        }

        //henter opp persondata
        val navSed = prefillNav.prefill(
            penSaksnummer = penSaksnummer,
            bruker = bruker,
            avdod = avdod,
            personData = personData,
            brukerInformasjon = brukerInformasjon,
            prefillPensjon?.kravDato
        )

        //Spesielle SED som har etterlette men benyttes av flere BUC
        //Må legge gjenlevende også som nav.annenperson
        if (avdod != null) {
            navSed.annenperson = prefillPensjon?.gjenlevende
            navSed.annenperson?.person?.rolle = "01"  //Claimant - etterlatte
        }

        logger.debug("-------------------| Preutfylling END |------------------- ")
        val p10000 = P10000(nav = navSed, p10000Pensjon = P10000Pensjon(prefillPensjon?.gjenlevende))

        if (avdod != null) {
            logger.info("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
            p10000.nav?.annenperson =  p10000.pensjon?.gjenlevende
            p10000.nav?.annenperson?.person?.rolle = "01"  //Claimant
            p10000.pensjon = null
        }
        return p10000
    }
}