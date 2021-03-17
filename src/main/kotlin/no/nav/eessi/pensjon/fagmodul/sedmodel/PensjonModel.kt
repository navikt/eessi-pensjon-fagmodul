package no.nav.eessi.pensjon.fagmodul.sedmodel

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

class MeldingOmPensjon(
		val melding: String?,
		val pensjon: Pensjon
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Pensjon(
		val gjenlevende: Bruker? = null, // Brukes fleres steder

		//P2000
		val angitidligstdato: String? = null,

		//P2XXX
		val ytelser: List<YtelserItem>? = null,
		val forespurtstartdato: String? = null,
		val kravDato: Krav? = null, //kravDato pkt. 9.1 P2000

		//P3000
		val landspesifikk: Landspesifikk? = null,

		//P5000
		val trygdetid: List<MedlemskapItem>? = null,

        //P8000
		val anmodning: AnmodningOmTilleggsInfo? = null,

		val bruker: Bruker? = null
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

//P8000
@JsonIgnoreProperties(ignoreUnknown = true)
data class AnmodningOmTilleggsInfo(
		val referanseTilPerson: String? = null
)

//P5000
@JsonIgnoreProperties(ignoreUnknown = true)
data class MedlemskapItem(
		val land: String? = null,
		val periode: Periode? = null,
)

//P2000 - P2200
data class YtelserItem(
		val annenytelse: String? = null,
		val totalbruttobeloeparbeidsbasert: String? = null,
		val institusjon: Institusjon? = null,
		val pin: PinItem? = null,
		val startdatoutbetaling: String? = null,
		val mottasbasertpaa: String? = null,
		val ytelse: String? = null,
		val totalbruttobeloepbostedsbasert: String? = null,
		val startdatoretttilytelse: String? = null,
		val beloep: List<BeloepItem>? = null,
		val sluttdatoretttilytelse: String? = null,
		val sluttdatoutbetaling: String? = null,
		val status: String? = null,
		val ytelseVedSykdom: String? = null, //7.2 //P2100
)

data class BeloepItem(
        val annenbetalingshyppighetytelse: String? = null,
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
		val annen: Annen? = null,
		val anneninformation: String? = null,
		val saksnummer: String? = null,
		val person: Person? = null,
		val dato: String? = null,
		val andreinstitusjoner: List<AndreinstitusjonerItem>? = null,
		val saksnummerAnnen: String? = null,
		val artikkel48: String? = null,
		val opphoer: Opphoer? = null,
		val tilleggsopplysning: String? = null,

		//P9000
		val p8000: RefP8000? = null,
		val bekreftelseSed: List<BekreftelseSedItem>? = null,

		val ikkeyrkesaktiv: String? = null,
		val arbeidsledig: String? = null,
		val negativtsvar: Negativtsvar? = null,

		val vedlegginfo: String? = null,
		val vedlegg: List<VedleggItem>? = null,

		val yrkesaktivitet: Yrkesaktivitet? = null
)

data class VedleggItem(
		val dokument: String? = null
)

data class BekreftelseSedItem(
		val aarsak: String? = null,
		val p8000ref: String? = null,
		val grunn: String? = null,
		//verder? 01,02 ??
		val info: String? = null
)

//P9000
data class RefP8000(
		//verdier? 01, 02, 03???
		val henvisningperson: String? = null,
		val dato: String? = null
)

//P9000
data class Negativtsvar(
		val aarsakgrunn: String? = null,
		val aarsakannen: String? = null,
		val aarsakikkesendsed: String? = null,
		val dokument: String? = null,
		val informasjon: String? = null,
		val bekreftelseinformasjon: String? = null,
		val sed: String? = null
)

data class Yrkesaktivitet(
		val ingenaktivtetinformasjon: String? = null,
		val tilleggsopplysning: String? = null
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

data class Annen(
        val institusjonsadresse: Institusjonsadresse? = null
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
		val beloepNetto: BeloepNetto? = null,
		val valuta: String? = null,
		val beloepBrutto: BeloepBrutto? = null,
		val utbetalingshyppighetAnnen: String? = null,
		val periode: Periode? = null,
		val utbetalingshyppighet: String? = null
)

data class BeloepNetto(
        val beloep: String? = null
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

data class Institusjonsadresse(
		val poststed: String? = null,
		val postnummer: String? = null,
		val land: String? = null
)
