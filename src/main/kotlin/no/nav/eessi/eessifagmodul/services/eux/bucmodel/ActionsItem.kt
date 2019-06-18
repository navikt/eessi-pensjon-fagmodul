package no.nav.eessi.eessifagmodul.services.eux.bucmodel

data class ActionsItem(

        val template: String? = null,
        val documentType: String? = null,
        val isDocumentRelated: Boolean? = null,
        val displayName: String? = null,
        val caseRelated: Boolean? = null,
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
        val documentRelated: Boolean? = null,
        val tags: Tags? = null,
        val actor: String? = null,
        val typeVersion: String? = null,
        var name: String? = null,
        val documentId: String? = null,
        val actionGroup: ActionGroup? = null,
        val bulk: Boolean? = null,
        val isBulk: Boolean? = null,
        val operation: String? = null,
        val parentDocumentId: Any? = null,
        val status: Any? = null
)