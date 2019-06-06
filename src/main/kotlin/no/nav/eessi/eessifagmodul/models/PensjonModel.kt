package no.nav.eessi.eessifagmodul.models

import java.time.LocalDate

data class PersonDetail(
        var sakType: String? = null,
        var buc: String? = null,
        var personNavn: String? = null,
        var kjoenn: String? = null,
        var fnr: String? = null,
        var fodselDato: LocalDate? = null,
        var aar16Dato: LocalDate? = null,
        var alder: Int? = null,
        var aktoerId: String? = null,
        var sivilStand: String? = null,
        var persomStatus: String? = null,
        var euxCaseId: String? = null
)

data class Pensjon(
		var reduksjon: List<ReduksjonItem>? = null,
		var vedtak: List<VedtakItem>? = null,
		var sak: Sak? = null,
		var gjenlevende: Bruker? = null,
		var bruker: Bruker? = null,
		var tilleggsinformasjon: Tilleggsinformasjon? = null,

        //p2000 - p2200
		var ytterligeinformasjon: String? = null,
		var etterspurtedokumenter: String? = null,
		var ytelser: List<YtelserItem>? = null,
		var forespurtstartdato: String? = null,

        //P3000
		var landspesifikk: Landspesifikk? = null,

        //P5000
		var medlemskapAnnen: List<MedlemskapItem>? = null,
		var medlemskapTotal: List<MedlemskapItem>? = null,
		var medlemskap: List<MedlemskapItem>? = null,
		var trygdetid: List<MedlemskapItem>? = null,
		var institusjonennaaikkesoektompensjon: List<String>? = null,
		var utsettelse: List<Utsettelse>? = null,

        //P2000, P2100, P2200, P8000?? Noen men ikke alle
		var vedlegg: List<String>? = null,

		var vedleggandre: String? = null,
		var angitidligstdato: String? = null,

		var kravDato: Krav? = null, //kravDato pkt. 9.1 P2000
		var antallSokereKjent: String? = null, //P2100 11.7

        //P8000
		var anmodning: AnmodningOmTilleggsInfo? = null,

        //P7000
		var samletVedtak: SamletMeldingVedtak? = null,

		//P10000 //P9000
		val merinformasjon: Merinformasjon? = null
        )

//P10000 -- innholder ytteligere informasjon om person (se kp. 5.1) som skal oversendes tilbake
data class Merinformasjon(
		val referansetilperson: String? = null, // kp.5.1 visr til om følgende informasjon gjelder hovedperson eller annenperson. - 01 - hoved -02 er annen

		val infoskolegang: List<InfoskolegangItem?>? = null,
		val overfoertedok: Overfoertedok? = null,
		val tilbaketrekkingpensjonskrav: List<TilbaketrekkingpensjonskravItem?>? = null,

		val yrkesaktiv: List<YrkesaktivItem?>? = null,

		val egenerklaering: Egenerklaering? = null,

		val infoarbeidsledig: List<InfoarbeidsledigItem?>? = null,

		val person: Person? = null, //fjenws...

		val bruker: Bruker? = null, //la stå?


		val livdoedinfo: Livdoedinfo? = null,

		val aktivitetsinfo: List<AktivitetsinfoItem?>? = null,
		val aktivitetsinfotilleggsopplysning: String? = null,

		val infoinntekt: List<InfoinntektItem>? = null,

		val relasjonforsoerget: Relasjonforsorget? = null, // endres i eux.
		val kravomgjennomgang: Kravomgjennomgang? = null,

		//merinfo, tillegginformajon for P10000
		val tilleggsinformasjon: String? = null,

		var ytelser: List<YtelserItem>? = null

)

data class Egenerklaering (
		val egenerklaering: String? = null,
		val dato: String? = null
)

//P10000 //P9000
data class YrkesaktivItem(
		val startdato: String? = null,
		val yrkesstatus: String? = null,
		val sluttdato: String? = null,

		val yrke: String? = null,
		val ansettelseforhold: String? = null,
		val timerpruke: String? = null
)

//P10000
data class Ytelsesdatoer(
		val annenytelse: String? = null,
		val datotyper: Datotyper? = null,
		val personkravstatus: String? = null,
		val beloep: List<BeloepItem>? = null
)

//P10000
data class Datotyper (
		val startdatoutbetaling: String? = null,
		val sluttdatoutbetaling: String? = null,

		val startdatoforstansytelse: String? = null,
		val sluttdatorettytelse: String? = null,

		val startdatoretttilytelser: String? = null,
		val sluttdatoredusertytelse: String? = null,

		val startdatoredusertytelse: String? = null,
		val sluttdatoforstansiytelser: String? = null,

		val datoavslagpaaytelse: String? = null,
		val datokravytelse: String? = null
)

//P10000 //P9000
data class InfoskolegangItem(
		val typeskolegang: String? = null,
		val startdatoskolegang: String? = null,
		val sluttdatoskolegang: String? = null,
		val skolenavn: String? = null,
		val merinfoomskolegang: String? = null
)

//P10000
data class Overfoertedok(
		val vedlagtedok: List<String?>? = null,
		val informasjonomdok: String? = null
)

//P10000
data class TilbaketrekkingpensjonskravItem(
		val landkode: String? = null,
		val datofortilbakekalling: String? = null,
		val kommentar: String? = null
)

//P10000
data class Kravomgjennomgang(
	//Art482RegECNo9872009
	val vedlagtkravomgjennomgangfremsatt: String? = null,
    val framsettelsesdatokrav: String? = null,
    val p7000datert: String? = null
)

//P10000
data class InfoinntektItem(

	val inntektellerikke: String? = null, //16.1.1
	val inntektskilde: String? = null, //16.1.2

	val startdatomottakinntekt: String? = null, //16.1.3
	val sluttdatomottakinntekt: String? = null, //16.1.4

	val inntektbelop: List<InntektBelopItem>? = null,

	val ytterligereinfoinntekt: String? = null, //16.1.6
	val tilleggsytelserutbetalingeritilleggtilinntekt: String? = null //16.1.7
)

//P10000
data class InntektBelopItem (
		val beloepgjeldendesiden: String? = null,
		val betalingshyppighet: String? = null,
		val annenbetalingshyppighet: List<String?>? = null,
		val valuta: String? = null,
		val beloep: String? = null
)

//P10000
data class Relasjonforsorget(
        val relasjonforsikretperson: String? = null,
        val beskrivelseannenslektning: String? = null
)

//P10000
data class Livdoedinfo(
   val dodsdato: String? = null,
   val by: String? = null,
   val land: String? = null,
   val lever: String? = null,
   val region: String? = null
)

//P10000
data class AktivitetsinfoItem(
    val antalltimer: String? = null,
    val startdato: String? = null,
    val sted: String? = null,
    val ansattype: String? = null,
    val sluttdato: String? = null
)

//P10000
data class InfoarbeidsledigItem(
    val startdato: String? = null,
    val arbeidsledig: String? = null,
    val sluttdato: String? = null
)

//P7000
data class SamletMeldingVedtak(
        var avslag: List<PensjonAvslagItem>? = null,
        var vedtaksammendrag: String? = null,
        var tildeltepensjoner: TildeltePensjoner? = null,
        var startdatoPensjonsRettighet: String? = null,  // 4.1.5
        var reduksjonsGrunn: String? = null    // 4.1.7
)

//P7000-5
data class PensjonAvslagItem(
        var pensjonType: String?= null,
        var begrunnelse: String? = null, //5.1
        var dato: String? = null,   //5.2
        var datoFrist: String? = null,
        var pin :PinItem? = null,
        var adresse: String? = null
)

//Institusjon
data class Institusjon(
        var institusjonsid: String? = null,
        var institusjonsnavn: String? = null,
        var saksnummer: String? = null,
        var sektor: String? = null,
        var land: String? = null,
        var pin: String? = null,
        var personNr: String? = null,
        var innvilgetPensjon: String? = null,  // 4.1.3.
        var utstedelsesDato: String? = null,  //4.1.4.
        var startdatoPensjonsRettighet: String? = null  //4.1.5
)

//P8000
data class AnmodningOmTilleggsInfo(
        var relasjonTilForsikretPerson: String? =  null, //4.1.1
        var beskrivelseAnnenSlektning: String? = null, // 4.2.1
        var referanseTilPerson: String? = null,
        var anmodningPerson: AnmodningOmPerson? = null, //10.
        var anmodningOmBekreftelse: AnmodningOmBekreftelse? = null, //9.
        var informasjon: AnmodningOmInformasjon? = null, //8
        var ytterligereInfoOmDokumenter: String? = null, //6.1
        var begrunnKrav: String? = null,
        var seder: List<SedAnmodningItem>? = null,
        var personAktivitet: List<PersonAktivitetItem>? = null,
        var personAktivitetSom: List<PersonAktivitetSomItem>? = null,
        var personInntekt: List<PersonensInntekt>? = null,
        var annenInfoOmYtelse: String? = null    //8.5
)

// 8.4
data class PersonensInntekt(
        var oppgiInntektFOM: String? = null,
        var personInntekt: List<PersonInntektItem>? = null
)

data class AnmodningOmInformasjon(
        var generellInformasjon: List<GenerellInfo>? = null, // 8.1
        var infoOmPersonYtelse: List<InfoOmPersonYtelse>? = null, // 8.2
        var annenEtterspurtInformasjon: String? = null,
        var begrunnelseKrav: String? = null //8.6
)

// Alt i denne blokken er 8.2
data class InfoOmPersonYtelse(
        var informerOmPersonFremsattKravEllerIkkeEllerMottattYtelse: String? = null, // 8.2.1
        var annenYtelse: String? = null, //8.2.2.1
        var sendInfoOm: List<SendInfoOm>? = null // 8.2.3.1
)

data class SendInfoOm(
        var sendInfoOm: String? = null,
        var annenInfoOmYtelser: String? = null // 8.2.3.2.1
)

data class AnmodningOmPerson(
        var egenerklaering: String? = null, //10.1
        var begrunnelseKrav: String? = null //10.2
)

data class AnmodningOmBekreftelse(
        var bekreftelseInfo: String? = null,
        var bekreftelsesGrunn: String? = null //9.2
)

data class GenerellInfo(
        var generellInfoOmPers: String? = null
)

data class SedAnmodningItem(
        var begrunnelse: String? = null,
        var andreEtterspurteSEDer: String? = null,
        var sendFolgendeSEDer: List<String>? = null //7.1.1
)

data class PersonAktivitetItem(
        var persAktivitet: String? = null
)

data class PersonAktivitetSomItem(
        var persAktivitetSom: String? = null
)

data class PersonInntektItem(
        var persInntekt: String? = null
)

//P2000
data class Utsettelse(
        var institusjonsnavn: String? = null,
        var institusjonsid: String? = null,
        var land: String? = null,
        var institusjon: Institusjon? = null,
        var tildato: String? = null
)

//P5000
data class MedlemskapItem(
        var relevans: String? = null,
        var ordning: String? = null,
        var land: String? = null,
        var sum: TotalSum? = null,
        var yrke: String? = null,
        var gyldigperiode: String? = null,
        var type: String? = null,
        var beregning: String? = null,
        var periode: Periode? = null
)

//P5000
data class Dager(
        var nr: String? = null,
        var type: String? = null
)

//P5000
data class TotalSum(
        var kvartal: String? = null,
        var aar: String? = null,
        var uker: String? = null,
        var dager: Dager? = null,
        var maaneder: String? = null
)

//P2000 - P2200 - //P10000
data class YtelserItem(
        var annenytelse: String? = null,
        var totalbruttobeloeparbeidsbasert: String? = null,
        var institusjon: Institusjon? = null,
        var pin: PinItem? = null,
        var startdatoutbetaling: String? = null,
        var mottasbasertpaa: String? = null,
        var ytelse: String? = null,
        var totalbruttobeloepbostedsbasert: String? = null,
        var startdatoretttilytelse: String? = null,
        var beloep: List<BeloepItem>? = null,
        var sluttdatoretttilytelse: String? = null,
        var sluttdatoutbetaling: String? = null,
        var status: String? = null,
        var ytelseVedSykdom: String? = null, //7.2 //P2100

        //P10000
        val annenbetalingshyppighet: String? = null,
        val datotyper: Datotyper? = null,
        val anneninfoomytelsertekst: String? = null,
        val tilleggsytelserutbetalingitilleggtilpensjon: String? = null,
		val ytelsesdatoer: Ytelsesdatoer? = null,
		val ytelsestype: String? = null

)


data class BeloepItem(
        var annenbetalingshyppighetytelse: String? = null,
        var betalingshyppighetytelse: String? = null,
        var valuta: String? = null,
        var beloep: String? = null,
        var gjeldendesiden: String? = null
)

data class Sak(
        var artikkel54: String? = null,
        var reduksjon: List<ReduksjonItem>? = null,
        var kravtype: List<KravtypeItem>? = null,
        var enkeltkrav: KravtypeItem? = null
)

data class KravtypeItem(
        var datoFrist: String? = null,
        var krav: String? = null
)

data class VedtakItem(
        var trekkgrunnlag: List<String>? = null,
        var mottaker: List<String>? = null,
        var grunnlag: Grunnlag? = null,
        var begrunnelseAnnen: String? = null,
        var artikkel: String? = null,
        var virkningsdato: String? = null,
        var ukjent: Ukjent? = null,
        var type: String? = null,
        var resultat: String? = null,
        var beregning: List<BeregningItem>? = null,
        var avslagbegrunnelse: List<AvslagbegrunnelseItem>? = null,
        var kjoeringsdato: String? = null,
        var basertPaa: String? = null,
        var basertPaaAnnen: String? = null,
        var delvisstans: Delvisstans? = null
)

data class Tilleggsinformasjon(
        var annen: Annen? = null,
        var anneninformation: String? = null,
        var saksnummer: String? = null,
        var person: Person? = null,
        var dato: String? = null,
        var andreinstitusjoner: List<AndreinstitusjonerItem>? = null,
        var saksnummerAnnen: String? = null,
        var artikkel48: String? = null,
        var opphoer: Opphoer? = null,
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
        var institusjonsid: String? = null,
        var institusjonsnavn: String? = null,
        var institusjonsadresse: String? = null,
        var postnummer: String? = null,
        var bygningsnr: String? = null,
        var land: String? = null,
        var region: String? = null,
        var poststed: String? = null
)

data class Annen(
        var institusjonsadresse: Institusjonsadresse? = null
)

data class Delvisstans(
        var utbetaling: Utbetaling? = null,
        var indikator: String? = null
)

data class Ukjent(
        var beloepBrutto: BeloepBrutto? = null
)

data class ReduksjonItem(
        var type: String? = null,
        var virkningsdato: List<VirkningsdatoItem>? = null,
        var aarsak: Arsak? = null,
        var artikkeltype: String? = null
)

data class VirkningsdatoItem(
        var startdato: String? = null,
        var sluttdato: String? = null
)

data class Arsak(
        var inntektAnnen: String? = null,
        var annenytelseellerinntekt: String? = null
)

data class Opphoer(
        var dato: String? = null,
        var annulleringdato: String? = null
)

data class Utbetaling(
        var begrunnelse: String? = null,
        var valuta: String? = null,
        var beloepBrutto: String? = null
)

data class Grunnlag(
        var medlemskap: String? = null,
        var opptjening: Opptjening? = null,
        var framtidigtrygdetid: String? = null
)

data class Opptjening(
        var forsikredeAnnen: String? = null
)

data class AvslagbegrunnelseItem(
        var begrunnelse: String? = null,
        var annenbegrunnelse: String? = null
)

data class BeregningItem(
        var beloepNetto: BeloepNetto? = null,
        var valuta: String? = null,
        var beloepBrutto: BeloepBrutto? = null,
        var utbetalingshyppighetAnnen: String? = null,
        var periode: Periode? = null,
        var utbetalingshyppighet: String? = null
)

data class BeloepNetto(
        var beloep: String? = null
)

data class BeloepBrutto(
        var ytelseskomponentTilleggspensjon: String? = null,
        var beloep: String? = null,
        var ytelseskomponentGrunnpensjon: String? = null,
        var ytelseskomponentAnnen: String? = null
)

data class Periode(
        var fom: String? = null,
        var tom: String? = null,
        var extra: String? = null
)

//P7000 4. Tildelte pensjoner
data class TildeltePensjoner(
        var pensjonType: String? = null, //4.1.2
        var vedtakPensjonType: String? = null, //4.1.1
        var tildeltePensjonerLand: String? = null,   //4.1.2.1.1.
        var addressatForRevurdering: String? = null,   //4.1.8.2.1.
        var institusjonPensjon: PensjonsInstitusjon? = null,
        var institusjon: Institusjon? = null
)

data class PensjonsInstitusjon(
        var sektor: String? = null
)