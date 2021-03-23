package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

class Sender (
        acronym: String? = null,
        countryCode: String? = null,
        name: String? = null,
        id: String? = null,
        val identifier: String? = null,
        val contactTypeIdentifier: String? = null,
        val authority: String? = null
): Organisation(
        acronym,
        countryCode,
        name,
        id
)
