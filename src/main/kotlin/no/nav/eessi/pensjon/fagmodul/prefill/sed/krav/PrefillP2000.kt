package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.ValidationException
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * preutfylling av NAV-P2000 SED for søknad krav om alderpensjon
 */
class PrefillP2000(private val prefillNav: PrefillNav, private val preutfyllingPersonFraTPS: PrefillPersonDataFromTPS, dataFromPEN: PensjonsinformasjonHjelper) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2000::class.java) }

    private val sakPensiondata: KravDataFromPEN = KravDataFromPEN(dataFromPEN)

    override fun prefill(prefillData: PrefillDataModel): SED {
        val sedId = prefillData.getSEDid()

        prefillData.saktype = KravDataFromPEN.KSAK.ALDER.name
        logger.debug("----------------------------------------------------------"
                + "\nSaktype              : ${prefillData.saktype} "
                + "\nSøker etter SaktId   : ${prefillData.penSaksnummer} "
                + "\nPreutfylling NAV     : ${prefillNav::class.java} "
                + "\nPreutfylling TPS     : ${preutfyllingPersonFraTPS::class.java} "
                + "\nPreutfylling Pensjon : ${sakPensiondata::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        //skipper å hente persondata dersom NAVSED finnes
        if (prefillData.kanFeltSkippes("NAVSED")) {
            sed.nav = Nav()
            //henter opp persondata
        } else {
            sed.nav = createNav(prefillData)
        }

        //skipper å henter opp pensjondata hvis PENSED finnes
        try {
            if (prefillData.kanFeltSkippes("PENSED")) {
                val pensjon = createPensjon(prefillData)
                //vi skal ha blank pensjon ved denne toggle
                //vi må ha med kravdato
                sed.pensjon = Pensjon(kravDato = pensjon.kravDato)

                //henter opp pensjondata
            } else {
                val pensjon = createPensjon(prefillData)

                //gjenlevende hvis det finnes..
                pensjon.gjenlevende = createGjenlevende(prefillData)
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

    //henter persondata fra TPS fyller ut sed.nav
    private fun createNav(prefillData: PrefillDataModel): Nav {
        logger.debug("[${prefillData.getSEDid()}] Preutfylling NAV")
        return prefillNav.prefill(prefillData)
    }

    //henter pensjondata fra PESYS fyller ut sed.pensjon
    private fun createPensjon(prefillData: PrefillDataModel): Pensjon {
        logger.debug("[${prefillData.getSEDid()}]   Preutfylling PENSJON")
        return sakPensiondata.prefill(prefillData)
    }

    //fylles ut kun når vi har etterlatt etterlattPinID.
    //noe vi må få fra PSAK. o.l
    private fun createGjenlevende(prefillData: PrefillDataModel): Bruker? {
        var gjenlevende: Bruker? = null
        if (prefillData.erGyldigEtterlatt()) {
            logger.debug("          Utfylling gjenlevende (etterlatt)")
            gjenlevende = preutfyllingPersonFraTPS.prefillBruker(prefillData.personNr)
        }
        return gjenlevende
    }

    override fun validate(data: SED) {
        when {
            data.nav?.bruker?.person?.etternavn == null -> throw ValidationException("Etternavn mangler")
            data.nav?.bruker?.person?.fornavn == null -> throw ValidationException("Fornavn mangler")
            data.nav?.bruker?.person?.foedselsdato == null -> throw ValidationException("Fødseldsdato mangler")
            data.nav?.bruker?.person?.kjoenn == null -> throw ValidationException("Kjønn mangler")
            data.nav?.krav?.dato == null -> throw ValidationException("Kravdato mangler")
        }
    }
}