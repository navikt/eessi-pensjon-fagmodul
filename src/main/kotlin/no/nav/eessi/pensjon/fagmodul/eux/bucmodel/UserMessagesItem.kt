package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

data class UserMessagesItem(
        val receiver: Receiver? = null,
        val sender: Sender? = null,
        val sbdh: Sbdh? = null,
        val ack: Ack? = null,
        val action: Any? = null,
        val isSent: Boolean? = null,
        val id: String? = null,
        val error: Any? = null,
        val sent: Boolean? = null,
        val status: Any? = null
)