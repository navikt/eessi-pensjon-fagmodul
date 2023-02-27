package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import no.nav.eessi.pensjon.eux.model.buc.ParticipantsItem

class ConversationsItem(
        val userMessages: List<UserMessagesItem>? = null,
        val id: String? = null,
        val participants: List<ParticipantsItem?>? = null
)