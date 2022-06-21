package no.nav.eessi.pensjon.services.statistikk

import no.nav.eessi.pensjon.eux.model.SedType
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
    private val XREQUESTID = "x_request_id"

    fun produserBucOpprettetHendelse(rinaid: String, dokumentId: String?) {
        try {
            val melding = StatistikkMelding(
                opprettelseType = HendelseType.BUC,
                rinaId = rinaid,
                dokumentId = dokumentId,
                vedtaksId = null,
                sedtype = null
            )
            produserKafkaMelding(melding)
        } catch (ex: Exception) {
            logger.warn("Klarte ikke å opprette melding på kafka: $rinaid, hendelse: BUC")
        }

    }

    fun produserSedOpprettetHendelse(rinaid: String, documentId: String?, vedtaksId: String?, sedtype: SedType? = null) {
        try {
            val melding = StatistikkMelding(
                opprettelseType = HendelseType.SED,
                rinaId = rinaid,
                dokumentId = documentId,
                vedtaksId = vedtaksId,
                sedtype =  sedtype
            )
            produserKafkaMelding(melding)

        } catch (ex: Exception) {
            logger.warn("Klarte ikke å opprette melding på kafka: $rinaid, hendelse: SED")
        }
    }

    private fun produserKafkaMelding(melding: StatistikkMelding) {
            kafkaTemplate.defaultTopic = statistikkTopic

            val key = populerMDC()

            val payload = melding.toJson()

            logger.info("Oppretter statistikk melding på kafka: ${kafkaTemplate.defaultTopic}  melding: $melding")
            try {
                kafkaTemplate.sendDefault(key, payload).get()
            } catch (exception: Exception) {
                logger.error(exception.message)
                logger.error(exception.printStackTrace().toString())
            }
        }

    fun populerMDC(): String = MDC.get(XREQUESTID)

}

data class StatistikkMelding(
    val opprettelseType: HendelseType,
    val rinaId: String,
    val dokumentId: String?,
    val vedtaksId: String?,
    val sedtype: SedType?
)

enum class HendelseType {
    BUC,
    SED
}