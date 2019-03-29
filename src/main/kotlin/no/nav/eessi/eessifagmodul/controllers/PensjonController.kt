package no.nav.eessi.eessifagmodul.controllers

import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.models.AktoerregisterException
import no.nav.eessi.eessifagmodul.models.IkkeFunnetException
import no.nav.eessi.eessifagmodul.models.IkkeGyldigKallException
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.Pensjontype
import no.nav.eessi.eessifagmodul.utils.errorBody
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.security.oidc.api.Protected
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Protected
@RestController
@RequestMapping("/pensjon")
class PensjonController(private val pensjonsinformasjonService: PensjonsinformasjonService,
                        private val aktoerregisterService: AktoerregisterService) {

    @ApiOperation("Henter ut saktype knyttet til den valgte sakId og aktoerId")
    @GetMapping("/saktype/{sakId}/{aktoerId}")
    fun hentPensjonSakType(@PathVariable("sakId", required = true) sakId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String>? {
        // FIXME This is a hack because Pesys uses the wrong identifier in some cases
        val fnr = if (isProbablyAnFnrSentAsAktoerId(aktoerId)) aktoerId else hentAktoerIdPin(aktoerId)
        val hentKunSakType = Pensjontype(sakId = "", sakType = "")
        try {
            val hentKunSakType = pensjonsinformasjonService.hentKunSakType(sakId, fnr)
        } catch (ife: IkkeFunnetException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ife.message!!))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.message!!))
        }
        return ResponseEntity.ok().body(mapAnyToJson(hentKunSakType))
    }

    @Throws(AktoerregisterException::class)
    fun hentAktoerIdPin(aktorid: String): String {
        if (aktorid.isBlank()) throw IkkeGyldigKallException("Mangler AktorId")
        return aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktorid)
    }

    private fun isProbablyAnFnrSentAsAktoerId(aktorid: String) = aktorid.length == 11

}



