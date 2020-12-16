package no.nav.eessi.pensjon.services.statistikk

import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class StatistikkHandler(@Value("\${NAIS_NAMESPACE}") val nameSpace : String,
                        private val kafkaTemplate: KafkaTemplate<String, String>,
                        @Value("\${kafka.statistikk.topic}") private val statistikkTopic: String) {

    private val logger = LoggerFactory.getLogger(StatistikkHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    fun produserBucOpprettetHendelse(rinaid: String, dokumentId: String?) {

        val melding = StatistikkMelding(
            hendelseType = HendelseType.OPPRETTBUC,
            rinaid = rinaid,
            dokumentId = dokumentId
        )
        produserKafkaMelding(melding)
    }

    fun produserSedOpprettetHendelse(rinaid: String, documentId: String?) {
        val melding = StatistikkMelding(
            hendelseType = HendelseType.OPPRETTSED,
            rinaid = rinaid,
            dokumentId = documentId
        )
        produserKafkaMelding(melding)
    }

    private fun produserKafkaMelding(melding: StatistikkMelding) {
        if(nameSpace == "q2") {
            kafkaTemplate.defaultTopic = statistikkTopic

            val key = populerMDC()

            val payload = melding.toJson()

            logger.info("Oppretter statistikk melding på kafka: ${kafkaTemplate.defaultTopic}  melding: $melding")
            kafkaTemplate.sendDefault(key, payload).get()
        }
    }

    fun populerMDC() = MDC.get(X_REQUEST_ID)

}

data class StatistikkMelding(
    val hendelseType: HendelseType,
    val rinaid: String,
    val dokumentId: String?
)

enum class HendelseType {
    OPPRETTBUC,
    OPPRETTSED
}