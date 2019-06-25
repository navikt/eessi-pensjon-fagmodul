package no.nav.eessi.eessifagmodul.services.eux

/**
 * data class model from EUX Basis
 */

data class RinaAksjon(
        val dokumentType: String? = null,
        val navn: String? = null,
        val dokumentId: String? = null,
        val kategori: String? = null,
        val id: String? = null
)

data class BucSedResponse(
        val caseId: String,
        val documentId: String
)

data class Rinasak(
        val id: String? = null,
        val processDefinitionId: String? = null,
        val traits: Traits? = null,
        val applicationRoleId: String? = null,
        val properties: Properties? = null,
        val status: String? = null
)

data class Properties(
        val importance: String? = null,
        val criticality: String? = null
)

data class Traits(
        val birthday: String? = null,
        val localPin: String? = null,
        val surname: String? = null,
        val caseId: String? = null,
        val name: String? = null,
        val flowType: String? = null,
        val status: String? = null
)

data class Vedlegg(
        val Filnavn: String,
        val file: String
)
