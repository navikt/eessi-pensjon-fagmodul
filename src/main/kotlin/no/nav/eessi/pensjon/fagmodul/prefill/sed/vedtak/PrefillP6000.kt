package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.SakHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP6000(private val sakHelper: SakHelper,
                   private val eessiInfo: EessiInformasjon) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {
        val sedId = prefillData.getSEDid()

        logger.debug("----------------------------------------------------------"
                + "\nPreutfylling Pensjon : ${sakHelper::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        logger.info("Henter ut lokal kontakt, institusjon (NAV Utland)")
        eessiInfo.mapEssiInformasjonTilPrefillDataModel(prefillData)
        logger.info("Andreinstitusjoner: ${prefillData.andreInstitusjon} ")

        logger.debug("Henter opp Persondata/Gjenlevende fra TPS")
        val gjenlevende = sakHelper.createGjenlevende(prefillData)

        logger.debug("Henter opp Pernsjondata fra PESYS")
        val pensjon = sakHelper.createPensjon(prefillData, gjenlevende)
        sed.pensjon = pensjon

        logger.debug("Henter opp Persondata fra TPS")
        sed.nav = sakHelper.createNav(prefillData)

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return prefillData.sed
    }
}

