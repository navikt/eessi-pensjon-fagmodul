package no.nav.eessi.eessifagmodul.controllers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@CrossOrigin
@RestController
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

}
