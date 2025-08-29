package no.nav.eessi.pensjon.fagmodul.api

data class FrontEndResponse<T>(
    val result: T? = null,
    val status: String? = null,
    val message: String? = null,
    val stackTrace: String? = null
)