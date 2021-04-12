package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper


import no.nav.eessi.pensjon.eux.model.sed.BeloepBrutto
import no.nav.eessi.pensjon.eux.model.sed.BeregningItem
import no.nav.eessi.pensjon.eux.model.sed.Periode
import no.nav.eessi.pensjon.eux.model.sed.Ukjent
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.ytelsepermaaned.V1YtelsePerMaaned
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PrefillPensjonVedtaksbelop {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjonVedtaksbelop::class.java) }

    /**
     *  4.1.7.3.1. Gross amount
     */
    fun createBelop(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): String {
        logger.debug("4.1.7.3.1         Gross amount")
        val belop = ytelsePrMnd.belop

        if (KSAK.UFOREP == sakType) {
            val uforUtOrd = VedtakPensjonDataHelper.hentYtelseskomponentBelop("UT_ORDINER,UT_TBF,UT_TBS", ytelsePrMnd)
            if (uforUtOrd > belop) {
                return uforUtOrd.toString()
            }
            return belop.toString()
        }
        return belop.toString()
    }

    /**
     *  4.1.7.3.3
     *
     *  Her skal det automatisk vises brutto grunnpensjon for de ulike beregningsperioder  Brutto garantipensjon for alderspensjon beregnet etter kapittel 20.
     */
    fun createYtelseskomponentGrunnpensjon(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): String? {
        logger.debug("4.1.7.3.3         Grunnpensjon")

        if (KSAK.UFOREP != sakType) {
            return VedtakPensjonDataHelper.hentYtelseskomponentBelop("GP,GT,ST", ytelsePrMnd).toString()
        }
        return null
    }

    /**
     *  4.1.7.3.4
     *
     *  Her skal det automatisk vises brutto tilleggspensjon for de ulike beregningsperioder  Brutto inntektspensjon for alderspensjon beregnet etter kapittel 20.
     */
    fun createYtelseskomponentTilleggspensjon(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): String? {
        logger.debug("4.1.7.3.4         Tilleggspensjon")

        if (KSAK.UFOREP != sakType) {
            return VedtakPensjonDataHelper.hentYtelseskomponentBelop("TP,IP", ytelsePrMnd).toString()
        }
        return null
    }

    /**
     * 4.1.9
     */
    fun createEkstraTilleggPensjon(pendata: Pensjonsinformasjon): Ukjent? {
        logger.debug("4.1.9         ekstra tilleggpensjon")

        var summer = 0
        pendata.ytelsePerMaanedListe.ytelsePerMaanedListe.forEach {
            summer += VedtakPensjonDataHelper.hentYtelseskomponentBelop("GJENLEV,TBF,TBS,PP,SKJERMT", it)
        }
        val ukjent = Ukjent(beloepBrutto = BeloepBrutto(ytelseskomponentAnnen = summer.toString()))
        if (summer > 0) {
            return ukjent
        }
        return null
    }

    /**
     *  4.1.7
     */
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

    /**
     * 4.1.8
     */
    private fun createBeregningItemPeriode(ytelsePrMnd: V1YtelsePerMaaned): Periode {
        logger.debug("4.1.7.1         BeregningItemPeriode")

        var tomstr: String? = null
        var fomstr: String? = null

        val fom = ytelsePrMnd.fom
        if (fom != null)
            fomstr = fom.simpleFormat()

        val tom = ytelsePrMnd.tom
        if (tom != null)
            tomstr = tom.simpleFormat()

        return Periode(
                fom = fomstr,
                tom = tomstr
        )
    }

    private fun createBeregningItem(ytelsePrMnd: V1YtelsePerMaaned, sakType: KSAK): BeregningItem {
        logger.debug("4.1.7         BeregningItem (Repeterbart)")

        return BeregningItem(
                //4.1.7.1 -- 4.1.7.2
                periode = createBeregningItemPeriode(ytelsePrMnd),

                beloepBrutto = BeloepBrutto(
                        //4.1.7.3.1. Gross amount
                        beloep = createBelop(ytelsePrMnd, sakType),

                        //4.1.7.3.3. Gross amount of basic pension
                        ytelseskomponentGrunnpensjon = createYtelseskomponentGrunnpensjon(ytelsePrMnd, sakType),

                        //4.1.7.3.4. Gross amount of supplementary pension
                        ytelseskomponentTilleggspensjon = createYtelseskomponentTilleggspensjon(ytelsePrMnd, sakType),
                ),

                //4.1.7.4 Currency automatisk hukes for "NOK" norway krone.
                valuta = "NOK",

                //4.1.7.5              //03 - montly 12/year
                utbetalingshyppighet = "maaned_12_per_aar"
        )
    }
}