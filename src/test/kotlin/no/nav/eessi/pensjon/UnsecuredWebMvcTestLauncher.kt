package no.nav.eessi.pensjon

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Profile
import org.springframework.test.annotation.DirtiesContext

@EnableJwtTokenValidation(ignore = ["org.springframework", "springfox.documentation", "no.nav.eessi"])
@SpringBootApplication
@Profile("unsecured-webmvctest")
@DirtiesContext
class UnsecuredWebMvcTestLauncher : SpringBootServletInitializer()

fun main(args: Array<String>) {
    runApplication<UnsecuredWebMvcTestLauncher>(*args)
}
