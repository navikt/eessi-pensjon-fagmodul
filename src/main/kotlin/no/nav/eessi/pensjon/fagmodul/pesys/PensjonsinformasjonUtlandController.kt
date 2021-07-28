package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.v3.oas.annotations.Operation
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.security.token.support.core.api.Protected
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.annotation.PostConstruct


/**
 * tjeneste for opprettelse av automatiske krav ved mottakk av Buc/Krav fra utland.
 * Se PK-55797 , EESSIPEN-68
 */
@CrossOrigin
@RestController
@RequestMapping("/pesys")
@Protected
class PensjonsinformasjonUtlandController(
    private val pensjonsinformasjonUtlandService: PensjonsinformasjonUtlandService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private lateinit var pensjonUtland: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        pensjonUtland = metricsHelper.init("pensjonUtland")
    }

    @Operation(method = "GET", description = "Henter ut kravhode fra innkommende SED fra EU/EØS. Nødvendig data for å automatisk opprette et krav i Pesys")
    @GetMapping("/hentKravUtland/{bucId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun hentKravUtland(@PathVariable("bucId", required = true) bucId: Int): KravUtland {
        return pensjonUtland.measure {
            pensjonsinformasjonUtlandService.hentKravUtland(bucId)
        }
    }
}
