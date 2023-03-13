package no.nav.eessi.pensjon

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.retry.annotation.EnableRetry
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2


@EnableJwtTokenValidation(ignore = ["org.springframework", "springfox.documentation", "no.nav.eessi.pensjon.fagmodul.health.DiagnosticsController"])
@EnableOAuth2Client(cacheEnabled = true)
@SpringBootApplication
@EnableCaching
@EnableRetry
@EnableSwagger2
@Profile("!unsecured-webmvctest")
class EessiFagmodulApplication

/**
 * under development (Intellij) må hva med under Vm option:
 * -Dspring.profiles.active=local  local run T environment
 *
 */
fun main(args: Array<String>) {
    runApplication<EessiFagmodulApplication>(*args)

    @Bean
    fun swagger2(): Docket? {
        return Docket(DocumentationType.SWAGGER_2).select()
            .apis(RequestHandlerSelectors.basePackage("no.nav.eessi.pensjon.fagmodul")).build()
    }
}
