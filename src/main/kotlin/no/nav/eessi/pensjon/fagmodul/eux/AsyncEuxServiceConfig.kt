package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
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
class AsyncEuxServiceConfig {

    @Value("\${eessipen-eux-rina.url}")
    lateinit var url: String

    @Bean
    fun euxAsyncOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        val templateBuilder = RestTemplateBuilder()
        return templateBuilder
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                    RequestResponseLoggerInterceptor())
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }



}