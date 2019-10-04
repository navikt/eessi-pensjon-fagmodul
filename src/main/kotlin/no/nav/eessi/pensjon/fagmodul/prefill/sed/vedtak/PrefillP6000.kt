package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillP6000Pensjon.createPensjon
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP6000(private val prefillNav: PrefillNav,
                   private val eessiInfo: EessiInformasjon,
                   private val dataFromPESYS: PensjonsinformasjonHjelper,
                   private val preutfyllingPersonFraTPS: PrefillPersonDataFromTPS) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {
        val sedId = prefillData.getSEDid()

        logger.debug("----------------------------------------------------------"
                + "\nPreutfylling Pensjon : ${PrefillP6000Pensjon::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        logger.info("Henter ut lokal kontakt, institusjon (NAV Utland)")
        prefillData.andreInstitusjon = eessiInfo.asAndreinstitusjonerItem()
        logger.info("Andreinstitusjoner: ${prefillData.andreInstitusjon} ")

        logger.debug("Henter opp Persondata/Gjenlevende fra TPS")

        val gjenlevende = if (prefillData.erGyldigEtterlatt()) {
            prefillNav.createBruker(preutfyllingPersonFraTPS.hentBrukerFraTPS(prefillData.personNr), null, null)
        } else null

        logger.debug("Henter opp Pernsjondata fra PESYS")
        sed.pensjon = createPensjon(dataFromPESYS, gjenlevende, prefillData.vedtakId, prefillData.andreInstitusjon)

        logger.debug("Henter opp Persondata fra TPS")
        sed.nav = prefillNav.prefill(prefillData)

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return prefillData.sed
    }
}

