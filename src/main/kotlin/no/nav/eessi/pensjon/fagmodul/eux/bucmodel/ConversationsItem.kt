package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

data class ConversationsItem(
        val date: Any? = null,
        val versionId: Any? = null,
        val userMessages: List<UserMessagesItem>? = null,
        val id: String? = null,
        val participants: List<ParticipantsItem?>? = null
)