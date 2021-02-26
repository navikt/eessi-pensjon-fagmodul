package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
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

    fun prefillSed(prefillData: PrefillDataModel, personData: PersonDataCollection, sak: V1Sak?, vedtak: V1Vedtak? = null) : SED {
        logger.debug("----------------------------------------------------------"
                + "\nSaktype                 : ${sak?.sakType} "
                + "\nSøker etter SakId       : ${prefillData.penSaksnummer} "
                + "\nSøker etter aktoerid    : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [${prefillData.sedType}] START |------------------ ")

        val sedType = prefillData.sedType

        val pensjon = PrefillP2xxxPensjon.populerPensjon(prefillData, sak)

        //henter opp persondata
        val nav = prefillNav.prefill(
            penSaksnummer = prefillData.penSaksnummer,
            bruker = prefillData.bruker,
            avdod = prefillData.avdod,
            personData = personData,
            brukerInformasjon = prefillData.getPersonInfoFromRequestData(),
            pensjon?.kravDato
        )

        PrefillP2xxxPensjon.validerGyldigVedtakEllerKravtypeOgArsak(sak, sedType, vedtak)

        return SED(
            type = sedType,
            nav = nav,
            pensjon = pensjon
        ).also {
            logger.debug("-------------------| Preutfylling [$sedType] END |------------------- ")
        }
    }
}
