package no.nav.eessi.eessifagmodul.services.eux.bucmodel

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class DocumentsItem(
        val parendDocumentId: Any? = null,
        val attachments: List<Attachment>? = null,
        val sendExecuted: Boolean? = null,
        val displayName: String? = null,
        val admin: Boolean? = null,
        val isDummyDocument: Boolean? = null,
        val mimeType: String? = null,
        val firstDocument: Boolean? = null,
        val type: String? = null,
        val conversations: List<ConversationsItem>? = null,
        val isSendExecuted: Boolean? = null,
        val hasMultipleVersions: Boolean? = null,
        val hasBusinessValidation: Boolean? = null,
        val parentType: String? = null,
        val id: String? = null,
        val hasCancel: Boolean? = null,
        val validation: Validation? = null,
        val direction: String? = null,
        val order: Int? = null,
        val hasLetter: Boolean? = null,
        val creator: Creator? = null,
        val hasReplyClarify: Boolean? = null,
        val hasReject: Boolean? = null,
        val comments: List<Any?>? = null,
        val starter: Boolean? = null,
        val mlc: Boolean? = null,
        val DMProcessId: String? = null,
        val isMLC: Boolean? = null,
        val selectParticipants: Boolean? = null,
        val isAdmin: Boolean? = null,
        val subProcessId: Int? = null,
        val creationDate: Any? = null,
        val hasClarify: Boolean? = null,
        val createTemplate: Any? = null,
        val tags: Any? = null,
        val toSenderOnly: Boolean? = null,
        val typeVersion: String? = null,
        val allowsAttachments: Boolean? = null,
        val versions: List<VersionsItem>? = null,
        val lastUpdate: Any? = null,
        val dummyDocument: Boolean? = null,
        val name: Any? = null,
        val canBeSentWithoutChild: Boolean? = null,
        val bulk: Boolean? = null,
        val parentDocumentId: String? = null,
        val isFirstDocument: Boolean? = null,
        val status: String? = null,
        @JsonIgnore
        val dmprocessId: Any? = null
)

data class ShortDocumentItem(
        val id: String? = null,
        val type: String? = null,
        val status: String? = null,
        val creationDate: Any? = null,
        val lastUpdate: LocalDate? = null,
        val attachments: List<ShortAttachment>? = null
)