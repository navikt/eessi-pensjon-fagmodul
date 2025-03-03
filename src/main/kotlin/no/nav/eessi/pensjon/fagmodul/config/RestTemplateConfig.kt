package no.nav.eessi.pensjon.fagmodul.config

import com.fasterxml.jackson.core.StreamReadConstraints
import com.nimbusds.jwt.JWTClaimsSet
import io.micrometer.core.instrument.MeterRegistry
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdOnBehalfOfTokenClient
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.fagmodul.eux.EuxErrorHandler
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
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
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*

@Configuration
@Profile("prod", "test")
class RestTemplateConfig(
        private val clientConfigurationProperties: ClientConfigurationProperties,
        private val oAuth2AccessTokenService: OAuth2AccessTokenService,
        private val tokenValidationContextHolder: TokenValidationContextHolder,
        private val meterRegistry: MeterRegistry,
        ) {

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")


    init {
        StreamReadConstraints.overrideDefaultStreamReadConstraints(
            StreamReadConstraints.builder().maxStringLength(100000000).build()
        )
    }

    @Value("\${AZURE_APP_EUX_CLIENT_ID}")
    lateinit var euxClientId: String

    @Value("\${AZURE_APP_SAF_CLIENT_ID}")
    lateinit var safClientId: String

    @Value("\${AZURE_APP_PREFILL_CLIENT_ID}")
    lateinit var prefillClientId: String

    @Value("\${AZURE_APP_PEN_CLIENT_ID}")
    lateinit var penClientId: String

    @Value("\${EESSIPEN_EUX_RINA_URL}")
    lateinit var euxUrl: String

    @Value("\${EESSIPENSJON_PREFILL_GCP_URL}")
    lateinit var prefillUrl: String

    @Value("\${PENSJONSINFORMASJON_URL}")
    lateinit var pensjonUrl: String

    @Value("\${SAF_GRAPHQL_URL}")
    lateinit var graphQlUrl: String

    @Value("\${SAF_HENTDOKUMENT_URL}")
    lateinit var hentRestUrl: String

    @Bean
    fun euxNavIdentRestTemplate(): RestTemplate = restTemplate(euxUrl, onBehalfOfBearerTokenInterceptor(euxClientId), EuxErrorHandler())

    @Bean
    fun euxSystemRestTemplate() = restTemplate(euxUrl, oAuth2BearerTokenInterceptor(clientProperties("eux-credentials"), oAuth2AccessTokenService), EuxErrorHandler())

    @Bean
    fun prefillOAuthTemplate() = restTemplate(prefillUrl, onBehalfOfBearerTokenInterceptor(prefillClientId))

    @Bean
    fun pensjoninformasjonRestTemplate() = restTemplate(pensjonUrl, oAuth2BearerTokenInterceptor(clientProperties("pensjon-credentials"), oAuth2AccessTokenService), EuxErrorHandler())

    @Bean
    fun safGraphQlOidcRestTemplate() = restTemplate(graphQlUrl, oAuth2BearerTokenInterceptor(clientProperties("saf-credentials"), oAuth2AccessTokenService))

    @Bean
    fun safRestOidcRestTemplate() = restTemplate(hentRestUrl, onBehalfOfBearerTokenInterceptor(safClientId))

    @Bean
    fun euxKlient() = EuxKlientAsSystemUser(euxNavIdentRestTemplate(), euxSystemRestTemplate())

    private fun restTemplate(url: String, tokenIntercetor: ClientHttpRequestInterceptor?, defaultErrorHandler: ResponseErrorHandler = DefaultResponseErrorHandler()) : RestTemplate {
        logger.info("init restTemplate: $url")
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(defaultErrorHandler)
            .readTimeout(Duration.ofSeconds(120))
            .connectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                IOExceptionRetryInterceptor(),
                RequestCountInterceptor(meterRegistry),
                RequestResponseLoggerInterceptor(),
                tokenIntercetor
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
       }
    }

    private fun clientProperties(oAuthKey: String): ClientProperties = clientConfigurationProperties.registration[oAuthKey]
        ?: throw RuntimeException("could not find oauth2 client config for $oAuthKey")

    private fun oAuth2BearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            response.access_token?.let { request.headers.setBearerAuth(it) }
            execution.execute(request, body!!)
        }
    }

    private fun onBehalfOfBearerTokenInterceptor(clientId: String): ClientHttpRequestInterceptor {
        logger.info("init onBehalfOfBearerTokenInterceptor: $clientId")
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val decodedToken = URLDecoder.decode(getToken(tokenValidationContextHolder).encodedToken, StandardCharsets.UTF_8)

            secureLog.info("NAVIdent: ${getClaims(tokenValidationContextHolder).get("NAVident")?.toString()}")

            val tokenClient: AzureAdOnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
                .withNaisDefaults()
                .buildOnBehalfOfTokenClient()

            val accessToken: String = tokenClient.exchangeOnBehalfOfToken(
                "api://$clientId/.default",
                decodedToken
            )
            logger.debug("accessToken: $accessToken")

            request.headers.setBearerAuth(accessToken)
            execution.execute(request, body!!)
        }
    }
}

