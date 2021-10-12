package no.nav.eessi.pensjon.services.statistikk

import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class StatistikkHandler(@Value("\${ENV}") val env : String,
                        private val aivenKafkaTemplate: KafkaTemplate<String, String>,
                        @Value("\${kafka.statistikk.topic}") private val statistikkTopic: String) {

    private val logger = LoggerFactory.getLogger(StatistikkHandler::class.java)
    private val XREQUESTID = "x_request_id"

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
            aivenKafkaTemplate.defaultTopic = statistikkTopic

            val key = populerMDC()

            val payload = melding.toJson()

            logger.info("Oppretter statistikk melding på kafka: ${aivenKafkaTemplate.defaultTopic}  melding: $melding")
            try {
                aivenKafkaTemplate.sendDefault(key, payload).get()
            } catch (exception: Exception) {
                logger.error(exception.message)
                logger.error(exception.printStackTrace().toString())
            }
        }
    }

    fun populerMDC(): String = MDC.get(XREQUESTID)

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