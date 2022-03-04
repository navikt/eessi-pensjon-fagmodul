package no.nav.eessi.pensjon

import com.ninjasquad.springmockk.MockkBean
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Profile
import org.springframework.web.client.RestTemplate

@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc", "no.nav.eessi"])
@SpringBootApplication
@Profile("unsecured-webmvctest")
class UnsecuredWebMvcTestLauncher : SpringBootServletInitializer() {

    @MockkBean(name = "oathTemplate")
    private lateinit var oathTemplate: RestTemplate

    fun main(args: Array<String>) {
        runApplication<UnsecuredWebMvcTestLauncher>(*args)
    }

}
