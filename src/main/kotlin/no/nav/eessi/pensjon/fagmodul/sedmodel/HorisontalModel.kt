package no.nav.eessi.pensjon.fagmodul.sedmodel

data class Medisinsk(
        val undersoekelse: Undersoekelse? = null,
        val informasjon: Informasjon? = null
)

data class Horisontal(
        val anmodningmedisinskinformasjon: Anmodningmedisinskinformasjon? = null,
        val refusjonskrav: Refusjonskrav? = null
)

//H020
data class Refusjonskrav(
        val debitorinstitusjon: Debitorinstitusjon? = null,
        val bank: Bank? = null,
        val totaltantallfakturaer: String? = null,
        val refusjon: List<RefusjonItem?>? = null,
        val kreditorinstitusjon: Kreditorinstitusjon? = null
)

data class Debitorinstitusjon(
        val navn: String? = null,
        val id: String? = null,
        val globalreferanse: String? = null
)

data class RefusjonItem(
        val henvisningtil: String? = null,
        val utstedelsesdato: String? = null,
        val kreditorinstitusjon: Kreditorinstitusjon? = null,
        val avslag: Avslag? = null

)

data class Avslag(
        val valuta: String? = null,
        val grunn: Grunn? = null,
        val type: Type? = null,
        val beloep: String? = null
)

data class Type(
        val id: String? = null
)

data class Kreditorinstitusjon(
        val navn: String? = null,
        val globalreferanse: String? = null,
        val id: String? = null,
        val krav: KreditorKrav? = null,
        val fakturanummer: String? = null,
        val valuta: String? = null,
        val beloep: String? = null,
        val utbetaling: KreditorUtbetaling? = null,
        val betalingsreferanse: String? = null
)

data class KreditorUtbetaling(
        val valuta: String? = null,
        val totalbeloep: String? = null
)

data class KreditorKrav(
        val valuta: String? = null,
        val forfallsdato: String? = null,
        val totalbeloep: String? = null,
        val avvist: Avvist? = null
)

data class Avvist(
        val valuta: String? = null,
        val totalbeloep: String? = null
)

//H120 - H121
data class Anmodningmedisinskinformasjon(
        val etterspurtdokumentasjon: List<String?>? = null,
        val familie: Familie? = null,
        val spesiellekravtildokumentasjon: String? = null,
        val arbeidsulykkeyrkessykdom: Arbeidsulykkeyrkessykdom? = null,
        val beroertytelse: String? = null,
        val etterspurthandling: List<String?>? = null,
        val relevantperiode: Relevantperiode? = null,
        val annendokumentasjon: String? = null,
        val samtykke: Samtykke? = null,
        val svar: Svar? = null
)

data class Arbeidsulykkeyrkessykdom(
        val arbeidsgiver: HorisontalArbeidsgiver? = null,
        val sykdom: Sykdom? = null,
        val dato: String? = null,
        val konsekvensellerbeskrivelse: String? = null,
        val bruker: Bruker? = null,
        val type: String? = null,
        val status: Status? = null
)

data class Familie(
        val etterspurtdokumentasjon: List<String?>? = null,
        val annendokumentasjon: String? = null
)

data class HorisontalArbeidsgiver(
        val identifikator: List<IdentifikatorItem?>? = null,
        val adresse: Adresse? = null,
        val navn: String? = null
)

data class Relevantperiode(
        val startdato: String? = null,
        val sluttdato: String? = null
)

data class IdentifikatorItem(
        val id: String? = null,
        val type: String? = null
)

data class Samtykke(
        val dekningkostnader: String? = null
)

data class Status(
        val annet: String? = null,
        val id: String? = null
)

data class Sykdom(

        val kode: String? = null,
        val kodingssystem: String? = null
)

data class Informasjon(
        val annen: String? = null,
        val type: List<String>? = null
)

data class Svar(
        val medisinsk: Medisinsk? = null,
        val undersoekelse: Undersoekelse? = null,
        val dokumentasjonikkevedlagt: Dokumentasjonikkevedlagt? = null,
        val erdokumentasjonsvedlagt: String? = null
)

data class Dokumentasjonikkevedlagt(
        val grunn: String? = null
)

data class Undersoekelse(
        val estimat: Estimat? = null,
        val ikkegjennomfoert: Ikkegjennomfoert? = null,
        val type: String? = null
)

data class Ikkegjennomfoert(
        val grunn: Grunn? = null
)

data class Grunn(
        val annen: String? = null,
        val type: String? = null,
        val annet: String? = null
)

data class Estimat(
        val kostnader: Kostnader? = null
)

data class Kostnader(
        val valuta: String? = null,
        val beloep: String? = null
)