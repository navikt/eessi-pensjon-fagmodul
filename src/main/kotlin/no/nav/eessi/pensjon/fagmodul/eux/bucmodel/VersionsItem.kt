package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

data class VersionsItem(

        val date: Any? = null,
        val id: String? = null,
        val user: User? = null
)

data class VersionsItemNoUser(

        val date: Any? = null,
        val id: String? = null
)
