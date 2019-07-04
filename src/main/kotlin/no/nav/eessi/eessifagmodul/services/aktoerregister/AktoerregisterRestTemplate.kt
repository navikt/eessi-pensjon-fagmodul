package no.nav.eessi.eessifagmodul.services.aktoerregister

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.eessifagmodul.security.sts.STSService
import no.nav.eessi.eessifagmodul.security.sts.UsernameToOidcInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.web.client.DefaultRestTemplateExchangeTagsProvider
import org.springframework.boot.actuate.metrics.web.client.MetricsRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate

@Component
class AktoerregisterRestTemplate(private val stsService: STSService,
                                 private val registry: MeterRegistry) {

    @Value("\${aktoerregister.api.v1.url}")
    lateinit var url: String

    @Bean
    fun aktoerregisterOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(url)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(UsernameToOidcInterceptor(stsService))
                .customizers(MetricsRestTemplateCustomizer(registry, DefaultRestTemplateExchangeTagsProvider(), "eessipensjon_fagmodul_aktoer"))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}