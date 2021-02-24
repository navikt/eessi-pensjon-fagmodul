package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

class UserMessagesItem(
        val receiver: Receiver? = null,
        val sender: Sender? = null,
        val sbdh: Sbdh? = null,
        val ack: Ack? = null,
        val action: Any? = null,
        val isSent: Boolean? = null,
        val id: String? = null,
        val error: Any? = null,
        val status: Any? = null
)