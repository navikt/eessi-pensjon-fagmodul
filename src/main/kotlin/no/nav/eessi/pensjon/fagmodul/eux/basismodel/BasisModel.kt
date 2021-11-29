package no.nav.eessi.pensjon.fagmodul.eux.basismodel

import no.nav.eessi.pensjon.eux.model.SedType

/**
 * data class model from EUX Basis
 */

class RinaAksjon(
        val dokumentType: SedType? = null,
        val navn: String? = null,
        val dokumentId: String? = null,
        val kategori: String? = null,
        val id: String? = null
)

data class BucSedResponse(
        val caseId: String,
        val documentId: String
)

class Rinasak(
        val id: String? = null,
        val processDefinitionId: String? = null,
        val traits: Traits? = null,
        val applicationRoleId: String? = null,
        val properties: Properties? = null,
        val status: String? = null
)

class Properties(
        val importance: String? = null,
        val criticality: String? = null
)

class Traits(
        val birthday: String? = null,
        val localPin: String? = null,
        val surname: String? = null,
        val caseId: String? = null,
        val name: String? = null,
        val flowType: String? = null,
        val status: String? = null
)

class SingleBucSedViewRequest(
        val euxCaseId: String,
        val aktoerId: String,
        val saknr: String,
        val avodnr: String? = null
)
