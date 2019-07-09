package no.nav.eessi.pensjon.fagmodul.person.models

/**
 * Personinformasjon fra TPS ( PersonV3 )
 */
data class Personinformasjon(var fulltNavn: String? = null,
                             var fornavn: String? = null,
                             var mellomnavn: String? = null,
                             var etternavn: String? = null)
