package no.nav.eessi.pensjon.api.pensjon

import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.services.pensjonsinformasjon.IkkeFunnetException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.helper.AktoerIdHelper
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import no.nav.eessi.pensjon.metrics.counter

@Protected
@RestController
@RequestMapping("/pensjon")
class PensjonController(private val pensjonsinformasjonService: PensjonsinformasjonService, private val aktoerIdHelper: AktoerIdHelper) {
    private val logger = LoggerFactory.getLogger(PensjonController::class.java)
    private final val hentSakTypeNavn = "eessipensjon_fagmodul.hentSakType"
    private val hentSakTypeVellykkede = counter(hentSakTypeNavn, "vellykkede")
    private val hentSakTypeFeilede = counter(hentSakTypeNavn, "feilede")

    @ApiOperation("Henter ut saktype knyttet til den valgte sakId og aktoerId")
    @GetMapping("/saktype/{sakId}/{aktoerId}")
    fun hentPensjonSakType(@PathVariable("sakId", required = true) sakId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String>? {
        logger.debug("Henter sakstype på ${sakId}")
        // FIXME This is a hack because Pesys uses the wrong identifier in some cases
        val fnr = if (isProbablyAnFnrSentAsAktoerId(aktoerId)) aktoerId else aktoerIdHelper.hentPinForAktoer(aktoerId)

        return try {
            val hentKunSakType = pensjonsinformasjonService.hentKunSakType(sakId, fnr)
            hentSakTypeVellykkede.increment()
            ResponseEntity.ok().body(mapAnyToJson(hentKunSakType))
        } catch (ife: IkkeFunnetException) {
            hentSakTypeFeilede.increment()
            logger.warn("Feil ved henting av sakstype, ingen sak funnet. Sak: ${sakId}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ife.message!!))
        } catch (e: Exception) {
            hentSakTypeFeilede.increment()
            logger.warn("Feil ved henting av sakstype på saksid: ${sakId}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.message!!))
        }
    }

    private fun isProbablyAnFnrSentAsAktoerId(aktorid: String) = aktorid.length == 11
}
