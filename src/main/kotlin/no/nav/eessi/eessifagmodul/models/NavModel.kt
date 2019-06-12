package no.nav.eessi.eessifagmodul.models

data class Nav(
        var eessisak: List<EessisakItem>? = null,
        var bruker: Bruker? = null,
        var ektefelle: Ektefelle? = null,
        var barn: List<BarnItem>? = null, //pkt 6 og 8
        var verge: Verge? = null,
        var krav: Krav? = null,
        //X005
        var sak: Navsak? = null,
        //P10000
        var annenperson: Bruker? = null

)

//X005
data class Navsak (
        var kontekst: Kontekst? = null,
        var leggtilinstitusjon: Leggtilinstitusjon? = null
)

//X005
data class Kontekst(
        var bruker: Bruker? = null
)

//X005
data class Leggtilinstitusjon(
        var institusjon: InstitusjonX005? = null,
        var grunn: String? = null

)

//X005
data class InstitusjonX005(
        val id: String,
        val navn: String
)


data class Krav(
        var dato: String? = null,
        //P15000
        var type: String? = null
)

data class Bruker(
        var mor: Foreldre? = null,
        var far: Foreldre? = null,
        var person: Person? = null,
        var adresse: Adresse? = null,
        var arbeidsforhold: List<ArbeidsforholdItem>? = null,
        var bank: Bank? = null
)

data class Bank(
        var navn: String? = null,
        var konto: Konto? = null,
        var adresse: Adresse? = null
)

data class Konto(
        var sepa: Sepa? = null,
        var innehaver: Innehaver? = null,
        var ikkesepa: IkkeSepa? = null,
        var kontonr: String? = null
)

data class IkkeSepa(
        var swift: String? = null
)

data class Sepa(
        var iban: String? = null,
        var swift: String? = null
)

data class Innehaver(
        var rolle: String? = null,
        var navn: String? = null
)

data class Foreldre(
        var person: Person? = null
)

data class BarnItem(
        var mor: Foreldre? = null,
        var person: Person? = null,
        var far: Foreldre? = null,
        var opplysningeromannetbarn: String? = null,
        var relasjontilbruker: String? = null
)

data class Ektefelle(
        var mor: Foreldre? = null,
        var person: Person? = null,
        var far: Foreldre? = null,
        var type: String? = null
)

data class Verge(
        var person: Person? = null,
        var adresse: Adresse? = null,
        var vergemaal: Vergemaal? = null,
        var vergenavn: String? = null
)

data class Vergemaal(
        var mandat: String? = null
)

data class Kontakt(
        var telefon: List<TelefonItem>? = null,
        var email: List<EmailItem>? = null,

        //direkte uten bruk av list?
        var telefonnr: String? = null,
        var emailadr: String? = null
)

data class TelefonItem(
        var type: String? = null,
        var nummer: String? = null
)

data class EmailItem(
        var adresse: String? = null
)


data class Person(
        var pin: List<PinItem>? = null,
        var pinannen: PinItem? = null, //kan fjernes hvis ikke i bruk
        var statsborgerskap: List<StatsborgerskapItem>? = null, //nasjonalitet
        var etternavn: String? = null,
        var fornavn: String? = null,
        var kjoenn: String? = null,
        var foedested: Foedested? = null,
        var tidligerefornavn: String? = null,
        var tidligereetternavn: String? = null,
        var fornavnvedfoedsel: String? = null,
        var etternavnvedfoedsel: String? = null,
        var foedselsdato: String? = null,

        var doedsdato: String? = null,
        var dodsDetalj: DodsDetalj? = null, //4 P2100

        var kontakt: Kontakt? = null,
        var sivilstand: List<SivilstandItem>? = null,   //familiestatus
        var relasjontilavdod: RelasjonAvdodItem? = null, //5.2.5 P2100
        var nyttEkteskapPartnerskapEtterForsikredeDod: NyttEkteskapPartnerskap? = null, //5.3.4 P2100
        var rolle: String? = null  //3.1 i P10000

)

data class DodsDetalj(
        var sted: String? = null, //4.1
        var dato: String? = null, //4.2
        var arsaker: List<DodsDetaljOrsakItem>? = null // 4.3
)

data class DodsDetaljOrsakItem(
        var arsak: String? = null, //4.3.1 P2100
        var annenArsak: String? = null //4.3.2.1
)

data class NyttEkteskapPartnerskap(
        var fraDato: String? = null,   //5.3.4. P2100
        var etternavn: String? = null, //5.3.4.2.1
        var fornavn: String? = null,   //5.3.4.2.2
        var borsammen: String? = null  //5.3

)

data class RelasjonAvdodItem(
        var pensjondetalj: List<AvdodPensjonItem>? = null, //3

        var relasjon: String? = null,  //5.2.5  P2100
        var sammehusholdning: String? = null,    //5.2.6  P2100
        var sammehusholdningfradato: String? = null, //5.2.7.1 P2100
        var harfellesbarn: String? = null, //5.3.2.1 P2100
        var forventetTerim: String? = null, //5.3.2.2
        var sperasjonType: String? = null, //5.3.3
        var giftParnerDato: String? = null // 5.3.1
)

data class AvdodPensjonItem(
        var mottattPensjonvedDod: String? = null, //3.2 P2100
        var mottattPensjonType: String? = null, //3.2.1
        var startDatoPensjonrettighet: String? = null, //3.2.3
        var institusjon: EessisakItem? = null // 3.2.2.1
)

data class SivilstandItem(
        var fradato: String? = null,
        var status: String? = null
)

data class StatsborgerskapItem(
        var land: String? = null
)

data class ArbeidsforholdItem(
        var inntekt: List<InntektItem?>? = null,
        var planlagtstartdato: String? = null,
        var arbeidstimerperuke: String? = null,
        var planlagtpensjoneringsdato: String? = null,
        var yrke: String? = null,
        var type: String? = null,
        var sluttdato: String? = null
)

data class InntektItem(
        var betalingshyppighetinntekt: String? = null,
        var beloeputbetaltsiden: String? = null,
        var valuta: String? = null,
        var annenbetalingshyppighetinntekt: String? = null,
        var beloep: String? = null
)

//TODO må kanskje oppdatere mot andre SED legge institusjonsid rett inn i PinItem..
data class PinItem(
        var institusjonsnavn: String? = null,
        var institusjonsid: String? = null,
        var sektor: String? = null,
        var identifikator: String? = null,  //rename? f.eks personnummer
        var land: String? = null,
        //P2000, P2100, P2200
        var institusjon: Institusjon? = null
)

data class Adresse(
        var gate: String? = null,
        var bygning: String? = null,
        var by: String? = null,
        var postnummer: String? = null,
        var region: String? = null,
        var land: String? = null,

        var kontaktpersonadresse: String? = null,
        var datoforadresseendring: String? = null,
        var postadresse: String? = null
)

data class Foedested(
        var by: String? = null,
        var land: String? = null,
        var region: String? = null
)

//refakt.. slå sammen til Institusjon
////TODO:
data class EessisakItem(
        var institusjonsid: String? = null,
        var institusjonsnavn: String? = null,
        var saksnummer: String? = null,
        var land: String? = null
)

//
//data class Institusjon(
//        var institusjonsid: String? = null,
//        var institusjonsnavn: String? = null,
//        var saksnummer: String? = null,
//        var sektor: String? = null,
//        var land: String? = null
//)

////TODO:
//hvor brukes denne?
data class Institusjonsadresse(
        var poststed: String? = null,
        var postnummer: String? = null,
        var land: String? = null
)

//hva benyttes denne til? må fjernes.
data class Ignore(
        var buildingName: String? = null,
        var region: String? = null,
        var otherInformation: String? = null
)
