package no.nav.eessi.pensjon.api.gjenny

import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

//@Unprotected
@RestController
@RequestMapping("/gjenny")
class GjennyController (
    private val innhentingService: InnhentingService,
    private val euxInnhentingService: EuxInnhentingService
    ) {

    private val logger = LoggerFactory.getLogger(GjennyController::class.java)
//    private lateinit var bucerForGjennyBrukere: MetricsHelper.Metric

    @GetMapping("/index")
    fun get(): String {
        return "index"
    }

    /**
     * Returnerer liste med buc'er fra Rina for gjenlevende og avdød
     */
    @GetMapping("/bucer/fnrlev/{fnrlev}/fnravdod/{fnrdod}")
    fun hentBucerMedJournalforteSeder(
        @PathVariable(value = "fnrlev", required = true) fnrlev: String,
        @PathVariable(value = "fnrdod", required = true) fnrdod: String,
    ): List<Rinasak> {
        logger.info("Henter bucer fra Rina for fnr")

        val gjenlevendeSaker = euxInnhentingService.hentBucerGjenny(fnrlev)
        val avdodSaker = euxInnhentingService.hentBucerGjenny(fnrdod)
        return gjenlevendeSaker + avdodSaker
    }

}