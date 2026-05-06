package no.nav.eessi.pensjon.api.geo

import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Unprotected
@RestController
@RequestMapping("/landogvalutakoder")
class LandOgValutakodeController(private val kodeverkService: KodeverkService) {

    private val logger = LoggerFactory.getLogger(LandOgValutakodeController::class.java)

    @GetMapping("/rina")
    fun landOgValutakoderAkseptertAvRina(@RequestParam(required = false) format: String?): ResponseEntity<FrontEndResponse<LandOgValutakodeMerKorrektFormat>>? {
        logger.info("Henter land- og valutakoder for rina, format: $format")
        return try {
            val aksepterteLandOgValutakoderFraRina = kodeverkService.getLandOgValutakoderAkseptertAvRina(format)
            ResponseEntity.ok(
                FrontEndResponse(
                    result = aksepterteLandOgValutakoderFraRina,
                    status = HttpStatus.OK.value().toString()
                )
            )
        } catch (ex: Exception) {
            logger.error("Feil ved henting av aksepterte land- og valutakoder fra Rina: ${ex.message}", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FrontEndResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.name, message = ex.message))
        }
    }
}
