package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import no.nav.eessi.pensjon.fagmodul.models.SEDType

class ActionsItem(

        val documentType: SEDType? = null,
        val displayName: String? = null,
        val id: String? = null,
        var name: String? = null,
        val documentId: String? = null,
        val operation: String? = null,
)