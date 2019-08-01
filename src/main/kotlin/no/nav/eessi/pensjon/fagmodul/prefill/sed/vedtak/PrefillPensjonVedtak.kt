package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.sedmodel.AvslagbegrunnelseItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.BeloepBrutto
import no.nav.eessi.pensjon.fagmodul.sedmodel.BeregningItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Grunnlag
import no.nav.eessi.pensjon.fagmodul.sedmodel.Opptjening
import no.nav.eessi.pensjon.fagmodul.sedmodel.Periode
import no.nav.eessi.pensjon.fagmodul.sedmodel.Ukjent
import no.nav.eessi.pensjon.fagmodul.sedmodel.VedtakItem
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sakalder.V1SakAlder
import no.nav.pensjon.v1.ytelsepermaaned.V1YtelsePerMaaned
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillPensjonVedtak : VedtakPensjonData() {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjonVedtak::class.java) }

    init {
        logger.debug("PrefillPensjonReduksjon")
    }

    //4.1
    fun createVedtakItem(pendata: Pensjonsinformasjon): VedtakItem {

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
                artikkel = createArtikkelInnvilgelseAvYtelseWithRule(pendata),

                //4.1.6  $pensjon.vedtak[x].virkningsdato
                virkningsdato = pendata.vedtak.virkningstidspunkt.simpleFormat(),

                //4.1.7 -- $pensjon.vedtak[x].beregning[x]..
                beregning = createBeregningItemList(pendata),

                //4.1.8  $pensjon.vedtak[x].kjoeringsdato -- datoFattetVedtak?
                kjoeringsdato = pendata.vedtak?.datoFattetVedtak?.simpleFormat(),

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

    //4.1.1
    /*
        HVIS søkt pensjon er Alderspensjon etter fylt 67 år, SÅ skal det vises «Old age»
        HVIS søkt pensjon er Alderspensjon før fylt 67 år, SÅ skal det vises «Early Old age»
        HVIS søkt pensjon er Uføretrygd, SÅ skal det vises «Invalidity»
        HVIS søkt pensjon er uføretrygd med gjenlevenderett, SÅ skal det vises «Invalidity»
        HVIS søkt pensjon er gjenlevendepensjon, SÅ skal det vises «Survivors»
        HVIS søkt pensjon er Alderspensjon etter fylt 67 år og med gjenlevenderettigheter, SÅ skal det vises «Old age»
        Hvis søkt pensjon er barnepensjon,  , SÅ skal det vises «Survivors»
     */
    fun createVedtakTypePensionWithRule(pendata: Pensjonsinformasjon): String {
        //04 og 05 benyttes ikke

        //v1sak fra PESYS
        val v1sak = pendata.sakAlder as V1SakAlder

        //pensjon før 67 (ja/nei - sann/usann)
        val pensjonUttakTidligereEnn67 = v1sak.isUttakFor67

        //type fra K_SAK_T
        val type = v1sak.sakType


        val sakType = KSAK.valueOf(type)
        logger.debug("4.1.1         VedtakTypePension")

        return when (sakType) {

            KSAK.ALDER -> {
                when (pensjonUttakTidligereEnn67) {
                    true -> "06" //"Førdtispensjon" //06
                    else -> "01"  //""Alderspensjon" //01
                }
            }

            KSAK.UFOREP -> "02" // "Uførhet" //"02"

            KSAK.BARNEP, KSAK.GJENLEV -> "03" // "Etterlatte" //"03"

        }
    }

    //4.1.2
    /*
        HVIS sakstype er Alderspensjon OG beregnet pensjon utgjør minste pensjonsnivå,
        SÅ skal det hukes for [01] Based on residence

        HVIS sakstype er Alderspensjon OG beregnet pensjon er over minste pensjonsnivå,
        SÅ skal det hukes for [02] Based on working

        HVIS sakstype er Uføretrygd og beregnet ytelse er på minstenivå (minsteytelse),
        SÅ skal det hukes for [01] Based on residence (hva hvis det også er egenopptjent UT?)

        HVIS sakstype er Uføretrygd OG beregnet ytelse er  ikke minsteytelse OG bruker ikke er ung ufør,
        SÅ skal det hukes for [02] Based on working

        HVIS sakstype er Uføretrygd OG bruker er ung ufør, OG ja for minsteytelse
        SÅ skal det hukes for [99] Other

        HVIS sakstype er Uføretrygd OG bruker er ung ufør, OG nei for minsteytelse
        SÅ skal det hukes for [02] Based on working

        HVIS sakstype er Gjenlevendepensjon og beregnet ytelse er på minstenivå,
        SÅ skal det hukes for [01] Based on residence

        HVIS sakstype er Gjenlevendepensjon og beregnet ytelse er over minstenivå,
        SÅ skal det hukes for [02] Based on work

        HVIS sakstype er Barnepensjon OG barnet er ikke foreldreløs,
        SÅ skal det hukes for [99] Other

        Det må lages flere regler for UT og for etterlattepensjon
     */
    //4.1.2 vedtak
    private fun createVedtakGrunnlagPentionWithRule(pendata: Pensjonsinformasjon): String {
        //01-residece, 02-working, 99-other --> 4.1.3.1 --> other

        logger.debug("4.1.2         VedtakGrunnlagPention")

        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)
        logger.debug("              KSAK: $sakType")

        return when (sakType) {
            KSAK.ALDER -> {
                when (isMottarMinstePensjonsniva(pendata)) {
                    true -> "01"
                    false -> "02"
                }
            }
            KSAK.UFOREP -> {
                when (isMottarMinstePensjonsniva(pendata)) {
                    true -> "01"
                    false -> "02"
                }
            }
            KSAK.GJENLEV -> {
                when (isMottarMinstePensjonsniva(pendata)) {
                    true -> "01"
                    false -> "02"
                }
            }
            KSAK.BARNEP -> {
                when (isForeldelos(pendata)) {
                    false -> "99"
                    true -> "99"
                }
            }
        }
    }

    //4.1.3.1 (other type)
    private fun createVedtakAnnenTypePentionWithRule(pendata: Pensjonsinformasjon): String? {

        logger.debug("4.1.3.1       VedtakAnnenTypePention")
        if (createVedtakGrunnlagPentionWithRule(pendata) == "99") {

            //TODO: Regler for annen bergeningtekst.
            return "Ytelsen er beregnet etter regler for barnepensjon"

        }
        return null
    }

    //4.1.4
    /*
        HVIS vedtaksresultat er Innvilgelse, OG sakstype IKKE er uføretrygd og kravtype er Førstegangsbehandling Norge/utland ELLER Mellombehandling SÅ skal det hukes for «[01] Award»
        HVIS vedtaksresultat er Avslag,  SÅ skal det automatisk hukes for «[02] Rejection»
        HVIS kravtype er Revurdering, SÅ skal det hukes for «[03] New calculation / recalculation»
        HVIS sakstype er Uføretrygd, OG kravtype er Førstegangsbehandling Norge/utland ELLER Mellombehandling, SÅ skal det hukes for «[04] Provisional or advance payment»
        Opphør - må håndteres Se pkt 6.2
     */
    private fun createTypeVedtakPentionWithRule(pendata: Pensjonsinformasjon): String? {
        //01> invillg , 02> avslag , 03> ny beregning , 04> foreløping utbeteling
        logger.debug("4.1.4         TypeVedtakPention (vedtak.resultat")

        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)
        val kravGjelder = pendata.vedtak.kravGjelder

        //TODO: finner man vedtaksresultat?
        val vedtaksresultat = hentVilkarsResultatHovedytelse(pendata)
        logger.debug("              vedtaksresultat: $vedtaksresultat")

        val erAvslag = vedtaksresultat == "AVSLAG"
        val erInnvilgelse = vedtaksresultat == "INNV"

        //PESYS kodeverk K_KRAV_SAK_FULL
        val erForsteGangBehandlingNorgeUtland = "F_BH_MED_UTL" == kravGjelder
        val erForsteGangBehandlingBosattUtland = "F_BH_BO_UTL" == kravGjelder
        val erMellombehandling = "MELLOMBH" == kravGjelder
        val erRevurdering = kravGjelder == "REVURD"

        if (KSAK.UFOREP != sakType) {
            if (erInnvilgelse && (erForsteGangBehandlingNorgeUtland || erMellombehandling || erForsteGangBehandlingBosattUtland))
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

    //4.1.5
    /*
        HVIS sakstype er alderspensjon,
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving] OG det er svart JA på «Minst tre års trygdetid» i Inngang og eksport,
        SÅ skal det hukes for [01] 883/2004: Art. 52(1)(a)

        HVIS sakstype er alderspensjon,
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving] OG det er svart NEI på «Minst tre års trygdetid» i Inngang og eksport, OG det er svart JA på "unntak fra forutgående trygdetid" SÅ skal det hukes for [01] 883/2004: Art. 52(1)(a)

        HVIS sakstype er alderspensjon, OG det er svart JA på «Oppfylt ved sammenlegging» (kap 19 og/eller kap 20) i Inngang og eksport,
        SÅ skal det hukes for [02] 883/2004: Art. 52(1)(b)

        HVIS sakstype er Uføretrygd,
        OG Kravtype er Førstegangsbehandling Norge/Utland [skjermbilde Krav],
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving],
        SÅ skal punkt 4.1.4. Decision type hukes for «[04] Provisional or advance payment»,
        OG punkt 4.1.5. Award benefit article hukes for «[01] 883/2004: Art. 52(1)(a)»

        HVIS sakstype er Uføretrygd,
        OG Kravtype er Mellombehandling Norge/Utland [skjermbilde Krav],
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving],
        SÅ skal punkt 4.1.4. Decision type hukes for «[04] Provisional or advance payment»,
        OG punkt 4.1.5. Award benefit article hukes for «[02] 883/2004: Art. 52(1)(b)»

        HVIS sakstype er Uføretrygd,
        OG Kravtype er Førstegangsbehandling Bosatt Utland [skjermbilde Krav],
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving],
        OG oppfyller ETT av kravene i tabell Medlemskap i folketrygden etter folketrygdloven § 12-2 [Vilkårsprøving Inngang og eksport],
        SÅ skal punkt 4.1.5. Award benefit article hukes for «[01] 883/2004: Art. 52(1)(a)»

        HVIS sakstype er Uføretrygd,
        OG Kravtype er Førstegangsbehandling Bosatt Utland [skjermbilde Krav],
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving],
        OG oppfyller INGEN av kravene i tabell Medlemskap i folketrygden etter folketrygdloven § 12-2 [Vilkårsprøving Inngang og eksport],
        SÅ skal punkt 4.1.5. Award benefit article hukes for «[02] 883/2004: Art. 52(1)(b)»

        HVIS sakstype er Uføretrygd,
        OG Kravtype er Sluttbehandling Norge Utland [skjermbilde Krav],
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving],
        OG Vedtakssammendrag viser vinnende beregning «Nasjonal» [Vedtakssammendrag],
        SÅ skal punkt 4.1.5. Award benefit article hukes for «[01]

        HVIS sakstype er Uføretrygd,
        OG Kravtype er Sluttbehandling Norge Utland [skjermbilde Krav],
        OG Resultat er Innvilgelse [skjermbilde Vilkårsprøving],
        OG Vedtakssammendrag viser vinnende beregning «EØS pro rata» [Vedtakssammendrag],
        SÅ skal punkt 4.1.5. Award benefit article hukes for «[02]
     */
    private fun createArtikkelInnvilgelseAvYtelseWithRule(pendata: Pensjonsinformasjon): String? {
        //01=> 883/2004 art 52 A, 02=> 883/2004 art 52 B, 03=> 883/2004 art 57 2, 04=> 883/2004 art 57 3, 05=> 833/2004 art 60 2
        logger.debug("4.1.5         ArtikkelInnvilgelseAvYtelse (vedtak.artikkel) TODO: Må fyllesut manuelt!! sak: ${pendata.sakAlder.sakType}")
        return null

        //TODO regler her er ikke 100% det må uthentes bedre svar fra PESYS.
    }


    //4.1.7 --
    fun createBeregningItemList(pendata: Pensjonsinformasjon): List<BeregningItem> {

        val ytelsePerMaaned = pendata.ytelsePerMaanedListe.ytelsePerMaanedListe
                .asSequence().sortedBy { it.fom.toGregorianCalendar() }.toMutableList()


        val resultList = mutableListOf<BeregningItem>()
        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        ytelsePerMaaned.forEach {
            resultList.add(createBeregningItem(it, sakType))
        }

        return resultList
    }

    private fun createBeregningItem(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): BeregningItem {
        logger.debug("4.1.7         BeregningItem (Repeterbart)")

        //val belop = ytelsePrMnd.belop.toString()

        return BeregningItem(
                //4.1.7.1 -- 4.1.7.2
                periode = createBeregningItemPeriode(ytelsePrMnd),

                //4.1.7.3.2 - netAmount -- Nei?
                beloepNetto = null,

                beloepBrutto = BeloepBrutto(
                        //4.1.7.3.1. Gross amount
                        beloep = createBelop(ytelsePrMnd, sakType),

                        //4.1.7.3.3. Gross amount of basic pension
                        ytelseskomponentGrunnpensjon = createYtelseskomponentGrunnpensjon(ytelsePrMnd, sakType),

                        //4.1.7.3.4. Gross amount of supplementary pension
                        ytelseskomponentTilleggspensjon = createYtelseskomponentTilleggspensjon(ytelsePrMnd, sakType),

                        //??? -- not in use?
                        ytelseskomponentAnnen = null
                ),

                //4.1.7.4 Currency automatisk hukes for "NOK" norway krone.
                valuta = "NOK",

                //4.1.7.5              //03 - montly 12/year
                utbetalingshyppighet = "03", //TODO "03" skal være korrekte!!

                //4.1.7.6.1     - Nei
                utbetalingshyppighetAnnen = null
        )
    }

    /**
    Hvis Annet en Alderpensjon skal YtelsePerMaaned belop benytes.

    Hvis Uførep med UT_ORDINER og eller UT_TBF eller UT_TBS er større en belop
    skal denne sum benyttes hvis ikke skal belop benyttes.
     */
    //4.1.7.3.1
    private fun createBelop(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): String {
        logger.debug("4.1.7.3.1         Gross amount")
        //4.1.7.3.1. Gross amount
        val belop = ytelsePrMnd.belop

        if (KSAK.UFOREP == sakType) {
            val uforUtOrd = hentYtelseskomponentBelop("UT_ORDINER,UT_TBF,UT_TBS", ytelsePrMnd)
            //logger.debug("              uforep UT_ORDINER belop : $uforUtOrd  belop : $belop")
            if (uforUtOrd > belop) {
                return uforUtOrd.toString()
            }
            return belop.toString()
        }

        return belop.toString()
    }

    /*
        Her skal det automatisk vises brutto grunnpensjon for de ulike beregningsperioder  Brutto garantipensjon for alderspensjon beregnet etter kapittel 20.
        Uføretrygd: feltet viser tomt
        Gjenlevendepensjon og barnepensjon: Grunnpensjon
        Hvis bruker har særtillegg, så skal det oppgis i dette feltet sammen med grunnpensjon, dvs. summen av grunnpensjon og særtillegg, eller summen av garantipensjon og pensjonstillegg.
        Behov for et nytt element fra Pensjon? Eller skal vi legge det sammen selv?

        GP grunnpensjon, ST særtillegg, GT garantipensjon,
     */
    //4.1.7.3.3
    private fun createYtelseskomponentGrunnpensjon(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): String? {
        logger.debug("4.1.7.3.3         Grunnpensjon")

        if (KSAK.UFOREP != sakType) {
            return hentYtelseskomponentBelop("GP,GT,ST", ytelsePrMnd).toString()
        }
        return null
    }

    /*
        Her skal det automatisk vises brutto tilleggspensjon for de ulike beregningsperioder  Brutto inntektspensjon for alderspensjon beregnet etter kapittel 20.

        Uføretrygd: feltet viser tomt

        Gjenlevendepensjon  og barnepensjon: Tilleggspensjon

        Tilleggspensjon.brutto TP, Inntektspensjon.brutto IP
     */
    //4.1.7.3.4
    private fun createYtelseskomponentTilleggspensjon(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): String? {
        logger.debug("4.1.7.3.4         Tilleggspensjon")

        if (KSAK.UFOREP != sakType) {
            return hentYtelseskomponentBelop("TP,IP", ytelsePrMnd).toString()
        }
        return null
    }


    //4.1.8
    private fun createBeregningItemPeriode(ytelsePrMnd: V1YtelsePerMaaned): Periode {
        logger.debug("4.1.7.1         BeregningItemPeriode")

        var tomstr: String? = null
        var fomstr: String? = null

        //not needed..(allways set)
        val fom = ytelsePrMnd.fom
        if (fom != null)
            fomstr = fom.simpleFormat()

        //needed..
        val tom = ytelsePrMnd.tom
        if (tom != null)
            tomstr = tom.simpleFormat()

        return Periode(
                fom = fomstr,
                tom = tomstr
        )
    }

    /*
    29.08.2018:
    Ja, vi tenkter at det er fint å ha dette preutfylt hvis vi har informasjon om beregning.

    Gjenlevendetillegg.brutto  GJENLEV
    Gjenlevenderettighet.brutto GJENLEV
    Barnetillegg.brutto TBF? og TBS?
    Ektefelletillegg.brutto
    MinstenivåTilleggIndividuelt.brutto
    MinstenivåTillegg PP.brutto
    Familietillegg.brutto
    DekningFasteUtgifterInst.brutto
    Skjermingstillegg.brutto SKJERMT
     */
    //4.1.9
    private fun createEkstraTilleggPensjon(pendata: Pensjonsinformasjon): Ukjent? {
        logger.debug("4.1.9         ekstra tilleggpensjon")

        var summer = 0
        pendata.ytelsePerMaanedListe.ytelsePerMaanedListe.forEach {
            summer += hentYtelseskomponentBelop("GJENLEV,TBF,TBS,PP,SKJERMT", it)
        }
        val ukjent = Ukjent(beloepBrutto = BeloepBrutto(ytelseskomponentAnnen = summer.toString()))
        if (summer > 0) {
            return ukjent
        }
        return null
    }

    //4.1.10 - 4.1.12
    private fun createGrunnlag(pendata: Pensjonsinformasjon): Grunnlag {

        logger.debug("4.1.10        Grunnlag")
        return Grunnlag(

                //4.1.10 - Nei
                medlemskap = createGrunnladMedlemskap(),

                //4.1.11
                opptjening = Opptjening(forsikredeAnnen = createOpptjeningForsikredeAnnen(pendata)),

                //4.1.12   $pensjon.vedtak[x].grunnlag.framtidigtrygdetid
                framtidigtrygdetid = createFramtidigtrygdetid(pendata)
        )
    }

    //4.1.10 - Nei
    //må fylles ut manuelt
    private fun createGrunnladMedlemskap(): String? {
        logger.debug("4.1.10            Basert på friviligperioder (Må fylles ut manuelt) Nei som default")
        return null
    }

    //4.1.12 - creditedPeriodIndicator -
    /*
        Hvis sakstype er Uføretrygd ELLER Gjenlevendepensjon ELLER Barnepensjon OG er innvilget OG
        beregningsmetode er "Folketrygd" OG "faktisk trygdetid i Norge" (skjermbilde "Beregne trygdetid") er mindre enn 40 år SÅ skal det velges alternativ "[1] Ja"

        Hvis sakstype er Uføretrygd ELLER Gjenlevendepensjon ELLER Barnepensjon OG er innvilget
        OG beregningsmetode er "Folketrygd" OG "faktisk trygdetid i Norge" (skjermbilde "Beregne trygdetid") er 40 år SÅ skal det velges alternativ "[2] Nei"

        Hvis sakstype er Uføretrygd ELLER Gjenlevendepensjon ELLER Barnepensjon OG er innvilget OG
        beregningsmetode er "EØS Pro rata" SÅ skal det velges alternativ "[2] Nei"

        Hvis sakstype er Alderspensjon SÅ skal det velges alternativ "[2] Nei"
     */
    private fun createFramtidigtrygdetid(pendata: Pensjonsinformasjon): String {
        logger.debug("4.1.12        Framtidigtrygdetid")
//        4.1.12. Credited period  [1] Yes | [0] No

        val key = "FOLKETRYGD"
        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        return when(sakType) {
            KSAK.ALDER -> "0" //nei
            else -> {
                when (hentVinnendeBergeningsMetode(pendata)) {
                    key -> "1" //ja

                    else -> "0" //nei
                }
            }
        }

    }

    /*
        HVIS Sakstype er Alderspensjon uten gjenlevenderett
        ELLER Uføretrygd uten gjenlevenderett
        SÅ skal det velges alternativ "[01] Nei".

        HVIS Sakstype er (Alderspensjon med gjenlevenderett
        OG ytelse  uten gjenlevenderett er vinnende beregning)
        ELLER Uføretrygd med gjenlevendetillegg som er lik 0
        SÅ skal det velges alternativ "[01] Nei".

        HVIS Sakstype er (Alderspensjon med gjenlevenderett
        OG ytelse  med gjenlevenderett er vinnende beregning)
        ELLER Uføretrygd med gjenlevendetillegg som er større enn 0
        SÅ skal det velges alternativ "[02] Delvis".

        HVIS Sakstype er Gjenlevendepensjon
        ELLER Barnepensjon
        SÅ skal det velges alternativ "[03] Fullstendig".
     */
    //4.1.11
    private fun createOpptjeningForsikredeAnnen(pendata: Pensjonsinformasjon): String? {
        logger.debug("4.1.11        OpptjeningForsikredeAnnen")

//        pendata.sakAlder.sakType
//        pendata.vedtak.kravVelgType
//        pendata.vedtak.kravGjelder

        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        val resultatGjenlevendetillegg = pendata.vilkarsvurderingListe?.vilkarsvurderingListe?.get(0)?.resultatGjenlevendetillegg ?: ""
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


    //4.1.13.1 - 4.1.13.2.1
    private fun createAvlsagsBegrunnelseItem(pendata: Pensjonsinformasjon): List<AvslagbegrunnelseItem>? {

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

    //4.1.13.1
    /*
        Hvis sakstype er alderspensjon,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Registrere trygdetid viser 00 trygdetid,
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[01]» i feltet 4.1.13.1. Rejection reasons.


        Hvis sakstype er alderspensjon,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Registrere trygdetid viser trygdetid 30 > < 360 dager,
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[02]» i feltet 4.1.13.1. Rejection reasons.

        HVIS sakstype er alderspensjon,
        OG Kravhode er markert med «Bodd/arbeidet i utlandet»,
        OG Resultat i Vilkårsprøving viser «Avslag»
        OG Vedtaksbegrunnelse viser «Pensjon for lav v/67 år»
        SÅ skal det velges alternativ «[03] Krav til perioden eller andre kvalifiseringskrav er ikke oppfylt» i feltet 4.1.13.1. Rejection reasons.


        HVIS sakstype er alderspensjon,
        OG Kravhode er markert med «Bodd/arbeidet i utlandet»,
        OG Resultat i Vilkårsprøving viser «Avslag»
        OG Vedtaksbegrunnelse viser «Bruker er under 62 år»
        SÅ skal det velges alternativ «[06] Pension age not yet reached» i feltet 4.1.13.1. Rejection reasons.


        Hvis sakstype er uføretrygd,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Registrere trygdetid viser 00 trygdetid,
        OG skjermbilde Vilkårsvurdering uføre viser Nei i «12-2 Forutgående medlemskap»,
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[01]» i feltet 4.1.13.1. Rejection reasons.


        Hvis sakstype er uføretrygd,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Registrere trygdetid viser trygdetid 30 > < 360 dager,
        OG skjermbilde Vilkårsvurdering uføre viser Nei i «12-2 Forutgående medlemskap»,
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[02]» i feltet 4.1.13.1. Rejection reasons.


        Hvis sakstype er uføretrygd,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Vilkårsvurdering uføre viser "Ikke opppfylt" i «§ 12-7, 12-17 Nedsatt inntektsevne»
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[04]» i feltet 4.1.13.1. Rejection reasons.


        HVIS sakstype er uføretrygd,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Vilkårsvurdering uføre viser "Ikke opppfylt" i «§ 12-5 Hensiktsmessig behandling» OG/ELLER «§ 12-5 Hensiktsmessige arbeidsrettede tiltak» OG/ELLER "§ 12-6 Sykdom, skade eller lyte - krav til årsakssammenheng"
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[08]» i feltet 4.1.13.1. Rejection reasons.

        HVIS sakstype er uføretrygd,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Vilkårsvurdering uføre viser "Ikke opppfylt" i «§ 12-4 Alder, samt vurdering ved krav fremsatt etter fylte 62 år"
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[03]» i feltet 4.1.13.1. Rejection reasons.

        HVIS sakstype er barnepensjon eller gjenlevendeytelse,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Registrere trygdetid viser 00 trygdetid på både gjenlevende og avdøde,
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[01]» i feltet 4.1.13.1. Rejection reasons.


        Hvis sakstype er barnepensjon eller gjenlevendeytelse,
        OG kravhode er markert med «Bodd/arbeidet i utlandet»
        OG skjermbilde Registrere trygdetid viser trygdetid 30 > < 36 dager på både gjenlevende og avdøde,
        OG skjermbilde Vilkårsprøving viser Avslag
        SÅ skal det velges alternativ «[02]» i feltet 4.1.13.1. Rejection reasons.


        HVIS sakstype er alderspensjon, uføretrygd?, barnepensjon eller gjenlevendeytelse,
        Og kravhode er markert med «Bodd/arbeidet i utlandet»,
        OG resultat i Vilkårsprøving viser «Avslag»,
        OG Vedtaksbegrunnelse viser «Ikke mottatt dokumentasjon»,
        SÅ skal det velges alternativ «[07] Manglende informasjon fra søkeren» i feltet 4.1.13.1. Rejection reasons.

     */
    fun createAvlsagsBegrunnelse(pendata: Pensjonsinformasjon): String? {
        logger.debug("4.1.13.1          AvlsagsBegrunnelse")

        if (pendata.vilkarsvurderingListe == null || pendata.vilkarsvurderingListe.vilkarsvurderingListe == null) {
            return null
        }
        val sakType = KSAK.valueOf(pendata.sakAlder.sakType)

        val avslagVilkarsproving = hentVilkarsResultatHovedytelse(pendata) == "AVSLAG"

        val harBoddArbeidetUtland = harBoddArbeidetUtland(pendata)
        val erTrygdetidListeTom = pendata.trygdetidListe.trygdetidListe.isEmpty()

        val erLavtTidligUttak = isVilkarsvurderingAvslagHovedytelseSamme("LAVT_TIDLIG_UTTAK", pendata)
        val erUnder62 = isVilkarsvurderingAvslagHovedytelseSamme("UNDER_62", pendata)
        val erIkkeMottattDok = "IKKE_MOTTATT_DOK" == hentVilkarsProvingAvslagHovedYtelse(pendata)

        //UFOREP
        val erForutMedlem = "FORUT_MEDL" == hentVilkarsvurderingUforetrygd(pendata).unntakForutgaendeMedlemskap
        val erHensArbrettTiltak = "HENS_ARBRETT_TILTAK" == hentVilkarsvurderingUforetrygd(pendata).hensiktsmessigArbeidsrettedeTiltak
        val erHensiktmessigBeh = "HENSIKTSMESSIG_BEH" == hentVilkarsvurderingUforetrygd(pendata).hensiktsmessigBehandling
        val erNedsattInntEvne = "NEDSATT_INNT_EVNE" == hentVilkarsvurderingUforetrygd(pendata).nedsattInntektsevne
        val erAlder = "ALDER" == hentVilkarsvurderingUforetrygd(pendata).alder

        //pkt1 og pkt.9
        if ((KSAK.ALDER == sakType || KSAK.BARNEP == sakType || KSAK.GJENLEV == sakType) && harBoddArbeidetUtland && erTrygdetidListeTom && avslagVilkarsproving)
            return "01"

        //pkt.2 og pkt.10
        if ((KSAK.ALDER == sakType || KSAK.BARNEP == sakType || KSAK.GJENLEV == sakType) && harBoddArbeidetUtland && erTrygdeTid(pendata) && avslagVilkarsproving)
            return "02"

        if (KSAK.ALDER == sakType && harBoddArbeidetUtland && erLavtTidligUttak && avslagVilkarsproving)
            return "03"

        if (KSAK.ALDER == sakType && harBoddArbeidetUtland && erUnder62 && avslagVilkarsproving)
            return "06"
        //hentVilkarsvurderingUforetrygd
        if (KSAK.UFOREP == sakType && harBoddArbeidetUtland && erAlder && avslagVilkarsproving)
            return "03"

        if (KSAK.UFOREP == sakType && harBoddArbeidetUtland && (erHensiktmessigBeh || erHensArbrettTiltak) && avslagVilkarsproving)
            return "08"
        //
        if (KSAK.UFOREP == sakType && harBoddArbeidetUtland && erNedsattInntEvne && avslagVilkarsproving)
            return "04"
        //
        if (KSAK.UFOREP == sakType && harBoddArbeidetUtland && erTrygdeTid(pendata) && erForutMedlem && avslagVilkarsproving)
            return "02"
        //pkt.5
        if (KSAK.UFOREP == sakType && harBoddArbeidetUtland && pendata.trygdetidListe.trygdetidListe.isEmpty() && erForutMedlem && avslagVilkarsproving)
            return "01"

        //siste..   pendata.sak.sakType alle..
        if (harBoddArbeidetUtland && erIkkeMottattDok && avslagVilkarsproving)
            return "07"

        logger.debug("              -- Ingen avslagsbegrunnelse")
        return null
    }
}