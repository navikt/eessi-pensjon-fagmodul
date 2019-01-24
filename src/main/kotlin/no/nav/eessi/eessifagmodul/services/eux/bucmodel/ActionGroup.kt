package no.nav.eessi.eessifagmodul.services.eux.bucmodel

data class ActionGroup(

        val type: String? = null,
        val parentDocId: Any? = null,
        val dMProcessId: String? = null,
        val hasLocalClose: Boolean? = null,
        val documentId: String? = null,
        val parentType: String? = null,
        val activityInstanceId: Any? = null,
        val operation: String? = null
)