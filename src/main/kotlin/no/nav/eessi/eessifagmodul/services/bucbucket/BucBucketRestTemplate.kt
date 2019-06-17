package no.nav.eessi.eessifagmodul.services.bucbucket

import no.nav.eessi.eessifagmodul.config.OidcAuthorizationHeaderInterceptor
import no.nav.eessi.eessifagmodul.config.RequestResponseLoggerInterceptor
import no.nav.security.oidc.context.OIDCRequestContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate

//@Component
@Deprecated(replaceWith = ReplaceWith("Nothing"), level = DeprecationLevel.WARNING, message = "Utgår")
class BucBucketRestTemplate(val oidcRequestContextHolder: OIDCRequestContextHolder) {

    // TODO: Fjern default-verdien når eux-bucbucket eksponerer ressursen i fasit
    @Value("\${bucbucket.v1.url:https://eux-bucbucket.nais.preprod.local/bucBucket/v1}")
    lateinit var url: String

    @Bean
    fun bucBucketOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(url)
                .errorHandler(DefaultResponseErrorHandler())
                //.additionalInterceptors(RequestResponseLoggerInterceptor(), OidcAuthorizationHeaderInterceptor(oidcRequestContextHolder))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }
}