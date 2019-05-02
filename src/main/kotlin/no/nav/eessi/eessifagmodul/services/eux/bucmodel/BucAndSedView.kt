package no.nav.eessi.eessifagmodul.services.eux.bucmodel

import no.nav.eessi.eessifagmodul.models.InstitusjonItem

data class BucAndSedView(
        val buc: String,
        val creator: InstitusjonItem,
        val caseId: String,
        val sakType: String? = null,
        val aktoerId: String,
        val status: String? = null,
        val institusjon: List<InstitusjonItem>,
        val seds: List<ShortDocumentItem>
)


