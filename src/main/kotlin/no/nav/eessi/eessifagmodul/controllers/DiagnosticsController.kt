package no.nav.eessi.eessifagmodul.controllers

import no.nav.security.oidc.api.Unprotected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@CrossOrigin
@RestController
@Unprotected
class DiagnosticsController {

    private val logger: Logger by lazy { LoggerFactory.getLogger(DiagnosticsController::class.java) }

    @Value("\${app.name}")
    lateinit var appName: String

    @Value("\${app.version}")
    private lateinit var appVersion: String


    @GetMapping("/ping")
    fun ping(): ResponseEntity<Unit> {
        logger.debug("Ping kalt, så alt er ok")
        return ResponseEntity.ok().build()
    }

    @GetMapping("/internal/selftest")
    fun selftest(): SelftestResult {
        logger.debug("Selftest passed")
        return SelftestResult(name = appName, version = appVersion, aggregateResult = 0, checks = null)
    }

    @GetMapping("/internal/isalive")
    fun isalive(): ResponseEntity<String> {
        return ResponseEntity.ok("Is alive")
    }

    @GetMapping("/internal/isready")
    fun isready(): ResponseEntity<String> {
        return ResponseEntity.ok("Is ready")
    }

}

data class SelftestResult(
        val name: String,
        val version: String,
        val timestamp: Instant = Instant.now(),
        val aggregateResult: Int,
        val checks: List<Check>?
)

data class Check(
        val endpoint: String,
        val description: String,
        val errorMessage: String,
        val result: Int
)

