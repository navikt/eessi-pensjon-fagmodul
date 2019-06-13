package no.nav.eessi.eessifagmodul.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.services.saf.SafService
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@Protected
@RestController
@RequestMapping("/saf")
class SafController(val safService: SafService) {

    private val logger = LoggerFactory.getLogger(SafController::class.java)

    @ApiOperation("Henter metadata for alle dokumenter i alle journalposter for en gitt aktørid")
    @GetMapping("/metadata", consumes = ["application/json"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun getMetadata(@RequestBody aktoerId: String): String {
        logger.info("Henter metadata for dokumenter i SAF for aktørid: $aktoerId")
        return safService.hentDokumentMetadata(aktoerId)
    }
}