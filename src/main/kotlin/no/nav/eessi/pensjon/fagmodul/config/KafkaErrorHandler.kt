package no.nav.eessi.pensjon.fagmodul.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.io.StringWriter


@Profile("prod")
@Component
class KafkaErrorHandler : CommonErrorHandler {
    private val logger = LoggerFactory.getLogger(KafkaErrorHandler::class.java)

    private val stopper = CommonContainerStoppingErrorHandler()

    override fun handleRecord(
        thrownException: Exception,
        record: ConsumerRecord<*, *>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer) {

        val stacktrace = StringWriter()
        //thrownException.printStackTrace(PrintWriter(stacktrace))

        logger.error("En feil oppstod under kafka konsumering av meldinger: \n ${hentMeldinger(record)} \n" +
                "Stopper containeren ! Restart er nødvendig for å fortsette konsumering, $stacktrace")
        stopper.handleRecord(thrownException, record, consumer, container)

    }

//    override fun handle(
//        thrownException: java.lang.Exception,
//        records: MutableList<ConsumerRecord<*, *>>?,
//        consumer: Consumer<*, *>,
//        container: MessageListenerContainer
//    ) {
//        val stacktrace = StringWriter()
//        thrownException.printStackTrace(PrintWriter(stacktrace))
//
//        logger.error("En feil oppstod under kafka konsumering av meldinger: \n ${hentMeldinger(records)} \n" +
//                "Stopper containeren ! Restart er nødvendig for å fortsette konsumering, $stacktrace")
//        stopper.handle(thrownException, records, consumer, container)
//    }

    fun hentMeldinger(records: ConsumerRecord<*, *>): String {
//        records?.forEach { it ->
//            meldinger += it.toString()
//            meldinger += "\n"
//        }

        var meldinger = ""
        meldinger += "--------------------------------------------------------------------------------\n"
        records.topic().toString()
        return meldinger
    }

}
