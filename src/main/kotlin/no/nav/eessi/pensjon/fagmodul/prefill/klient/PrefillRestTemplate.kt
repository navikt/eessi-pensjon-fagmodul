package no.nav.eessi.pensjon.fagmodul.prefill.klient

//@Component
//class PrefillRestTemplate(
//    private val tokenValidationContextHolder: TokenValidationContextHolder) {
//    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillRestTemplate::class.java) }
//
//    @Value("\${EESSIPENSJON_PREFILL_URL}")
//    lateinit var url: String
//
//
//    @Bean
//    fun prefillOidcRestTemplate(): RestTemplate {
//       return onPremTemplate()
//    }
//
//    private fun onPremTemplate(): RestTemplate {
//        return RestTemplateBuilder()
//            .rootUri(url)
//            .errorHandler(DefaultResponseErrorHandler())
//            .setReadTimeout(Duration.ofSeconds(120))
//            .setConnectTimeout(Duration.ofSeconds(120))
//            .additionalInterceptors(
//                RequestIdHeaderInterceptor(),
//                RequestResponseLoggerInterceptor(),
//                TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
//            .build().apply {
//                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//            }
//    }
//
//}
