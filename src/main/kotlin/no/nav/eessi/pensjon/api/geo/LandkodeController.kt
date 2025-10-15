package no.nav.eessi.pensjon.api.geo

import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@Unprotected
@RestController
@RequestMapping("/landkoder")
class LandkodeController(private val kodeverkService: KodeverkService) {

    private val logger = LoggerFactory.getLogger(LandkodeController::class.java)

    @GetMapping("/rina")
    fun landkoderAkseptertAvRina(@RequestParam(required = false) format: String?): ResponseEntity<FrontEndResponse<LandkodeMerKorrektFormat>>? {
        logger.info("Henter landkode for rina, format: $format")
        return try {
            val aksepterteLandkoderFraRina = kodeverkService.getLandkoderAkseptertAvRina(format)
            ResponseEntity.ok(
                FrontEndResponse(
                    result = aksepterteLandkoderFraRina,
                    status = HttpStatus.OK.value().toString()
                )
            )
        } catch (ex: Exception) {
            logger.error("Feil ved henting av aksepterte landkoder fra Rina: ${ex.message}", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FrontEndResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.name, message = ex.message))
        }
    }
}

