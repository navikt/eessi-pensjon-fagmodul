package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * preutfylling av NAV-P2000 SED for søknad krav om alderpensjon
 */
class PrefillP2000(private val prefillNav: PrefillNav,
                   private val pensjonsinformasjonService: PensjonsinformasjonService)  {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2000::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonData): SED {
        val sedId = prefillData.getSEDid()

        prefillData.saktype = EPSaktype.ALDER.name
        logger.debug("----------------------------------------------------------"
                + "\nSaktype              : ${prefillData.saktype} "
                + "\nSøker etter SaktId   : ${prefillData.penSaksnummer} "
                + "\nPreutfylling Pensjon : ${PrefillP2xxxPensjon::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        //skipper å hente persondata dersom NAVSED finnes
        if (prefillData.kanFeltSkippes("NAVSED")) {
            sed.nav = Nav()
        } else {
            //henter opp persondata
            sed.nav = prefillNav.prefill(
                    penSaksnummer = prefillData.penSaksnummer,
                    bruker = prefillData.bruker,
                    avdod = prefillData.avdod,
                    personData = personData ,
                    brukerInformasjon = prefillData.getPersonInfoFromRequestData()
            )
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
                                prefillData.andreInstitusjon
                        )
                        if (prefillData.kanFeltSkippes("PENSED")) {
                            Pensjon(kravDato = pensjon.kravDato) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
                        } else {
                            pensjon
                        }
                    }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            // TODO Should we really swallow this?
        }

        KravHistorikkHelper.settKravdato(prefillData, sed)

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        validate(prefillData)
        return prefillData.sed
    }

    private fun validate(data: PrefillDataModel) {
        when {
            data.sed.nav?.bruker?.person?.etternavn == null -> throw ValidationException("Etternavn mangler")
            data.sed.nav?.bruker?.person?.fornavn == null -> throw ValidationException("Fornavn mangler")
            data.sed.nav?.bruker?.person?.foedselsdato == null -> throw ValidationException("Fødseldsdato mangler")
            data.sed.nav?.bruker?.person?.kjoenn == null -> throw ValidationException("Kjønn mangler")
            data.sed.nav?.krav?.dato == null -> throw ValidationException("Kravdato mangler")
        }
    }
}

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
class ValidationException(message: String) : IllegalArgumentException(message)
