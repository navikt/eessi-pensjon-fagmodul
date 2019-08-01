package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * preutfylling av NAV-P2000 SED for søknad krav om alderpensjon
 */
class PrefillP2000(private val sakPensiondata: KravDataFromPEN) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2000::class.java) }


    override fun prefill(prefillData: PrefillDataModel): SED {
        val sedId = prefillData.getSEDid()

        prefillData.saktype = KravDataFromPEN.KSAK.ALDER.name
        logger.debug("----------------------------------------------------------"
                + "\nSaktype              : ${prefillData.saktype} "
                + "\nSøker etter SaktId   : ${prefillData.penSaksnummer} "
                + "\nPreutfylling Pensjon : ${sakPensiondata::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        //skipper å hente persondata dersom NAVSED finnes
        if (prefillData.kanFeltSkippes("NAVSED")) {
            sed.nav = Nav()
            //henter opp persondata
        } else {
            sed.nav = sakPensiondata.createNav(prefillData)
        }

        //skipper å henter opp pensjondata hvis PENSED finnes
        sakPensiondata.hentPensjonsdata(prefillData, sed)

        sakPensiondata.settKravdato(prefillData, sed)

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return prefillData.sed
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