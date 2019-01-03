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

        //P2000, P2100, P2200, P8000
        var vedlegg: List<String>? = null,

        var vedleggandre: String? = null,
        var angitidligstdato: String? = null,

        var kravDato: Krav? = null, //kravDato pkt. 9.1 P2000
        var antallSokereKjent: String? = null, //P2100 11.7

        //P8000
        var anmodning: AnmodningOmTilleggsInfo? = null,

        //P7000
        var samletVedtak: SamletMeldingVedtak? = null
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

//P2000 - P2200
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
        var ytelseVedSykdom: String? = null //7.2 //P2100
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
        var opphoer: Opphoer? = null
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