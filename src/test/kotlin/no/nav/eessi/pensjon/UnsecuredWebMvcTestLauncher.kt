package no.nav.eessi.pensjon

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Profile

@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc", "no.nav.eessi"])
@SpringBootApplication
@Profile("unsecured-webmvctest")
class UnsecuredWebMvcTestLauncher : SpringBootServletInitializer()

fun main(args: Array<String>) {
    runApplication<UnsecuredWebMvcTestLauncher>(*args)
}
