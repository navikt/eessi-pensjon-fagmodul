package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.vedtak.V1Vedtak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * preutfylling av NAV-P2000 SED for søknad krav om alderpensjon
 */
class PrefillP2000(private val prefillNav: PrefillNav)  {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2000::class.java) }

    fun prefillPDL(prefillPDLNav: PrefillPDLNav, prefillData: PrefillDataModel, personData: PersonDataCollection?, sak: V1Sak?, vedtak: V1Vedtak? = null): SED {
        require(personData != null) { "Trenger PersonDataCollection" }

        postPrefill(prefillData, sak, vedtak)

        val nav = prefillPDLNav(prefillPDLNav, prefillData, personData)

        return prefillpen(prefillData, nav, sak, vedtak)
    }

    fun prefill(prefillData: PrefillDataModel, personData: PersonData, sak: V1Sak?, vedtak: V1Vedtak? = null): SED {
        postPrefill(prefillData, sak, vedtak)

        val nav = prefillNav(prefillData, personData)

        return prefillpen(prefillData, nav, sak, vedtak)
    }

    private fun prefillPDLNav(prefillPDLNav: PrefillPDLNav, prefillData: PrefillDataModel, personData: PersonDataCollection): Nav {
        return prefillPDLNav.prefill(
            penSaksnummer = prefillData.penSaksnummer,
            bruker = prefillData.bruker,
            avdod = prefillData.avdod,
            personData = personData,
            brukerInformasjon = prefillData.getPersonInfoFromRequestData()
        )
    }

    private fun prefillNav(prefillData: PrefillDataModel, personData: PersonData): Nav {
        return prefillNav.prefill(
            penSaksnummer = prefillData.penSaksnummer,
            bruker = prefillData.bruker,
            avdod = prefillData.avdod,
            personData = personData,
            brukerInformasjon = prefillData.getPersonInfoFromRequestData()
        )
    }

    private fun postPrefill(prefillData: PrefillDataModel, sak: V1Sak?, vedtak: V1Vedtak?) {
        val sedType = "P2000"
        PrefillP2xxxPensjon.validerGyldigVedtakEllerKravtypeOgArsak(sak, sedType, vedtak)
        logger.debug("----------------------------------------------------------"
                + "\nSaktype                 : ${sak?.sakType} "
                + "\nSøker etter SakId       : ${prefillData.penSaksnummer} "
                + "\nSøker etter aktoerid    : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [$sedType] START |------------------ ")
    }

    private fun prefillpen(prefillData: PrefillDataModel, nav: Nav, sak: V1Sak?, vedtak: V1Vedtak? = null): SED {
        val sedType = prefillData.getSEDType()

        //valider pensjoninformasjon,
        var pensjon: Pensjon ? = null
        try {
            pensjon = Pensjon()
            val meldingOmPensjon = PrefillP2xxxPensjon.createPensjon(
                    prefillData.bruker.norskIdent,
                    prefillData.penSaksnummer,
                    sak,
                    prefillData.andreInstitusjon)
            pensjon = meldingOmPensjon.pensjon
            if (prefillData.isMinimumPrefill()) {
                pensjon = Pensjon(
                        kravDato = meldingOmPensjon.pensjon.kravDato
                ) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
            }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            //hvis feiler lar vi SB få en SED i RINA
        }

        //opprette en P2000 sed
        val sed = SED(
            sed = "P2000",
            nav = nav,
            pensjon = pensjon
        )
        PrefillP2xxxPensjon.settKravdato(sed)

        logger.debug("-------------------| Preutfylling [$sedType] END |------------------- ")
        validate(sed)
        return sed
    }

    private fun validate(sed: SED) {
        when {
            sed.nav?.bruker?.person?.etternavn == null -> throw ValidationException("Etternavn mangler")
            sed.nav?.bruker?.person?.fornavn == null -> throw ValidationException("Fornavn mangler")
            sed.nav?.bruker?.person?.foedselsdato == null -> throw ValidationException("Fødseldsdato mangler")
            sed.nav?.bruker?.person?.kjoenn == null -> throw ValidationException("Kjønn mangler")
            sed.nav?.krav?.dato == null -> {
                logger.warn("Kravdato mangler! Gjelder utsendelsen 'Førstegangsbehandling kun utland', se egen rutine på Navet.")
                throw ValidationException("Kravdato mangler\nGjelder utsendelsen \"Førstegangsbehandling kun utland\", se egen rutine på Navet.")
            }
        }
    }
}

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
class ValidationException(message: String) : IllegalArgumentException(message)
