package no.nav.eessi.pensjon.services.kodeverk

//@Component
//class KodeverkRestTemplate {
//
//    @Value("\${kodeverk.rest-api.url}")
//    private lateinit var kodeverkUrl: String
//
//    @Bean
//    fun kodeRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//                .rootUri(kodeverkUrl)
//                .errorHandler(DefaultResponseErrorHandler())
//                .additionalInterceptors(
//                        RequestIdHeaderInterceptor(),
//                        RequestResponseLoggerInterceptor()
//                )
//                .build().apply {
//                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//                }
//    }
//
//}

