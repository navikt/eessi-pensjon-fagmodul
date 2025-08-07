package no.nav.eessi.pensjon.api.geo

import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*


@Unprotected
@RestController
@RequestMapping("/landkoder")
class LandkodeController(private val kodeverkClient: KodeverkClient, private val kodeverkService: KodeverkService) {

    private val logger = LoggerFactory.getLogger(LandkodeController::class.java)

    @GetMapping("/")
    fun getLandKoder(): String {
        logger.info("Henter landkoder, default")
        return kodeverkClient.hentAlleLandkoder()
    }

    @GetMapping("/landkoder2")
    fun getLandKode2(): List<String> {
        logger.info("Henter landkoder i ISO Alpha2 standard")
        return kodeverkClient.hentLandkoderAlpha2()
    }

    @GetMapping("/{land2}/land3")
    fun getLandKoderAlpha3(@PathVariable("land2", required = true) land2: String): String? {
        logger.info("Henter landkoder i ISO ALpha 3 standard")
        return kodeverkClient.finnLandkode(land2)
    }

    @GetMapping("/{land3}/land2")
    fun getLandKoderAlpha2(@PathVariable("land3", required = true) land3: String): String? {
        logger.info("Henter Alpha2 landkode for Alpha3")
        return kodeverkClient.finnLandkode(land3)
    }

    @GetMapping("/rina")
    fun landkoderAkseptertAvRina(@RequestParam(required = false) format: String?): String? {
        logger.info("Henter landkode for rina, format: $format")
        return kodeverkService.getLandkoderAkseptertAvRina(format)
    }
}

