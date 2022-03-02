package no.nav.eessi.pensjon.fagmodul.prefill.klient

import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.security.token.TokenAuthorizationHeaderInterceptor
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    private val oauthPrefillRestTemplate: OauthPrefillRestTemplate?
    ) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillRestTemplate::class.java) }

    @Value("\${EESSIPENSJON_PREFILL_URL}")
    lateinit var url: String

    @Value("\${ENV}")
    lateinit var env: String

    @Bean
    fun prefillOidcRestTemplate(): RestTemplate {
        return if (env == "q2") {
            logger.debug("oauthPrefillRestTemplate selected")
            oauthPrefillRestTemplate?.oathTemplate()!!
        } else {
            logger.debug("onPremTemplate selected")
            onPremTemplate()
        }
//       return onPremTemplate(templateBuilder)

    }

    private fun onPremTemplate(): RestTemplate {
        return RestTemplateBuilder()
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
