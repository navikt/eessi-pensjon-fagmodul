package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Buc(
        var creator: Creator? = null,
        val attachments: List<Attachment>? = null,
        val comments: List<Any>? = null,
        var subject: Subject? = null,
        val businessId: String? = null,
        val initialVariables: Any? = null,
        val processDefinitionName: String? = null,
        val sensitive: Boolean? = null,
        val sensitiveCommitted: Boolean? = null,
        val hashCode: Int? = null,
        val lastUpdate: Any? = null,
        val internationalId: String? = null,
        val id: String? = null,
        val applicationRoleId: String? = null,
        var actions: List<ActionsItem>? = null,
        val startDate: Any? = null,
        val processDefinitionVersion: String? = null,
        val properties: Properties? = null,
        val status: String? = null,
        var participants: List<ParticipantsItem>? = null,
        var documents: List<DocumentsItem>? = null
)
