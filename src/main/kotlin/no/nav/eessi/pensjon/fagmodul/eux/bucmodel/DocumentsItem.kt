package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import no.nav.eessi.pensjon.fagmodul.models.SEDType

class DocumentsItem(
        val attachments: List<Attachment>? = null,
        val displayName: String? = null,
        val type: SEDType? = null,
        val conversations: List<ConversationsItem>? = null,
        val isSendExecuted: Boolean? = null,
        val id: String? = null,
        val direction: String? = null,
        val creationDate: Any? = null,
        val typeVersion: String? = null,
        val allowsAttachments: Boolean? = null,
        val versions: List<VersionsItem>? = null,
        val lastUpdate: Any? = null,
        val parentDocumentId: String? = null,
        val status: String? = null,
        val participants: List<ParticipantsItem?>? = null,
        val firstVersion: VersionsItemNoUser? = null,         // I bruk av frontend
        val lastVersion: VersionsItemNoUser? = null,         // I bruk av frontend
        val version: String? = null,
        var message: String? = null
)