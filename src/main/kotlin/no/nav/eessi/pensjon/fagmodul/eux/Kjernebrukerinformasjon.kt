package no.nav.eessi.pensjon.fagmodul.eux

data class Kjernebrukerinformasjon (
    val foedselsdato: String?,
    val fornavn: String?,
    val etternavn: String?,
    val kjoenn: String?,
    val fnr: String?,
    val saksnr: String?
)