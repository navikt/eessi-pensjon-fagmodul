package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper

import no.nav.eessi.pensjon.eux.model.sed.ReduksjonItem
import no.nav.eessi.pensjon.eux.model.sed.VirkningsdatoItem
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.hentGrunnPersjon
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.hentTilleggsPensjon
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PrefillPensjonReduksjon {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjonReduksjon::class.java) }

    //5.1
    fun createReduksjon(pendata: Pensjonsinformasjon): List<ReduksjonItem>? {
        logger.debug("PrefillPensjonReduksjon")
        logger.debug("5.1       Reduksjon")

        val reduksjon = ReduksjonItem(
                //5.1.1. - $pensjon.reduksjon[x].type
                type = createReduksjonType(pendata),

                //5.1.4 -- $pensjon.sak.reduksjon[x].artikkeltype
                artikkeltype = createReduksjonArtikkelType(pendata),

                //5.1.5 - Nei
                virkningsdato = createReduksjonDato()

        )
        if (reduksjon.type == null && reduksjon.artikkeltype == null) {
            return null
        }

        return listOf(reduksjon)

    }

    //5.1.5 (nei)
    private fun createReduksjonDato(): List<VirkningsdatoItem>? {
        logger.debug("5.1.5         ReduksjonDato  (nei)")
        //Nei
        return null
    }

    //5.1.1
    /*
        HVIS Sakstype er Uføretrygd
        OG Vilkårsprøving Detaljer Trygdeavtale
        Skal artikkel 10 anvendes på trygdetid
        SÅ skal det hukes av for "[02] Ytelse som fastsettes på grunnlag av en godskrevet periode"

        HVIS Sakstype er Gjenlevendepensjon
        OG Vilkårsprøving Detaljer Trygdeavtale Skal artikkel 10 anvendes på grunnpensjon
        OG/ELLER  Skal artikkel 10 anvendes på tilleggspensjon
        SÅ skal det hukes av for "[02] Ytelse som fastsettes på grunnlag av en godskrevet periode"

        HVIS Sakstype er barnepensjon
        OG Vilkårsprøving Detaljer Trygdeavtale Skal artikkel 10 anvendes på grunnpensjon
        SÅ skal det hukes av for "[02] Ytelse som fastsettes på grunnlag av en godskrevet periode"
     */
    private fun createReduksjonType(pendata: Pensjonsinformasjon): String? {
        logger.debug("5.1.1         ReduksjonType")
        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        if (sakType == KSAK.UFOREP && hentTilleggsPensjon(pendata))
            return "02"
        if (sakType == KSAK.GJENLEV && hentGrunnPersjon(pendata) || hentTilleggsPensjon(pendata))
            return "02"
        if (sakType == KSAK.BARNEP && hentGrunnPersjon(pendata))
            return "02"

        return null
    }

    //5.1.4
    /*
        Hvis sakstype er Uføretrygd,
        OG det er svart JA i  Vilkårsprøving/Detaljer trygdeavtale/Nordisk trygdeavtale/Skal artikkel 10 anvendes på trygdetid,
        SÅ skal det hukes for «[02] 883/2004: Art. 54(2)b»

        HVIS Sakstype er Gjenlevendepensjon
        OG Vilkårsprøving Detaljer Trygdeavtale Skal artikkel 10 anvendes på grunnpensjon
        OG/ELLER  Skal artikkel 10 anvendes på tilleggspensjon
        SÅ skal det hukes av for "[02] 883/2004: Art. 54(2)b»

        HVIS Sakstype er barnepensjon
        OG Detaljer trygdeavtale Skal artikkel 10 anvendes på grunnpensjon
        SÅ skal det hukes av for "[02] 883/2004: Art. 54(2)b»
     */
    private fun createReduksjonArtikkelType(pendata: Pensjonsinformasjon): String? {
        logger.debug("5.1.4         ReduksjonArtikkelType")
        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        if (sakType == KSAK.UFOREP && hentTilleggsPensjon(pendata))
            return "02"
        if (sakType == KSAK.GJENLEV && hentGrunnPersjon(pendata) || hentTilleggsPensjon(pendata))
            return "02"
        if (sakType == KSAK.BARNEP && hentGrunnPersjon(pendata))
            return "02"

        return null
    }


}
