package no.nav.eessi.eessifagmodul.geo

import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.services.PostnummerService
import no.nav.security.oidc.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Unprotected
@RestController
@RequestMapping("/postnummer")
class PostkodeController(private val postnummerService: PostnummerService) {

    private val logger = LoggerFactory.getLogger(PostkodeController::class.java)

    @ApiOperation("Henter ut Poststed med postnr som nøkkel")
    @GetMapping("/{postnr}/sted")
    fun getPostSted(@PathVariable("postnr", required = true) postnr: String): String? {

        logger.info("Henter poststed fra postnr: $postnr")
        return postnummerService.finnPoststed(postnr)
    }


}