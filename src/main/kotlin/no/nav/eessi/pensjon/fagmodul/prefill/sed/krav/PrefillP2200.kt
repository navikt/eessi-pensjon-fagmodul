package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * preutfylling av NAV-P2200 SED for søknad krav om uforepensjon
 */
class PrefillP2200(private val prefillNav: PrefillNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2200::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonData, sak: V1Sak?): SED {
        val sedType = prefillData.getSEDType()

        logger.debug("----------------------------------------------------------"
                + "\nSaktype                 : ${sak?.sakType} "
                + "\nSøker etter SakId       : ${prefillData.penSaksnummer} "
                + "\nSøker etter aktoerid    : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [$sedType] START |------------------ ")

        val sed = prefillData.sed

        //henter opp persondata
        sed.nav = prefillNav.prefill(
                penSaksnummer = prefillData.penSaksnummer,
                bruker = prefillData.bruker,
                avdod = prefillData.avdod,
                personData = personData ,
                brukerInformasjon = prefillData.getPersonInfoFromRequestData()
        )

        try {
            sed.pensjon = Pensjon()
            if (sak != null) {
                val meldingOmPensjon = PrefillP2xxxPensjon.createPensjon(
                        prefillData.bruker.norskIdent,
                        prefillData.penSaksnummer,
                        sak,
                        prefillData.andreInstitusjon)
                sed.pensjon = meldingOmPensjon.pensjon
                if (prefillData.isMinimumPrefill()) {
                    sed.pensjon = Pensjon(
                            kravDato = meldingOmPensjon.pensjon.kravDato
                    ) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
                }
            }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            // TODO Should we really swallow this?
        }

        KravHistorikkHelper.settKravdato(sed)

        logger.debug("-------------------| Preutfylling [$sedType] END |------------------- ")
        return prefillData.sed
    }
}
