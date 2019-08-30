package no.nav.eessi.pensjon.fagmodul.eux

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.security.oidc.OidcAuthorizationHeaderInterceptor
import no.nav.security.oidc.context.OIDCRequestContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.web.client.DefaultRestTemplateExchangeTagsProvider
import org.springframework.boot.actuate.metrics.web.client.MetricsRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class EuxRestTemplate(private val oidcRequestContextHolder: OIDCRequestContextHolder, private val registry: MeterRegistry) {

    @Value("\${eessipen-eux-rina.url}")
    lateinit var url: String

    @Bean
    fun euxOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(url)
                .errorHandler(DefaultResponseErrorHandler())
                .setReadTimeout(Duration.ofSeconds(120))
                .setConnectTimeout(Duration.ofSeconds(120))
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestCountInterceptor(registry),
                        RequestResponseLoggerInterceptor(),
                        OidcAuthorizationHeaderInterceptor(oidcRequestContextHolder))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

}
