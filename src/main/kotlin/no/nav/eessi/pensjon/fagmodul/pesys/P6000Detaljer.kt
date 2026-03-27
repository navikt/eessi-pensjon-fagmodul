package no.nav.eessi.pensjon.fagmodul.pesys

data class P6000Detaljer(
    val pesysId: String,
    val rinaSakId: String,
    val dokumentId: List<String>
)