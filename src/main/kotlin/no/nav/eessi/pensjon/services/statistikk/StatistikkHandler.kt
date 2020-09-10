package no.nav.eessi.pensjon.services.statistikk

import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class StatistikkHandler(private val kafkaTemplate: KafkaTemplate<String, String>,
                        @Value("\${kafka.statistikk.topic}") private val statistikkTopic: String) {

    private val logger = LoggerFactory.getLogger(StatistikkHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    fun leggOpprettBucPaaMelding(rinaid: String, bucType: String, timestamp: Long) {

        val melding = StatistikkMelding(
                hendelseType = HendelseType.OPPRETTBUC,
                rinaid = rinaid,
                bucType = bucType,
                timeStamp = timestamp
        )
        try {
            putMeldingPaaKafka(melding)
        }
        catch (e : Exception){
            logger.error(e.message, e)
        }
    }

    private fun putMeldingPaaKafka(melding: StatistikkMelding) {
        kafkaTemplate.defaultTopic = statistikkTopic

        val key = populerMDC()

        val payload = melding.toJson()

        logger.info("Opprette oppgave melding på kafka: ${kafkaTemplate.defaultTopic}  melding: $melding")
        kafkaTemplate.sendDefault(key, payload).get()
    }

    fun populerMDC() = MDC.get(X_REQUEST_ID)
}

data class StatistikkMelding(
    val hendelseType: HendelseType,
    val rinaid: String,
    val bucType: String,
    val timeStamp: Long,
    val saksNummer: String? = null,
    val vetaksId: String? = null,
    val hendelseVersjon: Int? = null
)

enum class HendelseType {
    OPPRETTBUC,
    OPPRETTSED
}