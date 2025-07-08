package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.security.token.support.core.api.Protected
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*


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
    private val gcpStorageService: GcpStorageService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private var pensjonUtland: MetricsHelper.Metric = metricsHelper.init("pensjonUtland")
    private var trygdeTidMetric: MetricsHelper.Metric = metricsHelper.init("trygdeTidMetric")

    @GetMapping("/hentKravUtland/{bucId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun hentKravUtland(@PathVariable("bucId", required = true) bucId: Int): KravUtland {
        return pensjonUtland.measure {
            pensjonsinformasjonUtlandService.hentKravUtland(bucId)!!
        }
    }

    @GetMapping("/hentTrygdetid")
    fun hentTrygdetid(
        @RequestParam("aktorId", required = true) aktoerId: String,
        @RequestParam("rinaNr", required = true) rinaNr: String
    ): TygdetidForPesys? {
        return trygdeTidMetric.measure {
            val trygdeTid = gcpStorageService.hentTrygdetid(aktoerId, rinaNr)
            return@measure when {
                trygdeTid?.size == 1 -> TygdetidForPesys(aktoerId, rinaNr, trygdeTid.first(), null)
                (trygdeTid?.size ?: 0) > 1 -> TygdetidForPesys(
                    aktoerId, rinaNr, null,
                    "Det er registrert ${trygdeTid?.size} trygdetid for rinaNr: $rinaNr, aktoerId: $aktoerId, gir derfor ingen trygdetid tilbake."
                )
                else -> TygdetidForPesys(
                    aktoerId, rinaNr, null,
                    "Det finnes ingen registrert trygdetid for rinaNr: $rinaNr, aktoerId: $aktoerId"
                )
            }
        }
    }

    data class TygdetidForPesys(
        val aktoerId: String?,
        val rinaNr: String,
        val trygdetid: String? = null,
        val error: String? = null
    )
}
