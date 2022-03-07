package no.nav.eessi.pensjon.fagmodul.prefill.klient

//@Component
//@Profile("prod", "test")
//class OauthPrefillRestTemplate(
//    private val clientConfigurationProperties: ClientConfigurationProperties,
//    private val oAuth2AccessTokenService: OAuth2AccessTokenService?) {
//
//    private val logger: Logger by lazy { LoggerFactory.getLogger(OauthPrefillRestTemplate::class.java) }
//
//    @Value("\${EESSIPENSJON_PREFILL_GCP_URL}")
//    lateinit var url: String
//
//    @Bean
//    fun oAuthTemplate() : RestTemplate {
//        return RestTemplateBuilder()
//            .rootUri(url)
//            .errorHandler(DefaultResponseErrorHandler())
//            .setReadTimeout(Duration.ofSeconds(120))
//            .setConnectTimeout(Duration.ofSeconds(120))
//            .additionalInterceptors(
//                RequestIdHeaderInterceptor(),
//                RequestResponseLoggerInterceptor(),
//                bearerTokenInterceptor(clientProperties("prefill-credentials"), oAuth2AccessTokenService!!)
//            )
//            .build().apply {
//                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory().apply {
//                    setOutputStreaming(false)
//                })
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
//            val tokenChunks = response.accessToken.split(".")
//            val tokenBody =  tokenChunks[1]
//            logger.info("subject: " + JWTClaimsSet.parse(Base64.getDecoder().decode(tokenBody).decodeToString()).subject)
//            logger.debug("response: " + response.toJson())
//
//            execution.execute(request, body!!)
//        }
//    }
//
//}