package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP2100(private val prefillNav: PrefillNav,
                   private val pensjonsinformasjonService: PensjonsinformasjonService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2100::class.java) }


    fun prefill(prefillData: PrefillDataModel, personData: PersonData): SED {
        val sedId = prefillData.getSEDid()

        prefillData.saktype = PenSaktype.GJENLEV_BARNEP.name
        logger.debug("\n\n----------------------------------------------------------"
                + "\nSaktype                  : ${prefillData.saktype} "
                + "\nSøker etter SaktId       : ${prefillData.penSaksnummer} "
                + "\nSøker etter avdodaktor   : ${prefillData.avdod?.aktorId} "
                + "\nerGyldigEtterlatt        : ${prefillData.avdod?.aktorId?.isNotEmpty()} "
                + "\nSøker etter gjenlaktoer  : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ \n")

        val sed = prefillData.sed

        //skipper å hente persondata dersom NAVSED finnes
        if (prefillData.kanFeltSkippes("NAVSED")) {
            sed.nav = Nav()
        } else {
            //henter opp persondata
            sed.nav = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData , brukerInformasjon = prefillData.getPersonInfoFromRequestData())
        }

        val pensak = PrefillP2xxxPensjon.hentRelevantPensjonSak(
                pensjonsinformasjonService,
                prefillData.bruker.aktorId,
                prefillData.penSaksnummer,
                prefillData.saktype,
                this::class.simpleName!!)

        try {
            sed.pensjon =
                    if (pensak == null) Pensjon()
                    else {
                        val pensjon = PrefillP2xxxPensjon.createPensjon(
                                prefillData.bruker.norskIdent,
                                prefillData.penSaksnummer,
                                pensak,
                                prefillData.andreInstitusjon,
                                eventuellGjenlevende(prefillData, personData.forsikretPerson))
                        if (prefillData.kanFeltSkippes("PENSED")) {
                            Pensjon(
                                    kravDato = pensjon.kravDato,
                                    gjenlevende = pensjon.gjenlevende
                            ) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
                        } else {
                            pensjon
                        }
                    }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            // TODO Should we really swallow this?
            // TODO What's consequences by removing this? and throw it up to UI?
        }

        KravHistorikkHelper.settKravdato(prefillData, sed)

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return prefillData.sed
    }

    private fun eventuellGjenlevende(prefillData: PrefillDataModel, gjenlevendeBruker: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker?): Bruker? {
        return if (prefillData.avdod != null) {
            logger.info("          Utfylling gjenlevende (etterlatt persjon.gjenlevende)")
            prefillNav.createBruker(gjenlevendeBruker!!, null, null)
        } else null
    }

}
