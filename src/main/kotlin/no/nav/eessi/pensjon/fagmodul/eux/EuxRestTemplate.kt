package no.nav.eessi.pensjon.fagmodul.eux

//@Component
//class EuxRestTemplate(
//    private val tokenValidationContextHolder: TokenValidationContextHolder,
//    private val stsService: STSService
//) {
//
//    @Value("\${eessipen-eux-rina.url}")
//    lateinit var url: String
//
//    @Bean
//    fun euxOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//                .rootUri(url)
//                .errorHandler(DefaultResponseErrorHandler())
//                .setReadTimeout(Duration.ofSeconds(120))
//                .setConnectTimeout(Duration.ofSeconds(120))
//                .additionalInterceptors(
//                    RequestIdHeaderInterceptor(),
//                    RequestResponseLoggerInterceptor(),
//                    TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
//                .build().apply {
//                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//                }
//    }
//
//    @Bean
//    fun euxUsernameOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//            .rootUri(url)
//            .errorHandler(DefaultResponseErrorHandler())
//            .setReadTimeout(Duration.ofSeconds(120))
//            .setConnectTimeout(Duration.ofSeconds(120))
//            .additionalInterceptors(
//                RequestIdHeaderInterceptor(),
//                RequestResponseLoggerInterceptor(),
//                UsernameToOidcInterceptor(stsService))
//            .build().apply {
//                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//            }
//    }
//
//}
