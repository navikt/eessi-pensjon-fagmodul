package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper

import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonVedtaksavslag.createAvlsagsBegrunnelseItem
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonVedtaksavslag.sjekkForVilkarsvurderingListeHovedytelseellerAvslag
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonVedtaksbelop.createBeregningItemList
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonVedtaksbelop.createEkstraTilleggPensjon
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.hentVilkarsResultatHovedytelse
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.hentVinnendeBergeningsMetode
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.isMottarMinstePensjonsniva
import no.nav.eessi.pensjon.fagmodul.sedmodel.Grunnlag
import no.nav.eessi.pensjon.fagmodul.sedmodel.Opptjening
import no.nav.eessi.pensjon.fagmodul.sedmodel.VedtakItem
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sakalder.V1SakAlder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PrefillPensjonVedtak {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjonVedtak::class.java) }

    /**
     *  4.1
     */
    fun createVedtakItem(pendata: Pensjonsinformasjon): VedtakItem {
        logger.debug("PrefillPensjonReduksjon")
        logger.debug("4.1       VedtakItem")

        return VedtakItem(

                //4.1.1  $pensjon.vedtak[x].type
                type = createVedtakTypePensionWithRule(pendata),

                //4.1.2  $pensjon.vedtak[x].basertPaa
                basertPaa = createVedtakGrunnlagPentionWithRule(pendata),

                //4.1.3.1 $pensjon.vedtak[x].basertPaaAnnen
                basertPaaAnnen = createVedtakAnnenTypePentionWithRule(pendata),

                //4.1.4 $pensjon.vedtak[x].resultat
                resultat = createTypeVedtakPentionWithRule(pendata),

                //4.1.5 $pensjon.vedtak[x].artikkel
                artikkel = null,

                //4.1.6  $pensjon.vedtak[x].virkningsdato
                virkningsdato = pendata.vedtak.virkningstidspunkt.simpleFormat(),

                //4.1.7 -- $pensjon.vedtak[x].beregning[x]..
                beregning = createBeregningItemList(pendata),

                //4.1.8  $pensjon.vedtak[x].kjoeringsdato -- datoFattetVedtak?
                kjoeringsdato = null,

                //4.1.9
                ukjent = createEkstraTilleggPensjon(pendata),

                //4.1.10 - 4.1.12 $pensjon.vedtak[x].grunnlag
                grunnlag = createGrunnlag(pendata),

                //Na
                mottaker = null,      //ikke i bruk?
                trekkgrunnlag = null,  //ikke i bruk?

                //4.1.13.2 Nei
                begrunnelseAnnen = null,

                //4.1.13.1 -- 4.1.13.2.1 - $pensjon.vedtak[x].avslagbegrunnelse[x].begrunnelse
                avslagbegrunnelse = createAvlsagsBegrunnelseItem(pendata),

                //4.1.14.1 // Ikke i bruk
                delvisstans = null
        )

    }


    /**
     * 4.1.1 - vedtaktype
     *
     * 01 - Old age
     * 02 - Invalidity
     * 03 - Survivors
     * 06 - Early Old age
     *
     * 04 og 05 benyttes ikke
     */
    fun createVedtakTypePensionWithRule(pendata: Pensjonsinformasjon): String {
        //v1sak fra PESYS
        val v1sak = pendata.sakAlder as V1SakAlder

        //Er pensjon før 67 ?
        val pensjonUttakTidligereEnn67 = v1sak.isUttakFor67

        //type fra K_SAK_T
        val type = v1sak.sakType

        val sakType = KSAK.valueOf(type)
        logger.debug("4.1.1         VedtakTypePension")

//        4.1.[1].1. Type pensjon
//        [01] Alderspensjon
//        [02] Uførhet
//        [03]Etterlatte
//        [04]Delvis uførhet
//        [05] 100% uførhet
//        [06] Førtidspensjon
        return when (sakType) {
            KSAK.ALDER -> {
                when (pensjonUttakTidligereEnn67) {
                    true -> "06"
                    else -> "01"
                }
            }
            KSAK.UFOREP -> "02"
            KSAK.BARNEP, KSAK.GJENLEV -> "03"
        }
    }

    /**
     * 4.1.2 vedtak
     *
     * [01] Based on residence
     * [02] Based on working
     * [99] Other
     *
     */
    private fun createVedtakGrunnlagPentionWithRule(pendata: Pensjonsinformasjon): String? {
        //TODO Det må lages flere regler for UT og for etterlattepensjon

        logger.debug("4.1.2         VedtakGrunnlagPention")

        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)
        logger.debug("              Saktype: $sakType")

        //hvis avslag returner vi tomt verdi
        if (sjekkForVilkarsvurderingListeHovedytelseellerAvslag(pendata)) return null

        return if (sakType == KSAK.BARNEP) "99"
        else {
            when (isMottarMinstePensjonsniva(pendata)) {
                true -> "01"
                false -> "02"
            }
        }
    }

    /**
     *  4.1.3.1 (other type)
     */
    private fun createVedtakAnnenTypePentionWithRule(pendata: Pensjonsinformasjon): String? {

        logger.debug("4.1.3.1       VedtakAnnenTypePention")
        if (createVedtakGrunnlagPentionWithRule(pendata) == "99") {

            //TODO: Regler for annen bergeningtekst.
            return "Ytelsen er beregnet etter regler for barnepensjon"

        }
        return null
    }

    /**
     *  4.1.4
     *
     *  HVIS vedtaksresultat er Innvilgelse, OG sakstype IKKE er uføretrygd og kravtype er Førstegangsbehandling Norge/utland ELLER Mellombehandling SÅ skal det hukes for «[01] Award»
     *  HVIS vedtaksresultat er Avslag,  SÅ skal det automatisk hukes for «[02] Rejection»
     *  HVIS kravtype er Revurdering, SÅ skal det hukes for «[03] New calculation / recalculation»
     *  HVIS sakstype er Uføretrygd, OG kravtype er Førstegangsbehandling Norge/utland ELLER Mellombehandling, SÅ skal det hukes for «[04] Provisional or advance payment»
     *  Opphør - må håndteres Se pkt 6.2
     */
    private fun createTypeVedtakPentionWithRule(pendata: Pensjonsinformasjon): String? {
        logger.debug("4.1.4         TypeVedtakPention (vedtak.resultat")

        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)
        val kravGjelder = pendata.vedtak.kravGjelder

        //TODO: finner man vedtaksresultat?
        val vedtaksresultat = hentVilkarsResultatHovedytelse(pendata)
        logger.debug("              vedtaksresultat: $vedtaksresultat")

        val erAvslag = vedtaksresultat == "AVSL"
        val erInnvilgelse = vedtaksresultat == "INNV"

        val erForsteGangBehandlingNorgeUtland = "F_BH_MED_UTL" == kravGjelder
        val erForsteGangBehandlingBosattUtland = "F_BH_BO_UTL" == kravGjelder
        val erMellombehandling = "MELLOMBH" == kravGjelder
        val erRevurdering = kravGjelder == "REVURD"

//        4.1.[1].4. Type vedtak
//        [01] Innvilgelse
//        [02] Avslag
//        [03] Ny beregning / omregning
//        [04] Foreløpig utbetaling eller forskudd
        if (KSAK.UFOREP != sakType && erInnvilgelse
                && (erForsteGangBehandlingNorgeUtland || erMellombehandling || erForsteGangBehandlingBosattUtland)) {
                return "01"
        }
        if (erAvslag)
            return "02"

        if (erRevurdering)
            return "03"

        if (KSAK.UFOREP == sakType && (erForsteGangBehandlingNorgeUtland || erMellombehandling))
            return "04"

        if (KSAK.UFOREP == sakType && erForsteGangBehandlingBosattUtland)
            return "01"

        logger.debug("              Ingen verdier funnet. (null)")
        return null
    }

    /**
     * 4.1.10 - 4.1.12
     */
    private fun createGrunnlag(pendata: Pensjonsinformasjon): Grunnlag {

        logger.debug("4.1.10        Grunnlag")

        if (sjekkForVilkarsvurderingListeHovedytelseellerAvslag(pendata)) return Grunnlag()

        return Grunnlag(

                //4.1.10 - Nei
                medlemskap = null,

                //4.1.11
                opptjening = Opptjening(forsikredeAnnen = createOpptjeningForsikredeAnnen(pendata)),

                //4.1.12   $pensjon.vedtak[x].grunnlag.framtidigtrygdetid
                framtidigtrygdetid = createFramtidigtrygdetid(pendata)
        )
    }

    /**
     *  4.1.12. Credited period
     *
     *  [1] Yes
     *  [0] No
     */
    private fun createFramtidigtrygdetid(pendata: Pensjonsinformasjon): String {
        logger.debug("4.1.12        Framtidigtrygdetid")

        return when (KSAK.valueOf(pendata.sakAlder.sakType)) {
            KSAK.ALDER -> "0"
            else -> {
                when (hentVinnendeBergeningsMetode(pendata)) {
                    "FOLKETRYGD" -> "1"

                    else -> "0"
                }
            }
        }
    }

    /**
     *  4.1.11 - OpptjeningForsikredeAnnen
     *
     * [01] - Nei
     * [02] - Delvis
     * [03] - Fullstendig
     *
     */
    private fun createOpptjeningForsikredeAnnen(pendata: Pensjonsinformasjon): String? {
        logger.debug("4.1.11        OpptjeningForsikredeAnnen")

        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        val resultatGjenlevendetillegg = pendata.vilkarsvurderingListe?.vilkarsvurderingListe?.get(0)?.resultatGjenlevendetillegg
                ?: ""
        val erUtenGjenlevendetillegg = resultatGjenlevendetillegg == ""
        val erMedGjenlevendetillegg = resultatGjenlevendetillegg != ""
        val vinnendeMetode = hentVinnendeBergeningsMetode(pendata)


        if ((KSAK.ALDER == sakType || KSAK.UFOREP == sakType) && erUtenGjenlevendetillegg)
            return "01"

        if (KSAK.ALDER == sakType && erMedGjenlevendetillegg && vinnendeMetode != "RETT_TIL_GJT")
            return "01"

        if (KSAK.ALDER == sakType && erMedGjenlevendetillegg && "RETT_TIL_GJT" == vinnendeMetode)
            return "02"

        if (KSAK.GJENLEV == sakType || KSAK.BARNEP == sakType) {
            return "03"
        }

        return null
    }
}
