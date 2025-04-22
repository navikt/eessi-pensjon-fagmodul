package no.nav.eessi.pensjon.api.geo

//import no.nav.security.token.support.core.api.Unprotected
//import org.slf4j.LoggerFactory
//import org.springframework.web.bind.annotation.GetMapping
//import org.springframework.web.bind.annotation.PathVariable
//import org.springframework.web.bind.annotation.RequestMapping
//import org.springframework.web.bind.annotation.RestController
//
//@Unprotected
//@RestController
//@RequestMapping("/postnummer")
//class PostkodeController() {
//
//    private val logger = LoggerFactory.getLogger(PostkodeController::class.java)
//
//    @GetMapping("/{postnr}/sted")
//    fun getPostSted(@PathVariable("postnr", required = true) postnr: String): String? {
//
//        logger.info("Henter poststed fra postnr: $postnr")
//        return postnummerService.finnPoststed(postnr)
//    }
//
//}