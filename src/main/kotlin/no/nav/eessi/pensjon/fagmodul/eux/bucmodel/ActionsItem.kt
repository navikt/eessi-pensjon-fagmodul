package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import no.nav.eessi.pensjon.fagmodul.models.SEDType

class ActionsItem(

        val template: String? = null,
        val documentType: SEDType? = null,
        val isDocumentRelated: Boolean? = null,
        val displayName: String? = null,
        val type: String? = null,
        val subdocumentId: Any? = null,
        val poolGroup: PoolGroup? = null,
        val isCaseRelated: Boolean? = null,
        val hasBusinessValidation: Boolean? = null,
        val caseId: String? = null,
        val id: String? = null,
        val canClose: Boolean? = null,
        val requiresValidDocument: Boolean? = null,
        val tempDocumentId: String? = null,
        val hasSendValidationOnBulk: Boolean? = null,
        val tags: Tags? = null,
        val actor: String? = null,
        val typeVersion: String? = null,
        var name: String? = null,
        val documentId: String? = null,
        val actionGroup: ActionGroup? = null,
        val isBulk: Boolean? = null,
        val operation: String? = null,
        val parentDocumentId: Any? = null,
        val status: Any? = null
)