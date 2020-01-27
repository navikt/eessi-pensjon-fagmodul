package no.nav.eessi.pensjon.api.geo

import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.services.geo.LandkodeService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkService
import no.nav.security.oidc.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@Unprotected
@RestController
@RequestMapping("/landkoder")
class LandkodeController(private val landkodeService: LandkodeService
                , private val kodeverkService: KodeverkService) {

    private val logger = LoggerFactory.getLogger(LandkodeController::class.java)

    @ApiOperation("Henter liste over land, land2 og land3 av ISO Alpha2 og ISO Alpha3 standard")
    @GetMapping("/")
    fun getLandKoder(): String {
        return kodeverkService.hentAlleLandkoder()
//        logger.info("Henter landkoder")
//        return landkodeService.hentAlleLandkoder()
    }

    @ApiOperation("Henter liste over landkoder av ISO Alpha2 standard")
    @GetMapping("/landkoder2")
    fun getLandKode2(): List<String> {
        logger.info("Henter landkoder")
        return landkodeService.hentLandkode2()
    }

    @ApiOperation("Henter ut land ISO Alpha3 standard hvor nøkkel er ISO Alpha2")
    @GetMapping("/{land2}/land3")
    fun getLandKoderAlpha3(@PathVariable("land2", required = true) land2: String): String? {
        logger.info("Henter landkoder")
        //return landkodeService.finnLandkode3(land2)
        return kodeverkService.finnLandkode3(land2)
    }

    @ApiOperation("Henter ut land ISO Alpha2 standard hvor nøkkel er ISO Alpha3")
    @GetMapping("/{land3}/land2")
    fun getLandKoderAlpha2(@PathVariable("land3", required = true) land3: String): String? {
        logger.info("Henter landkoder")
        //return landkodeService.finnLandkode2(land3)
        return kodeverkService.finnLandkode2(land3)
    }
}

