package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiOperation
import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.*


/**
 * tjeneste for opprettelse av automatiske krav ved mottakk av Buc/Krav fra utland.
 * Se PK-55797 , EESSIPEN-68
 */
@CrossOrigin
@RestController
@RequestMapping("/pesys")
@Protected
class PensjonsinformasjonUtlandController(private val pensjonsinformasjonUtlandService: PensjonsinformasjonUtlandService) {

    @ApiOperation(httpMethod = "PUT", value = "legger mock KravUtland til på map med bucid som key, KravUtland som verdi", response = KravUtland::class)
    @PutMapping("/putKravUtland/{bucId}")
    fun mockPutKravUtland(@PathVariable("bucId", required = true) bucId: Int, @RequestBody kravUtland: KravUtland): KravUtland {
        if (bucId in 1..999) {
            pensjonsinformasjonUtlandService.putKravUtlandMap(bucId, kravUtland)
            return hentKravUtland(bucId)
        }
        return KravUtland(errorMelding = "feil ved opprettelse av mock KravUtland, bucId må være mellom 1 og 999")
    }

    @ApiOperation(httpMethod = "DELETE", value = "sletter mock KravUtland fra map med buckid som key.", response = KravUtland::class)
    @DeleteMapping("/deleteKravUtland/{bucId}")
    fun mockDeleteKravUtland(@PathVariable("bucId", required = true) buckId: Int) {
        pensjonsinformasjonUtlandService.mockDeleteKravUtland(buckId)
    }

    @ApiOperation(httpMethod = "GET", value = "henter liste av keys fra mockMap med KravUtland", response = Set::class)
    @GetMapping("/hentKravUtlandKeys")
    fun mockGetKravUtlandKeys(): Set<Int> {
        return pensjonsinformasjonUtlandService.mockGetKravUtlandKeys()
    }

    @ApiOperation(httpMethod = "GET", value = "Henter ut kravhode fra innkommende SEDER fra EU/EØS. Nødvendig data for å automatisk opprette et krav i Pesys", response = KravUtland::class)
    @GetMapping("/hentKravUtland/{bucId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun hentKravUtland(@PathVariable("bucId", required = true) bucId: Int): KravUtland {
        return pensjonsinformasjonUtlandService.hentKravUtland(bucId)
    }
}
