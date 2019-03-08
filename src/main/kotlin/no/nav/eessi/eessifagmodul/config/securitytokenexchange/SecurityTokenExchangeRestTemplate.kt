package no.nav.eessi.eessifagmodul.config.securitytokenexchange

import no.nav.eessi.eessifagmodul.config.RequestResponseLoggerInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.client.support.BasicAuthenticationInterceptor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SecurityTokenExchangeRestTemplate {

    private val logger = LoggerFactory.getLogger(SecurityTokenExchangeRestTemplate::class.java)

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