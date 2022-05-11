package no.nav.eessi.pensjon.api

import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Unprotected
class ErrorLogonController {

    private val logger = LoggerFactory.getLogger(ErrorLogonController::class.java)

    @GetMapping("/errorlogon")
    fun errorlogon(): String {
        logger.error("Feiler ved logon")
        return "Det feiler ved logon"
    }

}