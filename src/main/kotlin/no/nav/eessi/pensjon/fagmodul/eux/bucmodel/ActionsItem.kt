package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import no.nav.eessi.pensjon.eux.model.sed.SedType

class ActionsItem(

        val documentType: SedType? = null,
        val displayName: String? = null,
        val id: String? = null,
        var name: String? = null,
        val documentId: String? = null,
        val operation: String? = null,
)