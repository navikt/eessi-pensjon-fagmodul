package no.nav.eessi.pensjon.api.pensjon

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.IkkeFunnetException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonValiderKrav
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct

@Protected
@RestController
@RequestMapping("/pensjon")
class PensjonController(private val pensjonsinformasjonClient: PensjonsinformasjonClient,
                        private val auditlogger: AuditLogger,
                        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(PensjonController::class.java)

    private lateinit var PensjonControllerHentSakType: MetricsHelper.Metric
    private lateinit var PensjonControllerHentSakListe: MetricsHelper.Metric
    private lateinit var PensjonControllerValidateSak: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        PensjonControllerHentSakType = metricsHelper.init("PensjonControllerHentSakType")
        PensjonControllerHentSakListe = metricsHelper.init("PensjonControllerHentSakListe")
        PensjonControllerValidateSak = metricsHelper.init("PensjonControllerValidateSak")
    }

    @ApiOperation("Henter ut saktype knyttet til den valgte sakId og aktoerId")
    @GetMapping("/saktype/{sakId}/{aktoerId}")
    fun hentPensjonSakType(@PathVariable("sakId", required = true) sakId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String>? {
        auditlogger.log("/saktype/{$sakId}/{$aktoerId}", "hentPensjonSakType")

        return PensjonControllerHentSakType.measure {
            logger.info("Henter sakstype på $sakId / $aktoerId")

            try {
                val hentKunSakType = pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId)
                ResponseEntity.ok(mapAnyToJson(hentKunSakType))
            } catch (ife: IkkeFunnetException) {
                logger.warn("Feil ved henting av sakstype, ingen sak funnet. Sak: ${sakId}")
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ife.message))
            } catch (e: Exception) {
                logger.warn("Feil ved henting av sakstype på saksid: ${sakId}")
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.message!!))
            }
        }
    }

    @ApiOperation("Henter ut kravdato når det eksisterer et vedtak")
    @GetMapping("/kravdato/{vedtaksId}/vedtak")
    fun hentKravDatoFraVedtak(@PathVariable("vedtaksId", required = true) vedtaksId: String) : Any? {
        return PensjonControllerValidateSak.measure {
            try {
                val pensjonSak = pensjonsinformasjonClient.hentAltPaaVedtak(vedtaksId = vedtaksId)
                pensjonSak.vedtak?.virkningstidspunkt?.simpleFormat()

            } catch (e: Exception) {
                logger.warn("Feil ved henting av kravdato på vedtaksId: ${vedtaksId}")
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.message!!))
            }
        }
    }

    @ApiOperation("Henter ut kravdato der det ikke eksisterer et vedtak")
    @GetMapping("/kravdato/{sakid}/{aktoer}")
    fun hentKravDato(@PathVariable("aktoerId", required = true) aktoerId: String, @PathVariable("sakId", required = true) sakId: String) : Any? {
        return PensjonControllerValidateSak.measure {
            try {
                pensjonsinformasjonClient.hentKravDato(aktoerId, sakId)
            } catch (e: Exception) {
                logger.warn("Feil ved henting av kravdato på saksid: ${sakId}")
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.message!!))
            }
        }
    }


    @ApiOperation("Henter ut en liste over alle saker på valgt aktoerId")
    @GetMapping("/validate/{aktoerId}/sakId/{sakId}/buctype/{buctype}")
    fun validerKravPensjon(@PathVariable("aktoerId", required = true) aktoerId: String, @PathVariable("sakId", required = true) sakId: String, @PathVariable("buctype", required = true) bucType: String): Boolean {
        return PensjonControllerValidateSak.measure {

            val pendata = pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId)
            if (pendata.brukersSakerListe == null) {
                logger.warn("Ingen gyldig brukerSakerListe funnet")
                throw PensjoninformasjonException("Ingen gyldig brukerSakerListe, mangler data fra pesys")
            }

            val sak = PensjonsinformasjonClient.finnSak(sakId, pendata)
            return@measure when(bucType) {
                "P_BUC_01", "P_BUC_03" -> {
                    PensjoninformasjonValiderKrav.validerGyldigKravtypeOgArsak(sak, bucType)
                    true
                }
                "P_BUC_02" -> {
                    PensjoninformasjonValiderKrav.validerGyldigKravtypeOgArsakGjenlevnde(sak, bucType)
                    true
                }
                else -> true
            }
        }
    }


    @ApiOperation("Henter ut en liste over alle saker på valgt aktoerId")
    @GetMapping("/sakliste/{aktoerId}")
    fun hentPensjonSakIder(@PathVariable("aktoerId", required = true) aktoerId: String): List<PensjonSak> {
        return PensjonControllerHentSakListe.measure {
            logger.info("henter sakliste for aktoer: $aktoerId")
            return@measure try {
                val pensjonInformasjon = pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId)
                val brukersSakerListe = pensjonInformasjon.brukersSakerListe.brukersSakerListe
                if (brukersSakerListe == null) {
                    logger.error("Ingen brukersSakerListe funnet i pensjoninformasjon for aktoer: $aktoerId")
                    throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ingen brukersSakerListe funnet i pensjoninformasjon for aktoer: $aktoerId")
                }
                brukersSakerListe.map { sak ->
                    logger.debug("PensjonSak for journalføring: sakId: ${sak.sakId} sakType: ${sak.sakType} sakStatus: ${sak.status} ")
                    PensjonSak(sak.sakId.toString(), sak.sakType, PensjonSakStatus.from(sak.status))
                }
            } catch (ex: Exception) {
                logger.warn("Ingen pensjoninformasjon kunne hentes", ex)
                listOf()
            }
        }
    }
}

class PensjonSak (
        val sakId: String,
        val sakType: String,
        val sakStatus: PensjonSakStatus
)

enum class PensjonSakStatus(val status: String) {
    TIL_BEHANDLING("TIL_BEHANDLING"),
    AVSLUTTET("AVSL"),
    LOPENDE("INNV"),
    OPPHOR("OPPHOR"),
    UKJENT("");

    companion object {
        @JvmStatic
        fun from(s: String): PensjonSakStatus {
            return values().firstOrNull { it.status == s } ?: UKJENT
        }
    }

}