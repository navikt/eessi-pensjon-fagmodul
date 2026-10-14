package no.nav.eessi.pensjon.services.pensjonsinformasjon

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class P6000MeldingOmVedtakDto(
    val avdod: Avdod?,
    val sakType: EessiFellesDto.EessiSakType,
    val trygdeavtale: Trygdeavtale?,
    val trygdetid: List<Trygdetid>,
    val vedtak: Vedtak,
    val vilkarsvurdering: List<Vilkarsvurdering>,
    val ytelsePerMaaned: List<YtelsePerMaaned>
) {
    data class Avdod(
        val avdod: String?,
        val avdodBoddArbeidetUtland: Boolean?,
        val avdodFarBoddArbeidetUtland: Boolean?,
        val avdodMorBoddArbeidetUtland: Boolean?
    )

    data class Trygdeavtale(
        val erArt10BruktGP: Boolean?,
        val erArt10BruktTP: Boolean?
    )

    data class Trygdetid(
        val fom: LocalDate,
        val tom: LocalDate
    )

    data class Vedtak(
        val virkningstidspunkt: LocalDate,
        val kravGjelder: String,
        val hovedytelseTrukket: Boolean,
        val boddArbeidetUtland: Boolean?,
        val datoFattetVedtak: LocalDate?
    )

    data class Vilkarsvurdering(
        val fom: LocalDate,
        val vilkarsvurderingUforetrygd: VilkarsvurderingUforetrygd?,
        val resultatHovedytelse: String?,
        val harResultatGjenlevendetillegg: Boolean,
        val avslagHovedytelse: String?
    )

    data class VilkarsvurderingUforetrygd(
        val alder: String?,
        val hensiktsmessigBehandling: String?,
        val hensiktsmessigArbeidsrettedeTiltak: String?,
        val nedsattInntektsevne: String?,
        val unntakForutgaendeMedlemskap: String?
    )

    data class YtelsePerMaaned(
        val fom: LocalDate,
        val tom: LocalDate?,
        val mottarMinstePensjonsniva: Boolean,
        val vinnendeBeregningsmetode: String? = null,
        val belop: Int,
        @JsonProperty("ytelseskomponenter")
        val ytelseskomponent: List<Ytelseskomponent>
    )

    data class Ytelseskomponent(
        val ytelsesKomponentType: String?,
        val belopTilUtbetaling: Int
    )
}
