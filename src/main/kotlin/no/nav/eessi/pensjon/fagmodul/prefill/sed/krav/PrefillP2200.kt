package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.vedtak.V1Vedtak
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * preutfylling av NAV-P2200 SED for søknad krav om uforepensjon
 */
class PrefillP2200(private val prefillNav: PrefillPDLNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2200::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonDataCollection, sak: V1Sak?, vedtak: V1Vedtak? = null) : SED {
        val sedType = prefillData.sedType

        logger.debug("----------------------------------------------------------"
                + "\nSaktype                 : ${sak?.sakType} "
                + "\nSøker etter SakId       : ${prefillData.penSaksnummer} "
                + "\nSøker etter aktoerid    : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [$sedType] START |------------------ ")


        //henter opp persondata
        val nav = prefillNav.prefill(
                penSaksnummer = prefillData.penSaksnummer,
                bruker = prefillData.bruker,
                avdod = prefillData.avdod,
                personData = personData ,
                brukerInformasjon = prefillData.getPersonInfoFromRequestData()
        )


        PrefillP2xxxPensjon.validerGyldigVedtakEllerKravtypeOgArsak(sak, sedType, vedtak)

        val andreInstitusjondetaljer = EessiInformasjon().asAndreinstitusjonerItem()
        var pensjon : Pensjon? = null
        try {
            pensjon = Pensjon()
                val meldingOmPensjon = PrefillP2xxxPensjon.createPensjon(
                        prefillData.bruker.norskIdent,
                        prefillData.penSaksnummer,
                        sak,
                        andreInstitusjondetaljer)
                pensjon = meldingOmPensjon.pensjon
                if (prefillData.sedType != SEDType.P6000) {
                    pensjon = Pensjon(
                            kravDato = meldingOmPensjon.pensjon.kravDato
                    ) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
                }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            // TODO Should we really swallow this?
        }

        val sed = SED(
            type = sedType,
            nav = nav,
            pensjon = pensjon
        )

        PrefillP2xxxPensjon.settKravdato(sed)

        logger.debug("-------------------| Preutfylling [$sedType] END |------------------- ")
        return sed
    }
}
