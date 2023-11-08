package no.nav.eessi.pensjon.api.gjenny

import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/gjenny")
class GjennyController (
    private val euxInnhentingService: EuxInnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(GjennyController::class.java)
    private lateinit var bucerForGjenny: MetricsHelper.Metric
    private lateinit var bucerForAvdodGjenny: MetricsHelper.Metric

    init {
        bucerForGjenny = metricsHelper.init("bucerForGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerForAvdodGjenny = metricsHelper.init("bucerForAvdodGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @GetMapping("/rinasaker/{aktoerId}/avdod/{avdodfnr}")
    fun getAvdodRinaSak(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("avdodfnr", required = true) avdodfnr : String
    ): List<EuxInnhentingService.BucView> {
        return bucerForAvdodGjenny.measure {
            logger.info("Henter gjenny rinasaker på avdod: $aktoerId")
            return@measure euxInnhentingService.hentBucViewAvdodGjenny(avdodfnr, aktoerId)
        }
    }


}