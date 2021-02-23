package no.nav.eessi.pensjon.fagmodul.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.data.rest.configuration.SpringDataRestConfiguration
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc

@Configuration
@EnableSwagger2WebMvc
@Import(value = [SpringDataRestConfiguration::class])
    class SwaggerConfig {

    @Bean
    fun api(): Docket {
        return Docket(DocumentationType.SWAGGER_2)
                .apiInfo(metaData())
                .groupName("EESSI-Pensjon - Spring Boot REST API")
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.springframework.boot"))
                .paths(PathSelectors.any())
                .build()
    }

    private fun metaData(): ApiInfo {
        return ApiInfoBuilder()
                .title("EESSI-Pensjon - Spring Boot REST API")
                .description("Spring Boot REST API for EESSI-Pensjon.\n" +
                        "Vi finnes på slack https://nav-it.slack.com/messages/CAB4L39T6 eller https://nav-it.slack.com/messages/CADNRDN5T")
                .build()
    }
}