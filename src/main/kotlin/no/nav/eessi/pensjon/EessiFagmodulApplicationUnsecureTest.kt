package no.nav.eessi.pensjon

import no.nav.security.spring.oidc.api.EnableOIDCTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Profile

/**
 * ONLY USE THIS FOR UNIT TESTING !!!
 */
@EnableOIDCTokenValidation(ignore = ["org.springframework", "springfox.documentation", "no.nav.eessi"])
@SpringBootApplication
@EnableCaching
@Profile("unsecured-webmvctest")
class EessiFagmodulApplicationUnsecureTest: SpringBootServletInitializer()

fun main(args: Array<String>) {
    runApplication<EessiFagmodulApplicationUnsecureTest>(*args)
}
