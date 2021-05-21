package no.nav.eessi.pensjon.fagmodul.models

enum class Kodeverk(val value: String) {
    LANDKODER("landkoder"),
    PENSJON("pensions");
}

data class KodeverkResponse(
    val kode: String? = null,
    val term: String? = null,
)