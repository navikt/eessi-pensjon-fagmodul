package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.KravDataFromPEN
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP6000(private val sakPensiondata: KravDataFromPEN,
                   private val eessiInfo: EessiInformasjon) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {
        val sedId = prefillData.getSEDid()

        logger.debug("----------------------------------------------------------"
                + "\nPreutfylling Pensjon : ${sakPensiondata::class.java} "
                + "\n------------------| Preutfylling [$sedId] START |------------------ ")

        val sed = prefillData.sed

        logger.info("Henter ut lokal kontakt, institusjon (NAV Utland)")
        eessiInfo.mapEssiInformasjonTilPrefillDataModel(prefillData)
        logger.info("Andreinstitusjoner: ${prefillData.andreInstitusjon} ")

        logger.debug("Henter opp Pernsjondata fra PESYS")
        val pensjon = sakPensiondata.createPensjon(prefillData)
        sed.pensjon = pensjon

        logger.debug("Henter opp Persondata fra TPS")
        sed.nav = sakPensiondata.createNav(prefillData)

        logger.debug("Henter opp Persondata/Gjenlevende fra TPS")
        pensjon.gjenlevende = sakPensiondata.createGjenlevende(prefillData)

        logger.debug("-------------------| Preutfylling [$sedId] END |------------------- ")
        return prefillData.sed
    }
}

