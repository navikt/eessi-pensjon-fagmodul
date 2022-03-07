package no.nav.eessi.pensjon.services.pensjonsinformasjon

/**
 * Rest template for PESYS pensjonsinformasjon
 */
//@Component
//class PensjonsinformasjonRestTemplate(private val stsService: STSService) {
//
//    @Value("\${pensjonsinformasjon.url}")
//    lateinit var url: String
//
//    @Bean
//    fun pensjonsinformasjonOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//                .rootUri(url)
//                .additionalInterceptors(
//                        RequestIdHeaderInterceptor(),
//                        RequestResponseLoggerInterceptor(),
//                        UsernameToOidcInterceptor(stsService))
//                .build().apply {
//                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//                }
//    }
//}
