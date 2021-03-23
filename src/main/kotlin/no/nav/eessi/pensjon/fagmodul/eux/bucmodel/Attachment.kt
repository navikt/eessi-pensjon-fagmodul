package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

class Attachment(
        val fileName: String? = null,
        val medical: Boolean? = null,
        val mimeType: String? = null,
        val lastUpdate: Any? = null,
        val name: String? = null,
        val documentId: String? = null,
        val id: String? = null,
)

class ShortAttachment(
        val id: String? = null,
        val name: String? = null,
        val fileName: String? = null,
        val mimeType: String? = null,
        val documentId: String? = null,
        val lastUpdate: Any? = null,
        val medical: Boolean? = null
)