package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillP6000Pensjon.createPensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP6000(private val prefillNav: PrefillPDLNav,
                   private val eessiInfo: EessiInformasjon,
                   private val pensjoninformasjon: Pensjonsinformasjon) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonDataCollection): SED {
        val sedType = prefillData.sedType

        logger.info("----------------------------------------------------------"
                + "\nPreutfylling Pensjon : P6000 "
                + "\n------------------| Preutfylling [$sedType] START |------------------ ")

        //val sed = prefillData.sed

        logger.info("Henter ut lokal kontakt, institusjon (NAV Utland)")
        //prefillData.andreInstitusjon = eessiInfo.asAndreinstitusjonerItem()
        val andreInstitusjondetaljer = eessiInfo.asAndreinstitusjonerItem()
        logger.info("Andreinstitusjoner: $andreInstitusjondetaljer ")

        logger.debug("Henter opp Persondata/Gjenlevende fra TPS")
        val gjenlevende = eventuellGjenlevende(prefillData, personData.forsikretPerson)

        logger.debug("Henter opp Pensjonsdata fra PESYS")
        val pensjon = createPensjon(pensjoninformasjon, gjenlevende, prefillData.vedtakId!!, andreInstitusjondetaljer)

        logger.debug("Henter opp Persondata fra TPS")
        val nav = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData, brukerInformasjon = prefillData.getPersonInfoFromRequestData())

        logger.info("-------------------| Preutfylling [$sedType] END |------------------- ")

        return SED(
            type = sedType,
            nav = nav,
            pensjon = pensjon
        )
    }

    private fun eventuellGjenlevende(prefillData: PrefillDataModel, gjenlevendeBruker: Person?): Bruker? {
        return if (prefillData.avdod != null) {
            logger.info("          Utfylling gjenlevende (etterlatt persjon.gjenlevende)")
            prefillNav.createBruker(gjenlevendeBruker!!, null, null)
        } else null
    }
}

