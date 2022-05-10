package no.nav.eessi.pensjon

import com.ninjasquad.springmockk.MockkBean
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Profile
import org.springframework.test.annotation.DirtiesContext

@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc", "no.nav.eessi"])
@SpringBootApplication
@Profile("unsecured-webmvctest")
@DirtiesContext
class UnsecuredWebMvcTestLauncher : SpringBootServletInitializer() {

    @MockkBean
    private lateinit var clientConfigurationProperties: ClientConfigurationProperties

    @MockkBean
    private lateinit var oAuth2AccessTokenService: OAuth2AccessTokenService

    fun main(args: Array<String>) {
        runApplication<UnsecuredWebMvcTestLauncher>(*args)
    }

}
