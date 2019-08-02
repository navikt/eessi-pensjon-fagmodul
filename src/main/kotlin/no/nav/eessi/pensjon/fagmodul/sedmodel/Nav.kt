package no.nav.eessi.pensjon.fagmodul.sedmodel

data class Nav(
        val eessisak: List<EessisakItem>? = null,
        val bruker: Bruker? = null,
        val ektefelle: Ektefelle? = null,
        val barn: List<BarnItem>? = null, //pkt 6 og 8
        val verge: Verge? = null,
        var krav: Krav? = null, // TODO fix mutable

        //X005
        val sak: Navsak? = null,
        //P10000
        val annenperson: Bruker? = null,

        //H120
        val endredeforhold: Endredeforhold? = null,
        val ytterligereinformasjon: String? = null
)

//H121
data class Endredeforhold(
        val bruker: Bruker? = null
)

//X005
data class Navsak (
        val kontekst: Kontekst? = null,
        val leggtilinstitusjon: Leggtilinstitusjon? = null
)

//X005
data class Kontekst(
        val bruker: Bruker? = null
)

//X005
data class Leggtilinstitusjon(
        val institusjon: InstitusjonX005? = null,
        val grunn: LeggtilinstitusjonGrunn? = null

)

data class LeggtilinstitusjonGrunn(
        val type: String? = null,
        val annet: String? = null
)

//X005
data class InstitusjonX005(
        val id: String,
        val navn: String
)

data class Krav(
        val dato: String? = null,
        //P15000
        val type: String? = null
)

data class Bruker(
        val mor: Foreldre? = null,
        val far: Foreldre? = null,
        val person: Person? = null,
        val adresse: Adresse? = null,

        //H120?
        val bostedsadresse: Adresse? = null,
        val status: BrukerStatus? = null,
        //H070
        val doedsfall: Doedsfall? = null,

        //P14000
        val endringer: Personendringer? = null,

        val arbeidsforhold: List<ArbeidsforholdItem>? = null,
        val bank: Bank? = null
)

//P14000
data class Personendringer(
        val personpinendringer: PersonpinEndringer? = null,
        val livsforhold: EndretLivsforhold? = null,
        val adresse: Adresse? = null,
        val sivilstand: EndretSivilstand? = null,
        val kjoenn: EndretKjoenn? = null,
        val gammeltetternavn: String? = null,
        val gammeltfornavn: String? = null,
        val utvandret: String? = null,
        val doedsdato: String? = null,
        val statsborgerskap: String? = null
)

//P14000
data class EndretLivsforhold(
        val verdi: String? = null,
        val startdato: String? = null
)

//P14000 endret kjønn
data class EndretKjoenn(
        val verdi: String? = null,
        val startdato: String? = null
)

//P14000 endret sivilstand
data class EndretSivilstand(
        val verdi: String? = null,
        val startdato: String? = null
)

//P14000 personpin endringer
data class PersonpinEndringer(
        val gammelt: String? = null,
        val nytt: String? = null
)

//H070
data class Doedsfall(
        val sted: Sted? = null,
        val doedsdato: String? = null,
        val dokumentervedlagt: Dokumentervedlagt? = null
)

//H070
data class Sted(
        val adresse: Adresse? = null
)

//H070
data class Dokumentervedlagt(
        val annet: List<String?>? = null,
        val type: List<String?>? = null
)

data class BrukerStatus(
        val id: String? = null
)

data class Bank(
        val navn: String? = null,
        val konto: Konto? = null,
        val adresse: Adresse? = null
)

data class Konto(
        val sepa: Sepa? = null,
        val innehaver: Innehaver? = null,
        val ikkesepa: IkkeSepa? = null,
        val kontonr: String? = null
)

data class IkkeSepa(
        val swift: String
)

data class Sepa(
        val iban: String? = null,
        val swift: String? = null
)

data class Innehaver(
        val rolle: String? = null,
        val navn: String? = null
)

data class Foreldre(
        val person: Person
)

data class BarnItem(
        val mor: Foreldre? = null,
        val person: Person? = null,
        val far: Foreldre? = null,
        val opplysningeromannetbarn: String? = null,
        val relasjontilbruker: String? = null
)

data class Ektefelle(
        val mor: Foreldre? = null,
        val person: Person? = null,
        val far: Foreldre? = null,
        val type: String? = null
)

data class Verge(
        val person: Person? = null,
        val adresse: Adresse? = null,
        val vergemaal: Vergemaal? = null,
        val vergenavn: String? = null
)

data class Vergemaal(
        val mandat: String? = null
)

data class Kontakt(
        val telefon: List<TelefonItem>? = null,
        val email: List<EmailItem>? = null,

        //direkte uten bruk av list?
        val telefonnr: String? = null,
        val emailadr: String? = null
)

data class TelefonItem(
        val type: String? = null,
        val nummer: String? = null
)

data class EmailItem(
        val adresse: String? = null
)

data class Person(
        val pin: List<PinItem>? = null,
        val pinannen: PinItem? = null, //kan fjernes hvis ikke i bruk
        val statsborgerskap: List<StatsborgerskapItem>? = null, //nasjonalitet
        val etternavn: String? = null,
        val fornavn: String? = null,
        val kjoenn: String? = null,
        val foedested: Foedested? = null,
        val tidligerefornavn: String? = null,
        val tidligereetternavn: String? = null,
        val fornavnvedfoedsel: String? = null,
        val etternavnvedfoedsel: String? = null,
        val foedselsdato: String? = null,

        val doedsdato: String? = null,
        val dodsDetalj: DodsDetalj? = null, //4 P2100

        val kontakt: Kontakt? = null,
        val sivilstand: List<SivilstandItem>? = null,   //familiestatus
        val relasjontilavdod: RelasjonAvdodItem? = null, //5.2.5 P2100
        val nyttEkteskapPartnerskapEtterForsikredeDod: NyttEkteskapPartnerskap? = null, //5.3.4 P2100
        val rolle: String? = null  //3.1 i P10000

)

data class DodsDetalj(
        val sted: String? = null, //4.1
        val dato: String? = null, //4.2
        val arsaker: List<DodsDetaljOrsakItem>? = null // 4.3
)

data class DodsDetaljOrsakItem(
        val arsak: String? = null, //4.3.1 P2100
        val annenArsak: String? = null //4.3.2.1
)

data class NyttEkteskapPartnerskap(
        val fraDato: String? = null,   //5.3.4. P2100
        val etternavn: String? = null, //5.3.4.2.1
        val fornavn: String? = null,   //5.3.4.2.2
        val borsammen: String? = null  //5.3

)

data class RelasjonAvdodItem(
        val pensjondetalj: List<AvdodPensjonItem>? = null, //3
        val relasjon: String? = null,  //5.2.5  P2100
        val sammehusholdning: String? = null,    //5.2.6  P2100
        val sammehusholdningfradato: String? = null, //5.2.7.1 P2100
        val harfellesbarn: String? = null, //5.3.2.1 P2100
        val forventetTerim: String? = null, //5.3.2.2
        val sperasjonType: String? = null, //5.3.3
        val giftParnerDato: String? = null // 5.3.1
)

data class AvdodPensjonItem(
        val mottattPensjonvedDod: String? = null, //3.2 P2100
        val mottattPensjonType: String? = null, //3.2.1
        val startDatoPensjonrettighet: String? = null, //3.2.3
        val institusjon: EessisakItem? = null // 3.2.2.1
)

data class SivilstandItem(
        val fradato: String? = null,
        val status: String? = null
)

data class StatsborgerskapItem(
        val land: String? = null
)

data class ArbeidsforholdItem(
        val inntekt: List<InntektItem?>? = null,
        val planlagtstartdato: String? = null,
        val arbeidstimerperuke: String? = null,
        val planlagtpensjoneringsdato: String? = null,
        val yrke: String? = null,
        val type: String? = null,
        val sluttdato: String? = null
)

data class InntektItem(
        val betalingshyppighetinntekt: String? = null,
        val beloeputbetaltsiden: String? = null,
        val valuta: String? = null,
        val annenbetalingshyppighetinntekt: String? = null,
        val beloep: String? = null
)

//TODO må kanskje oppdatere mot andre SED legge institusjonsid rett inn i PinItem..
data class PinItem(
        val institusjonsnavn: String? = null,
        val institusjonsid: String? = null,
        val sektor: String? = null,
        val identifikator: String? = null,  //rename? f.eks personnummer
        val land: String? = null,
        //P2000, P2100, P2200
        val institusjon: Institusjon? = null
)

data class Adresse(
        val gate: String? = null,
        val bygning: String? = null,
        val by: String? = null,
        val postnummer: String? = null,
        val region: String? = null,
        val land: String? = null,
        val kontaktpersonadresse: String? = null,
        val datoforadresseendring: String? = null,
        val postadresse: String? = null,
        val startdato: String? = null
)

data class Foedested(
        val by: String? = null,
        val land: String? = null,
        val region: String? = null
)

//refakt.. slå sammen til Institusjon
////TODO:
data class EessisakItem(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val saksnummer: String? = null,
        val land: String? = null
)

//hva benyttes denne til? må fjernes.
data class Ignore(
        val buildingName: String? = null,
        val region: String? = null,
        val otherInformation: String? = null
)