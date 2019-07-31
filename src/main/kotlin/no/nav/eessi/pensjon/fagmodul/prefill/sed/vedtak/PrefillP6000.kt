package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP6000(private val prefillNav: PrefillNav,
                   private val dataFromTPS: PrefillPersonDataFromTPS,
                   private val eessiInfo: EessiInformasjon,
                   penHjelper: PensjonsinformasjonHjelper) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000::class.java) }

    private var vedtakPensiondata: VedtakDataFromPEN = VedtakDataFromPEN(penHjelper)

    override fun prefill(prefillData: PrefillDataModel): SED {
        val sedId = prefillData.getSEDid()

        logger.debug("----------------------------------------------------------"
                + "\nPreutfylling NAV     : ${prefillNav::class.java} "
                + "\nPreutfylling TPS     : ${dataFromTPS::class.java} "
                + "\nPreutfylling Pensjon : ${vedtakPensiondata::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        logger.info("Henter ut lokal kontakt, institusjon (NAV Utland)")
        eessiInfo.mapEssiInformasjonTilPrefillDataModel(prefillData)
        logger.info("Andreinstitusjoner: ${prefillData.andreInstitusjon} ")

        logger.debug("Henter opp Pernsjondata fra PESYS")
        val pensjon = createPensjon(prefillData)
        sed.pensjon = pensjon

        logger.debug("Henter opp Persondata fra TPS")
        sed.nav = createNav(prefillData)

        logger.debug("Henter opp Persondata/Gjenlevende fra TPS")
        pensjon.gjenlevende = createGjenlevende(prefillData)

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return prefillData.sed
    }

    //henter persondata fra TPS fyller ut sed.nav
    private fun createNav(prefillData: PrefillDataModel): Nav {
        logger.debug("[${prefillData.getSEDid()}] Preutfylling Utfylling NAV")
        return prefillNav.prefill(prefillData)
    }

    //henter pensjondata fra PESYS fyller ut sed.pensjon
    private fun createPensjon(prefillData: PrefillDataModel): Pensjon {
        logger.debug("[${prefillData.getSEDid()}] Preutfylling Utfylling Pensjon")
        return vedtakPensiondata.prefill(prefillData)
    }

    //fylles ut kun når vi har etterlatt aktoerId og etterlattPinID.
    //noe vi må få fra PSAK. o.l
    private fun createGjenlevende(prefillData: PrefillDataModel): Bruker? {
        var gjenlevende: Bruker? = null
        if (prefillData.erGyldigEtterlatt()) {
            logger.debug("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
            gjenlevende = dataFromTPS.prefillBruker(prefillData.personNr)
        }
        logger.debug("[${prefillData.getSEDid()}] Preutfylling Utfylling Pensjon TPS")
        return gjenlevende
    }


}

