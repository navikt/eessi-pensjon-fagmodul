package no.nav.eessi.pensjon.vedlegg.client

//@Component
//class SafRestTemplate(private val tokenValidationContextHolder: TokenValidationContextHolder,
//                      private val registry: MeterRegistry) {
//
//    @Value("\${saf.graphql.url}")
//    lateinit var graphQlUrl: String
//
//    @Value("\${saf.hentdokument.url}")
//    lateinit var restUrl: String
//
//    @Bean
//    fun safGraphQlOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//                .rootUri(graphQlUrl)
//                .errorHandler(DefaultResponseErrorHandler())
//                .additionalInterceptors(
//                        RequestIdHeaderInterceptor(),
//                        RequestResponseLoggerInterceptor(),
//                        TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
//                .build().apply {
//                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//                }
//    }
//
//
//    @Bean
//    fun safRestOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//                .rootUri(restUrl)
//                .errorHandler(DefaultResponseErrorHandler())
//                .additionalInterceptors(
//                        RequestIdHeaderInterceptor(),
//                        RequestResponseLoggerInterceptor(),
//                        TokenAuthorizationHeaderInterceptor(tokenValidationContextHolder))
//                .build().apply {
//                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//                }
//    }
//}
//
