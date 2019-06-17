package no.nav.eessi.eessifagmodul.services.saf

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.eessifagmodul.config.OidcAuthorizationHeaderInterceptorSelectIssuer
import no.nav.eessi.eessifagmodul.config.RequestResponseLoggerInterceptor
import no.nav.security.oidc.context.OIDCRequestContextHolder
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
class SafRestTemplate(private val oidcRequestContextHolder: OIDCRequestContextHolder,
                      private val registry: MeterRegistry) {

    @Value("\${saf.graphql.url}")
    lateinit var graphQlUrl: String

    @Value("\${saf.hentdokument.url}")
    lateinit var restUrl: String

    @Bean
    fun safGraphQlOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(graphQlUrl)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(RequestResponseLoggerInterceptor(),
                        OidcAuthorizationHeaderInterceptorSelectIssuer(oidcRequestContextHolder, "oidc"))
                .customizers(MetricsRestTemplateCustomizer(registry, DefaultRestTemplateExchangeTagsProvider(), "eessipensjon_fagmodul_safGraphQL"))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }


    @Bean
    fun safRestOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(restUrl)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(RequestResponseLoggerInterceptor(),
                        OidcAuthorizationHeaderInterceptorSelectIssuer(oidcRequestContextHolder, "oidc"))
                .customizers(MetricsRestTemplateCustomizer(registry, DefaultRestTemplateExchangeTagsProvider(), "eessipensjon_fagmodul_safRest"))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}

