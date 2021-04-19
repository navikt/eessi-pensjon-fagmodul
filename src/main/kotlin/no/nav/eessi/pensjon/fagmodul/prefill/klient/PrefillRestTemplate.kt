package no.nav.eessi.pensjon.fagmodul.prefill.klient

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
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
    private val registry: MeterRegistry,
) {

    @Value("\${EESSIPENSJON_PREFILL_URL}")
    lateinit var url: String

    @Bean
    fun prefillOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(url)
                .errorHandler(DefaultResponseErrorHandler())
                .setReadTimeout(Duration.ofSeconds(120))
                .setConnectTimeout(Duration.ofSeconds(120))
                .additionalInterceptors(
                    RequestIdHeaderInterceptor(),
                    RequestCountInterceptor(registry),
                    RequestResponseLoggerInterceptor(),
                    TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}
