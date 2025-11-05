package no.nav.eessi.pensjon.fagmodul.pesys

data class TrygdetidRequest(
    val fnr: String,
    val rinaNr: Int? = null
)
