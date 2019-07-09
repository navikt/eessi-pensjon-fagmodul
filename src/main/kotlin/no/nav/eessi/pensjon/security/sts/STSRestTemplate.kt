package no.nav.eessi.pensjon.security.sts

import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.client.support.BasicAuthenticationInterceptor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

/**
 * STS rest template for å hente OIDCtoken for nye tjenester
 *
 */
@Component
class STSRestTemplate {

    private val logger = LoggerFactory.getLogger(STSRestTemplate::class.java)

    @Value("\${security-token-service-token.url}")
    lateinit var baseUrl: String

    @Value("\${srveessipensjon.username}")
    lateinit var username: String

    @Value("\${srveessipensjon.password}")
    lateinit var password: String

    @Bean
    fun securityTokenExchangeBasicAuthRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        logger.info("Oppretter RestTemplate for: $baseUrl")
        return templateBuilder
                .rootUri(baseUrl)
                .additionalInterceptors(RequestResponseLoggerInterceptor())
                .additionalInterceptors(BasicAuthenticationInterceptor(username, password))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}