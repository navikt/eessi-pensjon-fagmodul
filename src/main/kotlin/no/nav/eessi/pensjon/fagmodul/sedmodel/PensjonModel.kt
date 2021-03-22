package no.nav.eessi.pensjon.fagmodul.sedmodel

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

class MeldingOmPensjon(
		val melding: String?,
		val pensjon: Pensjon
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Pensjon(
		val gjenlevende: Bruker? = null, // Brukes fleres steder

		//P2XXX
		val ytelser: List<YtelserItem>? = null,
		val forespurtstartdato: String? = null,
		val kravDato: Krav? = null, //kravDato pkt. 9.1 P2000
)

//Institusjon
data class Institusjon(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val saksnummer: String? = null,
        val sektor: String? = null,
        val land: String? = null,
        val pin: String? = null,
        val personNr: String? = null,
        val innvilgetPensjon: String? = null,  // 4.1.3.
        val utstedelsesDato: String? = null,  //4.1.4.
        val startdatoPensjonsRettighet: String? = null  //4.1.5
)

//P2000 - P2200
@JsonIgnoreProperties(ignoreUnknown = true)
data class YtelserItem(
		val totalbruttobeloeparbeidsbasert: String? = null,
		val institusjon: Institusjon? = null,
		val pin: PinItem? = null,
		val startdatoutbetaling: String? = null,
		val mottasbasertpaa: String? = null,
		val ytelse: String? = null,
		val startdatoretttilytelse: String? = null,
		val beloep: List<BeloepItem>? = null,
		val status: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BeloepItem(
        val betalingshyppighetytelse: String? = null,
        val valuta: String? = null,
        val beloep: String? = null,
        val gjeldendesiden: String? = null
)

data class Sak(
		val artikkel54: String? = null,
		val reduksjon: List<ReduksjonItem>? = null,
		val kravtype: List<KravtypeItem>? = null,
		val enkeltkrav: KravtypeItem? = null
)

data class KravtypeItem(
        val datoFrist: String? = null,
        val krav: String? = null
)

data class VedtakItem(
		val grunnlag: Grunnlag? = null,
		val virkningsdato: String? = null,
		val ukjent: Ukjent? = null,
		val type: String? = null,
		val resultat: String? = null,
		val beregning: List<BeregningItem>? = null,
		val avslagbegrunnelse: List<AvslagbegrunnelseItem>? = null,
		val basertPaa: String? = null,
		val basertPaaAnnen: String? = null
)

data class Tilleggsinformasjon(
		val dato: String? = null,
		val andreinstitusjoner: List<AndreinstitusjonerItem>? = null,
		val artikkel48: String? = null,
		val opphoer: Opphoer? = null,
)

data class AndreinstitusjonerItem(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val institusjonsadresse: String? = null,
        val postnummer: String? = null,
        val bygningsnr: String? = null,
        val land: String? = null,
        val region: String? = null,
        val poststed: String? = null
)

data class Ukjent(
        val beloepBrutto: BeloepBrutto? = null
)

data class ReduksjonItem(
		val type: String? = null,
		val virkningsdato: List<VirkningsdatoItem>? = null,
		val aarsak: Arsak? = null,
		val artikkeltype: String? = null
)

data class VirkningsdatoItem(
        val startdato: String? = null,
        val sluttdato: String? = null
)

data class Arsak(
        val inntektAnnen: String? = null,
        val annenytelseellerinntekt: String? = null
)

data class Opphoer(
        val dato: String? = null,
        val annulleringdato: String? = null
)

data class Grunnlag(
		val medlemskap: String? = null,
		val opptjening: Opptjening? = null,
		val framtidigtrygdetid: String? = null
)

data class Opptjening(
        val forsikredeAnnen: String? = null
)

data class AvslagbegrunnelseItem(
        val begrunnelse: String? = null,
        val annenbegrunnelse: String? = null
)

data class BeregningItem(
		val valuta: String? = null,
		val beloepBrutto: BeloepBrutto? = null,
		val periode: Periode? = null,
		val utbetalingshyppighet: String? = null
)

data class BeloepBrutto(
        val ytelseskomponentTilleggspensjon: String? = null,
        val beloep: String? = null,
        val ytelseskomponentGrunnpensjon: String? = null,
        val ytelseskomponentAnnen: String? = null
)

data class Periode(
        val fom: String? = null,
        val tom: String? = null,
        val extra: String? = null
)
