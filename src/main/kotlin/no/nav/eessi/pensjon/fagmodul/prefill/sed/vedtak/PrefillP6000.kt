package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillP6000Pensjon.createPensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP6000(private val prefillNav: PrefillNav,
                   private val eessiInfo: EessiInformasjon,
                   private val pensjoninformasjon: Pensjonsinformasjon) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonData): SED {
        val sedId = prefillData.getSEDType()

        logger.debug("----------------------------------------------------------"
                + "\nPreutfylling Pensjon : ${PrefillP6000Pensjon::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        logger.info("Henter ut lokal kontakt, institusjon (NAV Utland)")
        prefillData.andreInstitusjon = eessiInfo.asAndreinstitusjonerItem()
        logger.info("Andreinstitusjoner: ${prefillData.andreInstitusjon} ")

        logger.debug("Henter opp Persondata/Gjenlevende fra TPS")
        val gjenlevende = eventuellGjenlevende(prefillData, personData.forsikretPerson)

        logger.debug("Henter opp Pensjonsdata fra PESYS")
        sed.pensjon = createPensjon(pensjoninformasjon, gjenlevende, prefillData.vedtakId!!, prefillData.andreInstitusjon)

        logger.debug("Henter opp Persondata fra TPS")
        sed.nav = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData, brukerInformasjon = prefillData.getPersonInfoFromRequestData())

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return sed
    }

    private fun eventuellGjenlevende(prefillData: PrefillDataModel, gjenlevendeBruker: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker?): Bruker? {
        return if (prefillData.avdod != null) {
            logger.info("          Utfylling gjenlevende (etterlatt persjon.gjenlevende)")
            prefillNav.createBruker(gjenlevendeBruker!!, null, null)
        } else null
    }
}

