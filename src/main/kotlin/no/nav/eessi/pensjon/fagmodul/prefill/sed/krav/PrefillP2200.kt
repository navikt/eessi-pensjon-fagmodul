package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * preutfylling av NAV-P2200 SED for søknad krav om uforepensjon
 */
class PrefillP2200(private val sakPensiondata: KravDataFromPEN) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2200::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {
        val sedId = prefillData.getSEDid()

        logger.debug("----------------------------------------------------------"
                + "\nPreutfylling Pensjon : ${sakPensiondata::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        //henter opp persondata
        sed.nav = sakPensiondata.createNav(prefillData)

        //henter opp pensjondat
        //val pensjon = createPensjon(prefillData)
        //skipper å henter opp pensjondata hvis PENSED finnes
        try {
            if (prefillData.kanFeltSkippes("PENSED")) {
                val pensjon = sakPensiondata.createPensjon(prefillData)
                //vi skal ha blank pensjon ved denne toggle
                //vi må ha med kravdato
                sed.pensjon = Pensjon(kravDato = pensjon.kravDato)

                //henter opp pensjondata
            } else {
                val pensjon = sakPensiondata.createPensjon(prefillData)

                //gjenlevende hvis det finnes..
                pensjon.gjenlevende = sakPensiondata.createGjenlevende(prefillData)
                //legger pensjon på sed (få med oss gjenlevende/avdød)
                sed.pensjon = pensjon
            }
        } catch (pen: PensjoninformasjonException) {
            logger.error(pen.message)
            sed.pensjon = Pensjon()
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
        }

        //sette korrekt kravdato på sed (denne kommer fra PESYS men opprettes i nav?!)
        //9.1.
        if (prefillData.kanFeltSkippes("NAVSED")) {
            //sed.nav?.krav = Krav("")
            //pensjon.kravDato = null
        } else {
            logger.debug("9.1     legger til nav kravdato fra pensjon kravdato : ${sed.pensjon?.kravDato} ")
            sed.nav?.krav = sed.pensjon?.kravDato
        }

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return prefillData.sed
    }
}