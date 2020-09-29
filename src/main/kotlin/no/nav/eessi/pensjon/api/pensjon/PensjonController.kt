package no.nav.eessi.pensjon.api.pensjon

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.IkkeFunnetException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.annotation.PostConstruct

@Protected
@RestController
@RequestMapping("/pensjon")
class PensjonController(private val pensjonsinformasjonClient: PensjonsinformasjonClient,
                        private val auditlogger: AuditLogger,
                        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(PensjonController::class.java)

    private lateinit var PensjonControllerHentSakType: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        PensjonControllerHentSakType = metricsHelper.init("PensjonControllerHentSakType")
    }

    @ApiOperation("Henter ut saktype knyttet til den valgte sakId og aktoerId")
    @GetMapping("/saktype/{sakId}/{aktoerId}")
    fun hentPensjonSakType(@PathVariable("sakId", required = true) sakId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String>? {
        auditlogger.log("/saktype/{$sakId}/{$aktoerId}", "hentPensjonSakType")

        return PensjonControllerHentSakType.measure {
            logger.debug("Henter sakstype på $sakId / $aktoerId")

            try {
                val hentKunSakType = pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId)
                ResponseEntity.ok().body(mapAnyToJson(hentKunSakType))
            } catch (ife: IkkeFunnetException) {
                logger.warn("Feil ved henting av sakstype, ingen sak funnet. Sak: ${sakId}")
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ife.message!!))
            } catch (e: Exception) {
                logger.warn("Feil ved henting av sakstype på saksid: ${sakId}")
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.message!!))
            }
        }
    }

    @ApiOperation("Henter ut en liste over alle saker på valgt aktoerId")
    @GetMapping("/sakliste/{aktoerId}")
    fun getPensjonSakliste(@PathVariable("aktoerId", required = true) aktoerId: String) : List<Long> {
        val pensjonInformasjon = pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId)
        return pensjonInformasjon.brukersSakerListe.brukersSakerListe.map { sak -> sak.sakId }
    }
}
