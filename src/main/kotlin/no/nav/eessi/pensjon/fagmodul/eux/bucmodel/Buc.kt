package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.buc.ParticipantsItem

@JsonIgnoreProperties(ignoreUnknown = true)
class Buc(
        var creator: Creator? = null,
        val attachments: List<Attachment>? = null,
        // I bruk av frontend
        val comments: List<Any>? = null,
        // I bruk av frontend
        var subject: Subject? = null,
        val processDefinitionName: String? = null,
        val lastUpdate: Any? = null,
        val internationalId: String? = null,
        val id: String? = null,
        var actions: List<ActionsItem>? = null,
        val startDate: Any? = null,
        val processDefinitionVersion: String? = null,
        val status: String? = null,
        var participants: List<ParticipantsItem>? = null,
        var documents: List<DocumentsItem>? = null
)
