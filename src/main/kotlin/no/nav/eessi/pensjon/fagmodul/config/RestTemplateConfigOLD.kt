package no.nav.eessi.pensjon.fagmodul.config
//
//import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
//import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
//import no.nav.eessi.pensjon.security.sts.STSService
//import no.nav.eessi.pensjon.security.sts.UsernameToOidcInterceptor
//import no.nav.eessi.pensjon.security.token.TokenAuthorizationHeaderInterceptor
//import no.nav.security.token.support.client.core.ClientProperties
//import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
//import no.nav.security.token.support.client.spring.ClientConfigurationProperties
//import no.nav.security.token.support.core.context.TokenValidationContextHolder
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.boot.web.client.RestTemplateBuilder
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.context.annotation.Profile
//import org.springframework.http.HttpRequest
//import org.springframework.http.client.BufferingClientHttpRequestFactory
//import org.springframework.http.client.ClientHttpRequestExecution
//import org.springframework.http.client.ClientHttpRequestInterceptor
//import org.springframework.http.client.SimpleClientHttpRequestFactory
//import org.springframework.web.client.DefaultResponseErrorHandler
//import org.springframework.web.client.RestTemplate
//import java.time.Duration
//
//@Configuration
//class RestTemplateConfigOLD(
//    private val tokenValidationContextHolder: TokenValidationContextHolder,
//    private val stsService: STSService,
//    private val clientConfigurationProperties: ClientConfigurationProperties,
//    private val oAuth2AccessTokenService: OAuth2AccessTokenService?) {
//
//
//    @Value("\${eessipen-eux-rina.url}")
//    lateinit var euxUrl: String
//
//    @Value("\${EESSIPENSJON_PREFILL_GCP_URL}")
//    lateinit var prefillUrl: String
//
//    @Value("\${kodeverk.rest-api.url}")
//    private lateinit var kodeverkUrl: String
//
//    @Value("\${pensjonsinformasjon.url}")
//    lateinit var pensjonUrl: String
//
//    @Value("\${saf.graphql.url}")
//    lateinit var graphQlUrl: String
//
//    @Value("\${saf.hentdokument.url}")
//    lateinit var hentRestUrl: String
//
//
//    @Bean
//    fun euxOidcRestTemplate() = restTemplate(euxUrl, TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
//
//    @Bean
//    fun euxUsernameOidcRestTemplate() = restTemplate(euxUrl, UsernameToOidcInterceptor(stsService))
//
//    @Bean
//    @Profile("prod", "test")
//    fun prefillOAuthTemplate() = restTemplate(prefillUrl, bearerTokenInterceptor(clientProperties("prefill-credentials"), oAuth2AccessTokenService!!))
//
//    @Bean
//    fun kodeRestTemplate() = restTemplate(kodeverkUrl)
//
//    @Bean
//    fun pensjonsinformasjonOidcRestTemplate() = restTemplate(pensjonUrl, UsernameToOidcInterceptor(stsService))
//
//    @Bean
//    fun safGraphQlOidcRestTemplate() = restTemplate(graphQlUrl, TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
//
//    @Bean
//    fun safRestOidcRestTemplate() = restTemplate(hentRestUrl, TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
//
//
//    private fun restTemplate(url: String, tokenIntercetor: ClientHttpRequestInterceptor?) : RestTemplate {
//        return RestTemplateBuilder()
//            .rootUri(url)
//            .errorHandler(DefaultResponseErrorHandler())
//            .setReadTimeout(Duration.ofSeconds(120))
//            .setConnectTimeout(Duration.ofSeconds(120))
//            .additionalInterceptors(
//                RequestIdHeaderInterceptor(),
//                RequestResponseLoggerInterceptor(),
//                tokenIntercetor
//            )
//            .build().apply {
//                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()
//                    .apply { setOutputStreaming(false) }
//                )
//            }
//    }
//
//    private fun restTemplate(url: String) : RestTemplate {
//        return RestTemplateBuilder()
//            .rootUri(url)
//            .errorHandler(DefaultResponseErrorHandler())
//            .additionalInterceptors(
//                RequestIdHeaderInterceptor(),
//                RequestResponseLoggerInterceptor()
//            )
//            .build().apply {
//                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()
//                    .apply { setOutputStreaming(false) }
//                )
//            }
//    }
//
//
//    private fun clientProperties(oAuthKey: String): ClientProperties = clientConfigurationProperties.registration[oAuthKey]
//        ?: throw RuntimeException("could not find oauth2 client config for $oAuthKey")
//
//    private fun bearerTokenInterceptor(clientProperties: ClientProperties, oAuth2AccessTokenService: OAuth2AccessTokenService): ClientHttpRequestInterceptor {
//        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
//            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
//            request.headers.setBearerAuth(response.accessToken)
////            val tokenChunks = response.accessToken.split(".")
////            val tokenBody =  tokenChunks[1]
////            logger.debug("subject: " + JWTClaimsSet.parse(Base64.getDecoder().decode(tokenBody).decodeToString()).subject)
////            logger.debug("response: " + response.toJson())
//            execution.execute(request, body!!)
//        }
//    }
//
//
//}
