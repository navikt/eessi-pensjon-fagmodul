package no.nav.eessi.eessifagmodul.prefill.nav

import no.nav.eessi.eessifagmodul.models.Pensjon
import no.nav.eessi.eessifagmodul.models.PensjoninformasjonException
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.Prefill
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillPensjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
//TODO: Denne klasser vil nok utgå når alle SED er klar med egen Preutfylling..
class PrefillPerson(private val prefillNav: PrefillNav, private val prefilliPensjon: PrefillPensjon) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPerson::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {

        logger.debug("----------------------------------------------------------")

        logger.debug("Preutfylling NAV     : ${prefillNav::class.java} ")
        logger.debug("Preutfylling Pensjon : ${prefilliPensjon::class.java} ")

        logger.debug("------------------| Preutfylling START |------------------ ")

        logger.debug("[${prefillData.getSEDid()}] Preutfylling Utfylling Data")

        val sed = prefillData.sed

        if (prefillData.kanFeltSkippes("NAVSED")) {
            //skipper å hente persondata dersom NAVSED finnes
            sed.nav = null
        } else {
            //henter opp persondata
            sed.nav = prefillNav.prefill(prefillData)
        }
        logger.debug("[${prefillData.getSEDid()}] Preutfylling Utfylling NAV")

        try {
            if (prefillData.kanFeltSkippes("PENSED")) {
                //vi skal ha blank pensjon ved denne toggle
                sed.pensjon = null

                //henter opp pensjondata
            } else {
                sed.pensjon = prefilliPensjon.prefill(prefillData)
            }
            logger.debug("[${prefillData.getSEDid()}] Preutfylling Utfylling Pensjon")
        } catch (pen: PensjoninformasjonException) {
            logger.error(pen.message)
            sed.pensjon = Pensjon()
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
        }

        logger.debug("-------------------| Preutfylling END |------------------- ")
        return prefillData.sed

    }

}

