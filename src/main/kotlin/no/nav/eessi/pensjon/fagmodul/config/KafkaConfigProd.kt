package no.nav.eessi.pensjon.fagmodul.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.JsonDeserializer
import java.time.Duration

@EnableKafka
@Profile("prod")
@Configuration
class KafkaConfigProd(
    @param:Value("\${kafka.keystore.path}") private val keystorePath: String,
    @param:Value("\${kafka.credstore.password}") private val credstorePassword: String,
    @param:Value("\${kafka.truststore.path}") private val truststorePath: String,
    @param:Value("\${kafka.brokers}") private val aivenBootstrapServers: String,
    @param:Value("\${kafka.security.protocol}") private val securityProtocol: String,
    @param:Value("\${ONPREM_KAFKA_BOOTSTRAP_SERVERS_URL}") private val onpremBootstrapServers: String,
    @param:Value("\${srvusername}") private val srvusername: String,
    @param:Value("\${srvpassword}") private val srvpassword: String,
    @Autowired private val kafkaErrorHandler: KafkaErrorHandler?,
    @Value("\${KAFKA_AUTOMATISERING_TOPIC}") private val automatiseringTopic: String
) {

    @Bean
    fun aivenProducerFactory(): ProducerFactory<String, String> {
        val configMap: MutableMap<String, Any> = HashMap()
        populerAivenCommonConfig(configMap)
        configMap[ProducerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-fagmodul"
        configMap[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configMap[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configMap[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = aivenBootstrapServers
        return DefaultKafkaProducerFactory(configMap)
    }

    @Bean
    fun aivenKafkaTemplate(): KafkaTemplate<String, String> {
        val kafkaTemplate = KafkaTemplate(aivenProducerFactory())
        kafkaTemplate.defaultTopic = automatiseringTopic
        return kafkaTemplate
    }

    fun aivenKafkaConsumerFactory(): ConsumerFactory<String, String> {
        val keyDeserializer: JsonDeserializer<String> = JsonDeserializer(String::class.java)
        keyDeserializer.setRemoveTypeHeaders(true)
        keyDeserializer.addTrustedPackages("*")
        keyDeserializer.setUseTypeHeaders(false)

        val valueDeserializer = StringDeserializer()

        val configMap: MutableMap<String, Any> = HashMap()
        populerAivenCommonConfig(configMap)
        configMap[ConsumerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-fagmodul"
        configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = aivenBootstrapServers
        configMap[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false

        return DefaultKafkaConsumerFactory(configMap, keyDeserializer, valueDeserializer)
    }

    @Bean
    fun aivenKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = aivenKafkaConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.authorizationExceptionRetryInterval =  Duration.ofSeconds(4L)
        if (kafkaErrorHandler != null) {
            factory.setErrorHandler(kafkaErrorHandler)
        }
        return factory
    }

    private fun populerAivenCommonConfig(configMap: MutableMap<String, Any>) {
        configMap[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystorePath
        configMap[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = credstorePassword
        configMap[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = credstorePassword
        configMap[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = credstorePassword
        configMap[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
        configMap[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
        configMap[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststorePath
        configMap[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = securityProtocol
    }

    private fun populerOnpremCommonConfig(configMap: MutableMap<String, Any>) {
        configMap[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
        configMap[SaslConfigs.SASL_MECHANISM] = "PLAIN"
        configMap[SaslConfigs.SASL_JAAS_CONFIG] = "org.apache.kafka.common.security.plain.PlainLoginModule required username='${srvusername}' password='${srvpassword}';"
    }

}