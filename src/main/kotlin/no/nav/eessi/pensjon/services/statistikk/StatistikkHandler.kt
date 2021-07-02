package no.nav.eessi.pensjon.services.statistikk

import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class StatistikkHandler(@Value("\${ENV}") val env : String,
                        private val kafkaTemplate: KafkaTemplate<String, String>,
                        @Value("\${kafka.statistikk.topic}") private val statistikkTopic: String) {

    private val logger = LoggerFactory.getLogger(StatistikkHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    fun produserBucOpprettetHendelse(rinaid: String, dokumentId: String?) {

        val melding = StatistikkMelding(
            opprettelseType = HendelseType.BUC,
            rinaId = rinaid,
            dokumentId = dokumentId,
            vedtaksId = null
        )
        produserKafkaMelding(melding)
    }

    fun produserSedOpprettetHendelse(rinaid: String, documentId: String?, vedtaksId: String?) {
        val melding = StatistikkMelding(
            opprettelseType = HendelseType.SED,
            rinaId = rinaid,
            dokumentId = documentId,
            vedtaksId = vedtaksId
        )
        produserKafkaMelding(melding)
    }

    private fun produserKafkaMelding(melding: StatistikkMelding) {
        if(env == "q2") {
            kafkaTemplate.defaultTopic = statistikkTopic

            val key = populerMDC()

            val payload = melding.toJson()

            logger.info("Oppretter statistikk melding på kafka: ${kafkaTemplate.defaultTopic}  melding: $melding")
            kafkaTemplate.sendDefault(key, payload).get()
        }
    }

    fun populerMDC(): String = MDC.get(X_REQUEST_ID)

}

data class StatistikkMelding(
    val opprettelseType: HendelseType,
    val rinaId: String,
    val dokumentId: String?,
    val vedtaksId: String?
)

enum class HendelseType {
    BUC,
    SED
}