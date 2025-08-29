package no.nav.eessi.pensjon.api.geo

import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@Unprotected
@RestController
@RequestMapping("/landkoder")
class LandkodeController(private val kodeverkClient: KodeverkClient, private val kodeverkService: KodeverkService) {

    private val logger = LoggerFactory.getLogger(LandkodeController::class.java)

//    @GetMapping("/")
//    fun getLandKoder(): ResponseEntity<FrontEndResponse<String>> {
//        logger.info("Henter landkoder, default")
//        return try {
//            val hentLandkoder = kodeverkClient.hentAlleLandkoder()
//            ResponseEntity.ok(FrontEndResponse(result = hentLandkoder, status = HttpStatus.OK.value().toString()))
//        } catch (ex: Exception) {
//            logger.error("Feil ved henting av landkoder: ${ex.message}", ex)
//            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(FrontEndResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.name, message = ex.message))
//        }
//    }
//
//    @GetMapping("/landkoder2")
//    fun getLandKode2(): ResponseEntity<FrontEndResponse<List<String>>> {
//        logger.info("Henter landkoder i ISO Alpha2 standard")
//        return try {
//            val listeLandkoder2 = kodeverkClient.hentLandkoderAlpha2()
//            ResponseEntity.ok(FrontEndResponse(result = listeLandkoder2, status = HttpStatus.OK.value().toString()))
//        } catch (ex: Exception) {
//            logger.error("Feil ved henting av landkoder2: ${ex.message}", ex)
//            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(FrontEndResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.name, message = ex.message))
//        }
//    }
//
//    @GetMapping("/{land2}/land3")
//    fun getLandKoderAlpha3(@PathVariable("land2", required = true) land2: String): ResponseEntity<FrontEndResponse<String>>? {
//        logger.info("Henter landkoder i ISO ALpha 3 standard")
//        return try {
//            val landkoder3 = kodeverkClient.finnLandkode(land2)
//            ResponseEntity.ok(FrontEndResponse(result = landkoder3, status = HttpStatus.OK.value().toString()))
//        } catch (ex: Exception) {
//            logger.error("Feil ved henting av landkoder3: ${ex.message}", ex)
//            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(FrontEndResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.name, message = ex.message))
//        }
//    }
//
//    @GetMapping("/{land3}/land2")
//    fun getLandKoderAlpha2(@PathVariable("land3", required = true) land3: String): ResponseEntity<FrontEndResponse<String>>? {
//        logger.info("Henter Alpha2 landkode for Alpha3")
//        return try {
//            val landkode2 = kodeverkClient.finnLandkode(land3)
//            ResponseEntity.ok(FrontEndResponse(result = landkode2, status = HttpStatus.OK.value().toString()))
//        } catch (ex: Exception) {
//            logger.error("Feil ved henting av landkoder2: ${ex.message}", ex)
//            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(FrontEndResponse(status = HttpStatus.INTERNAL_SERVER_ERROR.name, message = ex.message))
//        }
//    }

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

