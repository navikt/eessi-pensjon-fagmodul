package no.nav.eessi.pensjon.integrationtest

import io.mockk.mockk
import no.nav.eessi.pensjon.api.geo.KodeverkService
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.*
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.web.client.RestTemplate

@TestConfiguration
class IntegrasjonsTestConfig(
    @Value("\${" + EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS + "}")  private val brokerAddresses: String,
    val euxNavIdentRestTemplate: RestTemplate, val euxSystemRestTemplate: RestTemplate) {

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val configs = HashMap<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = this.brokerAddresses
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return DefaultKafkaProducerFactory(configs)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        val kafkaTemplate = KafkaTemplate(producerFactory())
        kafkaTemplate.defaultTopic = "automatiseringTopic"
        return kafkaTemplate
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val configs = HashMap<String, Any>()
        configs[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = this.brokerAddresses
        configs[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        configs[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configs[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configs[ConsumerConfig.GROUP_ID_CONFIG] = "eessi-pensjon-group-test"

        return DefaultKafkaConsumerFactory(configs)
    }

    @Bean
    @Primary
    fun kafkaTemplateTest(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory()).apply {
            defaultTopic = "test"
        }
    }
    @Bean
    fun euxNavIdentRestTemplate(): RestTemplate = mockk()
    @Bean
    fun euxSystemRestTemplate(): RestTemplate = mockk()
    @Bean
    fun euxKlient(): EuxKlientAsSystemUser = EuxKlientAsSystemUser(euxNavIdentRestTemplate, euxSystemRestTemplate)

    @Bean
    fun kodeverkService(): KodeverkService = KodeverkService(euxNavIdentRestTemplate)

}