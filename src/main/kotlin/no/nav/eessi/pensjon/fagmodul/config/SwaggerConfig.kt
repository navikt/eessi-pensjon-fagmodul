package no.nav.eessi.pensjon.fagmodul.config

//import org.springdoc.core.GroupedOpenApi
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration


//@Configuration
//class SwaggerConfig {
//
//    @Bean
//    fun publicApi(): GroupedOpenApi? {
//        return GroupedOpenApi
//            .builder()
//            .group("EESSI-Pensjon - Spring Boot REST API")
//            .pathsToMatch("/**").build()
//    }
//
//}

//@Configuration
//EnableS
//@Import(value = [SpringDataRestConfiguration::class])
//class SwaggerConfig {
//
//    @Bean
//    fun api(): Docket {
//        return Docket(DocumentationType.OAS_30)
//                .apiInfo(metaData())
//                .groupName("EESSI-Pensjon - Spring Boot REST API")
//                .select()
//                .apis((RequestHandlerSelectors.basePackage("org.springframework.boot")).negate())
//                .paths(PathSelectors.any())
//                .build()
//    }
//
//    private fun metaData(): ApiInfo {
//        return ApiInfoBuilder()
//                .title("EESSI-Pensjon - Spring Boot REST API")
//                .description("Spring Boot REST API for EESSI-Pensjon.\n" +
//                        "Vi finnes på slack https://nav-it.slack.com/messages/CAB4L39T6 eller https://nav-it.slack.com/messages/CADNRDN5T")
//                .build()
//    }
//}