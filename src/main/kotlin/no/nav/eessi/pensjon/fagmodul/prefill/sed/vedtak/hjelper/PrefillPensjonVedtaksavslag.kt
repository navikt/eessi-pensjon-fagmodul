package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper

import no.nav.eessi.pensjon.fagmodul.sedmodel.AvslagbegrunnelseItem
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PrefillPensjonVedtaksavslag {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjonVedtaksavslag::class.java) }

    fun sjekkForVilkarsvurderingListeHovedytelseellerAvslag(pendata: Pensjonsinformasjon): Boolean {
        try {
            val hovedytelseAvslag = pendata.vilkarsvurderingListe.vilkarsvurderingListe.first()
            if (hovedytelseAvslag.resultatHovedytelse == "AVSL" || hovedytelseAvslag.avslagHovedytelse == "AVSL") {
                return true
            }
        } catch (ex: Exception) {
            logger.error("Ingen vilkarsvurderingListe, sjekk på AVSL")
        }
        return false
    }

    /**
     * /4.1.13.1 - 4.1.13.2.1
     */
    fun createAvlsagsBegrunnelseItem(pendata: Pensjonsinformasjon): List<AvslagbegrunnelseItem>? {
        logger.debug("4.1.13        AvlsagsBegrunnelseItem")

        val avslagbegrunnelse = createAvlsagsBegrunnelse(pendata)

        val item = listOf(AvslagbegrunnelseItem(

                //4.1.13.1
                begrunnelse = avslagbegrunnelse,

                //4.1.13.2 Other - Nei
                annenbegrunnelse = null
        ))

        if (avslagbegrunnelse == null)
            return null

        return item
    }

    /**
     *  4.1.13.1 - Rejection reasons
     */
    fun createAvlsagsBegrunnelse(pendata: Pensjonsinformasjon): String? {
        logger.debug("4.1.13.1          AvlsagsBegrunnelse")

        if (pendata.vilkarsvurderingListe == null || pendata.vilkarsvurderingListe.vilkarsvurderingListe == null) {
            return null
        }
        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        val erAvslagVilkarsproving = VedtakPensjonDataHelper.hentVilkarsResultatHovedytelse(pendata) == "AVSL"

        val harBoddArbeidetUtland = VedtakPensjonDataHelper.harBoddArbeidetUtland(pendata)
        val erTrygdetidListeTom = pendata.trygdetidListe.trygdetidListe.isEmpty()

        val erLavtTidligUttak = VedtakPensjonDataHelper.isVilkarsvurderingAvslagHovedytelseSamme("LAVT_TIDLIG_UTTAK", pendata)
        val erUnder62 = VedtakPensjonDataHelper.isVilkarsvurderingAvslagHovedytelseSamme("UNDER_62", pendata)
        val erIkkeMottattDok = "IKKE_MOTTATT_DOK" == VedtakPensjonDataHelper.hentVilkarsProvingAvslagHovedYtelse(pendata)

        //UFOREP
        val erForutMedlem = "FORUT_MEDL" == VedtakPensjonDataHelper.hentVilkarsvurderingUforetrygd(pendata).unntakForutgaendeMedlemskap
        val erHensArbrettTiltak = "HENS_ARBRETT_TILTAK" == VedtakPensjonDataHelper.hentVilkarsvurderingUforetrygd(pendata).hensiktsmessigArbeidsrettedeTiltak
        val erHensiktmessigBeh = "HENSIKTSMESSIG_BEH" == VedtakPensjonDataHelper.hentVilkarsvurderingUforetrygd(pendata).hensiktsmessigBehandling
        val erNedsattInntEvne = "NEDSATT_INNT_EVNE" == VedtakPensjonDataHelper.hentVilkarsvurderingUforetrygd(pendata).nedsattInntektsevne
        val erAlder = "ALDER" == VedtakPensjonDataHelper.hentVilkarsvurderingUforetrygd(pendata).alder

        if (KSAK.UFOREP != sakType && harBoddArbeidetUtland && erAvslagVilkarsproving) {
            //pkt1 og pkt.9
            if (erTrygdetidListeTom)
                return "01"

            //pkt.2 og pkt.10
            if (VedtakPensjonDataHelper.erTrygdeTid(pendata))
                return "02"

            if (erLavtTidligUttak)
                return "03"

            if (erUnder62)
                return "06"
        }

        if (KSAK.UFOREP == sakType && harBoddArbeidetUtland) {
            //hentVilkarsvurderingUforetrygd
            if (erAlder && erAvslagVilkarsproving)
                return "03"

            if ((erHensiktmessigBeh || erHensArbrettTiltak) && erAvslagVilkarsproving)
                return "08"

            if (erNedsattInntEvne && erAvslagVilkarsproving)
                return "04"

            if (VedtakPensjonDataHelper.erTrygdeTid(pendata) && erForutMedlem && erAvslagVilkarsproving)
                return "02"
            //pkt.5
            if (pendata.trygdetidListe.trygdetidListe.isEmpty() && erForutMedlem && erAvslagVilkarsproving)
                return "01"
        }

        //siste..   pendata.sak.sakType alle..
        if (harBoddArbeidetUtland && erIkkeMottattDok && erAvslagVilkarsproving)
            return "07"

        logger.debug("              -- Ingen avslagsbegrunnelse")
        return null
    }
}