package no.nav.eessi.pensjon.fagmodul.config

import com.nimbusds.jwt.JWTClaimsSet
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdOnBehalfOfTokenClient
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.jwt.JwtTokenClaims
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
import java.util.*


@Configuration
@Profile("prod", "test")
class RestTemplateConfig(
    private val clientConfigurationProperties: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    private val tokenValidationContextHolder: TokenValidationContextHolder) {

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Value("\${AZURE_APP_EUX_CLIENT_ID}")
    lateinit var euxClientId: String

    @Value("\${EESSIPEN_EUX_RINA_URL}")
    lateinit var euxUrl: String

    @Value("\${EESSI_PEN_ONPREM_PROXY_URL}")
    lateinit var proxyUrl: String

    @Value("\${EESSIPENSJON_PREFILL_GCP_URL}")
    lateinit var prefillUrl: String

    @Value("\${KODEVERK_REST_API_URL}")
    private lateinit var kodeverkUrl: String

    @Value("\${PENSJONSINFORMASJON_URL}")
    lateinit var pensjonUrl: String

    @Value("\${SAF_GRAPHQL_URL}")
    lateinit var graphQlUrl: String

    @Value("\${SAF_HENTDOKUMENT_URL}")
    lateinit var hentRestUrl: String

    //Dette var den gamle euxOidcResttemplaten
    @Bean
    fun euxNavIdentRestTemplate(): RestTemplate = restTemplate(euxUrl, euxNavIdenBearerTokenInterceptor(clientProperties("eux-credentials"), oAuth2AccessTokenService!!))

    //Dette var den gamle euxUsernameOidcRestTemplate
    @Bean
    fun euxSystemRestTemplate() = restTemplate(euxUrl, bearerTokenInterceptor(clientProperties("eux-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun proxyOAuthRestTemplate() = restTemplate(proxyUrl, bearerTokenInterceptor(clientProperties("proxy-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun prefillOAuthTemplate() = restTemplate(prefillUrl, bearerTokenInterceptor(clientProperties("prefill-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun kodeRestTemplate() = restTemplate(kodeverkUrl, bearerTokenInterceptor(clientProperties("proxy-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun pensjonsinformasjonOidcRestTemplate() = restTemplate(pensjonUrl, bearerTokenInterceptor(clientProperties("proxy-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun safGraphQlOidcRestTemplate() = restTemplate(graphQlUrl, bearerTokenInterceptor(clientProperties("saf-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun safRestOidcRestTemplate() = restTemplate(hentRestUrl, bearerTokenInterceptor(clientProperties("saf-credentials"), oAuth2AccessTokenService!!))


    private fun restTemplate(url: String, tokenIntercetor: ClientHttpRequestInterceptor?) : RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
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

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor? {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
//            val tokenChunks = response.accessToken.split(".")
//            val tokenBody =  tokenChunks[1]
//            logger.info("subject: " + JWTClaimsSet.parse(Base64.getDecoder().decode(tokenBody).decodeToString()).subject)
            execution.execute(request, body!!)
        }
    }

    private fun euxNavIdenBearerTokenInterceptor(clientProperties: ClientProperties, oAuth2AccessTokenService: OAuth2AccessTokenService): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            try {
                logger.info("NAVIdent: ${getClaims(tokenValidationContextHolder).get("NAVident")?.toString()}")
                logger.info("token: ${getToken(tokenValidationContextHolder).tokenAsString}")
            } catch (ex: Exception) { }

            val tokenClient: AzureAdOnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
                .withNaisDefaults()
                .buildOnBehalfOfTokenClient()

            val accessToken: String = tokenClient.exchangeOnBehalfOfToken(
                "api://$euxClientId/.default",
                "<access_token>"
            )

            logger.info("NAVIdent til eux: ${JWTClaimsSet.parse(accessToken).claims.get("NAVident")?.toString()} ")
            logger.info("On Behalf accessToken: $accessToken")

//            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
//            request.headers.setBearerAuth(response.accessToken)
            request.headers.setBearerAuth(accessToken)
            execution.execute(request, body!!)
        }
    }

    private fun getClaims(tokenValidationContextHolder: TokenValidationContextHolder): JwtTokenClaims {
        val context = tokenValidationContextHolder.tokenValidationContext
        if(context.issuers.isEmpty())
            throw RuntimeException("No issuer found in context")

        val validIssuer = context.issuers.filterNot { issuer ->
            val oidcClaims = context.getClaims(issuer)
            oidcClaims.expirationTime.before(Date())
        }.map { it }


        if (validIssuer.isNotEmpty()) {
            val issuer = validIssuer.first()
            return context.getClaims(issuer)
        }
        throw RuntimeException("No valid issuer found in context")

    }

    private fun getToken(tokenValidationContextHolder: TokenValidationContextHolder): JwtToken {
        val context = tokenValidationContextHolder.tokenValidationContext
        if(context.issuers.isEmpty())
            throw RuntimeException("No issuer found in context")
        val issuer = context.issuers.first()

        return context.getJwtToken(issuer)
    }

}

