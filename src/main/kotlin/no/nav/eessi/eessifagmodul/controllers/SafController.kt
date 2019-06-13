package no.nav.eessi.eessifagmodul.controllers

import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.services.saf.SafService
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@Protected
@RestController
@RequestMapping("/saf")
class SafController(val safService: SafService) {

    private val logger = LoggerFactory.getLogger(SafController::class.java)

    @ApiOperation("Henter metadata for alle dokumenter i alle journalposter for en gitt aktørid")
    @GetMapping("/metadata/{aktoerId}")
    fun getMetadata(@PathVariable("aktoerId", required = true) aktoerId: String): String {
        logger.info("Henter metadata for dokumenter i SAF for aktørid: $aktoerId")
        return safService.hentDokumentMetadata(aktoerId)
    }
}