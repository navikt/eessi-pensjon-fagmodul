package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

data class Attachment(
        val creator: Creator? = null,
        val fileName: String? = null,
        val medical: Boolean? = null,
        val mimeType: String? = null,
        val creationDate: Any? = null,
        val internalId: Any? = null,
        val versions: List<VersionsItem?>? = null,
        val hasMultipleVersions: Boolean? = null,
        val lastUpdate: Any? = null,
        val caseId: String? = null,
        val name: String? = null,
        val documentId: String? = null,
        val id: String? = null,
        val parentDocumentId: Any? = null
)

data class ShortAttachment(
        val id: String? = null,
        val name: String? = null,
        val fileName: String? = null,
        val mimeType: String? = null,
        val documentId: String? = null,
        val lastUpdate: Long? = null,
        val medical: Boolean? = null
)