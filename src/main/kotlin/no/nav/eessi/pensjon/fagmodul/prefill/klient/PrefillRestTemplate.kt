package no.nav.eessi.pensjon.fagmodul.prefill.klient

import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.security.token.TokenAuthorizationHeaderInterceptor
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class PrefillRestTemplate(
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val oauthPrefillRestTemplate: OauthPrefillRestTemplate? ) {

    @Value("\${EESSIPENSJON_PREFILL_URL}")
    lateinit var url: String

    @Value("\${ENV}")
    lateinit var env: String

    @Bean
    fun prefillOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return if (env == "q2") {
            oauthPrefillRestTemplate?.oathTemplate(templateBuilder)!!
        } else {
            onPremTemplate(templateBuilder)
        }
    }

    private fun onPremTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestResponseLoggerInterceptor(),
                TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }

}
