package no.nav.eessi.eessifagmodul.prefill.vedtak

import com.google.common.base.Preconditions
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.trygdetidliste.V1TrygdetidListe
import no.nav.pensjon.v1.vilkarsvurdering.V1Vilkarsvurdering
import no.nav.pensjon.v1.vilkarsvurderingliste.V1VilkarsvurderingListe
import no.nav.pensjon.v1.vilkarsvurderinguforetrygd.V1VilkarsvurderingUforetrygd
import no.nav.pensjon.v1.ytelsepermaaned.V1YtelsePerMaaned
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.temporal.ChronoUnit

abstract class VedtakPensjonData {

    private val logger: Logger by lazy { LoggerFactory.getLogger(VedtakPensjonData::class.java) }

    //K_SAK_T Kodeverk fra PESYS
    enum class KSAK {
        ALDER,
        UFOREP,
        GJENLEV,
        BARNEP;
    }

    init {
        logger.debug("PensjonData")
    }

    fun harBoddArbeidetUtland(pendata: Pensjonsinformasjon): Boolean {
        Preconditions.checkArgument(pendata.vedtak != null, "Vedtak er null")
        return pendata.vedtak.isBoddArbeidetUtland || harAvdodBoddArbeidetUtland(pendata)
    }

    private fun harAvdodBoddArbeidetUtland(pendata: Pensjonsinformasjon): Boolean {
        val avdod: V1Avdod = pendata.avdod ?: V1Avdod()

        if (avdod.avdod != "" && avdod.isAvdodBoddArbeidetUtland != null) {
            return avdod.isAvdodBoddArbeidetUtland
        }
        return false
    }

    fun isVilkarsvurderingAvslagHovedytelseSamme(key: String, pendata: Pensjonsinformasjon): Boolean {
        return key == hentVilkarsProvingAvslagHovedYtelse(pendata)
    }

    //TODO - summere opp i ant. dager . trygdetidListe.fom - tom.
    fun erTrygdeTid(pendata: Pensjonsinformasjon, storreEnn: Int = 30, mindreEnn: Int = 360): Boolean {
        Preconditions.checkArgument(pendata.trygdetidListe != null, "trygdetidListe er Null")
        Preconditions.checkArgument(pendata.trygdetidListe.trygdetidListe != null, "trygdetidListe er Null")
        val trygdeListe = pendata.trygdetidListe
        val days = summerTrygdeTid(trygdeListe)

        return days in (storreEnn + 1)..(mindreEnn - 1)
    }

    fun summerTrygdeTid(trygdeListe: V1TrygdetidListe): Int {
        Preconditions.checkArgument(trygdeListe.trygdetidListe != null, "trygdetidListe er Null")
        val daylist = mutableListOf<Int>()
        trygdeListe.trygdetidListe.forEach {
            val fom = it.fom.toGregorianCalendar().time.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val tom = it.tom.toGregorianCalendar().time.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val nrdays = ChronoUnit.DAYS.between(fom, tom)
            logger.debug("              SummerTrygdeTid: $nrdays  fom: $fom  tom: $tom ")
            daylist.add(nrdays.toInt())
        }
        var days = 0
        daylist.forEach {
            days += it
        }
        logger.debug("              Total SummerTrygdeTid: $days ")
        return days
    }

    fun hentYtelseskomponentBelop(keys: String, ytelse: V1YtelsePerMaaned): Int {
        val keylist = keys.split(",")
        var summer = 0
        keylist.forEach { keyword ->
            ytelse.ytelseskomponentListe.forEach { it2 ->
                //logger.debug("keyword: $keyword ==> type: ${it2.ytelsesKomponentType}")
                if (keyword.trim() == it2.ytelsesKomponentType) {
                    summer += it2.belopTilUtbetaling
                }
            }
        }
        return summer
    }

    fun hentGrunnPersjon(pendata: Pensjonsinformasjon?): Boolean {
        return pendata?.trygdeavtale?.isErArt10BruktGP ?: false
    }

    fun hentTilleggsPensjon(pendata: Pensjonsinformasjon?): Boolean {
        return pendata?.trygdeavtale?.isErArt10BruktTP ?: false
    }

    fun hentVilkarsvurderingUforetrygd(pendata: Pensjonsinformasjon): V1VilkarsvurderingUforetrygd {
        return hentV1Vilkarsvurdering(pendata)?.vilkarsvurderingUforetrygd ?: return V1VilkarsvurderingUforetrygd()
    }

    //         Kodeverk K_RESULT_BEGR 2017
    fun hentVilkarsProvingAvslagHovedYtelse(pendata: Pensjonsinformasjon): String {
        return hentV1Vilkarsvurdering(pendata)?.avslagHovedytelse ?: return ""
    }

    private fun hentV1Vilkarsvurdering(pendata: Pensjonsinformasjon): V1Vilkarsvurdering? {
        val v1VilkarsvurderingListe: V1VilkarsvurderingListe? = pendata.vilkarsvurderingListe ?: V1VilkarsvurderingListe()
        return v1VilkarsvurderingListe?.vilkarsvurderingListe?.getOrElse(0) { V1Vilkarsvurdering() }
    }

    fun hentVilkarsResultatHovedytelse(pendata: Pensjonsinformasjon): String {
        return hentV1Vilkarsvurdering(pendata)?.resultatHovedytelse ?: return "" // UNDER_62 -- LAVT_TIDLIG_UTTAK osv..
    }

    fun isForeldelos(pendata: Pensjonsinformasjon): Boolean {
        val avdodpinfar: String? = pendata.avdod.avdodFar ?: "INGEN"
        val avdodpinmor: String? = pendata.avdod.avdodMor ?: "INGEN"
        if (avdodpinfar != "INGEN" && avdodpinmor != "INGEN") {
            return true
        }
        return false
    }

    fun hentVinnendeBergeningsMetode(pendata: Pensjonsinformasjon): String {
        logger.debug(" +            hentVinnendeBergeningsMetode")
        return hentSisteYtelsePerMaaned(pendata).vinnendeBeregningsmetode
    }

    fun hentVurdertBeregningsmetodeNordisk(pendata: Pensjonsinformasjon): Boolean {
        logger.debug(" +            hentVurdertBeregningsmetodeNordisk")
        return hentSisteYtelsePerMaaned(pendata).isVurdertBeregningsmetodeNordisk
    }

    fun hentYtelseBelop(pendata: Pensjonsinformasjon): String {
        logger.debug(" +            hentYtelseBelop")
        return hentSisteYtelsePerMaaned(pendata).belop.toString()
    }

    fun isMottarMinstePensjonsniva(pendata: Pensjonsinformasjon): Boolean {
        logger.debug(" +            isMottarMinstePensjonsniva")
        return hentSisteYtelsePerMaaned(pendata).isMottarMinstePensjonsniva
    }

    fun hentSisteYtelsePerMaaned(pendata: Pensjonsinformasjon): V1YtelsePerMaaned {
        val ytelseprmnd = pendata.ytelsePerMaanedListe
        val liste = ytelseprmnd.ytelsePerMaanedListe as List<V1YtelsePerMaaned>
        return liste.asSequence().sortedBy { it.fom.toGregorianCalendar() }.toList().last()
    }


}