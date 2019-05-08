package no.nav.eessi.eessifagmodul

import no.nav.security.spring.oidc.api.EnableOIDCTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@EnableOIDCTokenValidation(ignore = ["org.springframework", "springfox.documentation"])
@SpringBootApplication
class EessiFagmodulApplication

/**
 * under development (Intellij) må hva med under Vm option:
 * -Dspring.profiles.active=local  local run T environment
 *
 */
fun main(args: Array<String>) {
    runApplication<EessiFagmodulApplication>(*args)
}
