package no.nav.eessi.eessifagmodul.services.pensjonsinformasjon

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.eessifagmodul.config.RequestResponseLoggerInterceptor
import no.nav.eessi.eessifagmodul.security.sts.STSService
import no.nav.eessi.eessifagmodul.security.sts.UsernameToOidcInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

/**
 * Rest template for PESYS pensjonsinformasjon
 */
@Component
class PensjonsinformasjonRestTemplate(private val stsService: STSService, private val registry: MeterRegistry) {

    @Value("\${pensjonsinformasjon.url}")
    lateinit var url: String

    @Bean
    fun pensjonsinformasjonOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(url)
                .additionalInterceptors(RequestResponseLoggerInterceptor(), UsernameToOidcInterceptor(stsService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}