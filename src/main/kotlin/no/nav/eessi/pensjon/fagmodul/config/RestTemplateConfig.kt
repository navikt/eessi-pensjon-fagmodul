package no.nav.eessi.pensjon.fagmodul.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdOnBehalfOfTokenClient
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.utils.getClaims
import no.nav.eessi.pensjon.utils.getToken
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpRequest
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration


@Configuration
@Profile("prod", "test")
class RestTemplateConfig(
        @Value("\${ENV}") private val environment: String,
        private val clientConfigurationProperties: ClientConfigurationProperties,
        private val oAuth2AccessTokenService: OAuth2AccessTokenService,
        private val tokenValidationContextHolder: TokenValidationContextHolder,
        private val meterRegistry: MeterRegistry,
        ) {

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Value("\${AZURE_APP_EUX_CLIENT_ID}")
    lateinit var euxClientId: String

    @Value("\${AZURE_APP_SAF_CLIENT_ID}")
    lateinit var safClientId: String

    @Value("\${AZURE_APP_PREFILL_CLIENT_ID}")
    lateinit var prefillClientId: String

    @Value("\${EESSIPEN_EUX_RINA_URL}")
    lateinit var euxUrl: String

    @Value("\${EESSI_PEN_ONPREM_PROXY_URL}")
    lateinit var proxyUrl: String

    @Value("\${EESSIPENSJON_PREFILL_GCP_URL}")
    lateinit var prefillUrl: String

    @Value("\${PENSJONSINFORMASJON_URL}")
    lateinit var pensjonUrl: String

    @Value("\${SAF_GRAPHQL_URL}")
    lateinit var graphQlUrl: String

    @Value("\${SAF_HENTDOKUMENT_URL}")
    lateinit var hentRestUrl: String

    @Bean
    fun euxNavIdentRestTemplate(): RestTemplate = restTemplate(euxUrl, onBehalfOfBearerTokenInterceptor(euxClientId))

    @Bean
    fun euxSystemRestTemplate() = restTemplate(euxUrl, oAuth2BearerTokenInterceptor(clientProperties("eux-credentials"), oAuth2AccessTokenService))

    @Bean
    fun proxyOAuthRestTemplate() = restTemplate(proxyUrl, oAuth2BearerTokenInterceptor(clientProperties("proxy-credentials"), oAuth2AccessTokenService))

    @Bean
    fun prefillOAuthTemplate() = restTemplate(prefillUrl, onBehalfOfBearerTokenInterceptor(prefillClientId))

    @Bean
    fun pensjoninformasjonRestTemplate() = restTemplate(pensjonUrl, oAuth2BearerTokenInterceptor(clientProperties("proxy-credentials"), oAuth2AccessTokenService))

    @Bean
    fun safGraphQlOidcRestTemplate() = restTemplate(graphQlUrl, oAuth2BearerTokenInterceptor(clientProperties("saf-credentials"), oAuth2AccessTokenService))

    @Bean
    fun safRestOidcRestTemplate() = restTemplate(hentRestUrl, onBehalfOfBearerTokenInterceptor(safClientId))


    private fun restTemplate(url: String, tokenIntercetor: ClientHttpRequestInterceptor?) : RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestCountInterceptor(meterRegistry),
                RequestResponseLoggerInterceptor(),
                tokenIntercetor
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()
                    .apply { setOutputStreaming(false) }
                )
       }
    }


    private fun clientProperties(oAuthKey: String): ClientProperties = clientConfigurationProperties.registration[oAuthKey]
        ?: throw RuntimeException("could not find oauth2 client config for $oAuthKey")

    private fun oAuth2BearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor? {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
            execution.execute(request, body!!)
        }
    }

    private fun onBehalfOfBearerTokenInterceptor(clientId: String): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val navidentTokenFromUI = getToken(tokenValidationContextHolder).tokenAsString

            if (environment == "q2") {
                logger.debug("obot : $navidentTokenFromUI")
            }

            logger.info("NAVIdent: ${getClaims(tokenValidationContextHolder).get("NAVident")?.toString()}")

            val tokenClient: AzureAdOnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
                .withNaisDefaults()
                .buildOnBehalfOfTokenClient()

            val accessToken: String = tokenClient.exchangeOnBehalfOfToken(
                "api://$clientId/.default",
                navidentTokenFromUI
            )

            request.headers.setBearerAuth(accessToken)
            execution.execute(request, body!!)
        }

    }

}

