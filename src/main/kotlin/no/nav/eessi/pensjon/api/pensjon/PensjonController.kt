package no.nav.eessi.pensjon.api.pensjon

import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiPensjonSak
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PesysService
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter
import java.util.*

@Protected
@RestController
@RequestMapping("/pensjon")
class PensjonController(
    private val pesysService: PesysService,
    private val auditlogger: AuditLogger,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(PensjonController::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    private  var pensjonControllerHentSakType: MetricsHelper.Metric
    private  var pensjonControllerHentSakListe: MetricsHelper.Metric
    private  var pensjonControllerValidateSak: MetricsHelper.Metric
    private  var pensjonControllerKravDato: MetricsHelper.Metric
    init {
        pensjonControllerHentSakType = metricsHelper.init("PensjonControllerHentSakType")
        pensjonControllerHentSakListe = metricsHelper.init("PensjonControllerHentSakListe")
        pensjonControllerValidateSak = metricsHelper.init("PensjonControllerValidateSak")
        pensjonControllerKravDato = metricsHelper.init("PensjonControllerKravDato")
    }

    /**
     * Brukes for å hente saktype fra frontend / EP
     */
    @GetMapping("/saktype/{sakId}/{aktoerId}")
    fun hentPensjonSakType(@PathVariable("sakId", required = true) sakId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<FrontEndResponse<Pensjontype>> {
        auditlogger.log("hentPensjonSakType", aktoerId)

        return pensjonControllerHentSakType.measure {
            try {
                logger.info("Henter sakstype på $sakId / $aktoerId")
                pesysService.hentSaktype(sakId)?.let {
                    ResponseEntity.ok(FrontEndResponse(Pensjontype(sakId, it.name), HttpStatus.OK.name))
                } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    FrontEndResponse(result = null, status = HttpStatus.NOT_FOUND.name, message = "Sakstype ikke funnet for sakId: $sakId")
                )
            } catch (e: Exception) {
                logger.warn("Feil ved henting av sakstype på saksid: $sakId")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    FrontEndResponse(result = null, status = HttpStatus.BAD_REQUEST.name, message = "Feil ved henting av sakstype på saksid: $sakId")
                )
            }
        }
    }

    data class Pensjontype(
        val sakId: String,
        val sakType: String
    )

    /**
     * Brukes for å hente kravdato fra frontend / EP
     */
    @GetMapping("/kravdato/saker/{saksId}/krav/{kravId}/aktor/{aktoerId}")
    fun hentKravDatoFraAktor(@PathVariable("saksId", required = true) sakId: String, @PathVariable("kravId", required = true) kravId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<FrontEndResponse<KravDato>> {
        return pensjonControllerKravDato.measure {
            val xid = MDC.get("x_request_id") ?: UUID.randomUUID().toString()
            if (sakId.isEmpty() || kravId.isEmpty() || aktoerId.isEmpty()) {
                logger.warn("Det mangler verdier: saksId $sakId, kravId: $kravId, aktørId: $aktoerId, x_request_id: $xid")
                return@measure ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    FrontEndResponse(result = null, status = HttpStatus.BAD_REQUEST.name, message = "Det mangler verdier: saksId $sakId, kravId: $kravId, aktørId: $aktoerId")
                )
            }
            return@measure try {
                pesysService.hentKravdato(kravId)?.let {
                    ResponseEntity.ok(FrontEndResponse(KravDato(it.toString()), HttpStatus.OK.name))
                } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    FrontEndResponse(result = null, status = HttpStatus.BAD_REQUEST.name, message = "Feiler å hente kravDato")
                )
            } catch (e: Exception) {
                logger.warn("Feil ved henting av kravdato på saksid: $sakId, x_request_id: $xid", e)
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    FrontEndResponse(result = null, status = HttpStatus.BAD_REQUEST.name, message = "Feil ved henting av kravdato på saksid: $sakId")
                )
            }
        }
    }

    data class KravDato(
        val kravDato: String
    )

    /**
     * Brukes for å hente vedtak fra frontend / EP
     */
    @GetMapping("/vedtak/{vedtakid}/uforetidspunkt")
    fun hentVedtakforForUfor(@PathVariable("vedtakid", required = true) vedtakId: String): FrontEndResponse<Uforetidspunkt> {
        logger.info("Henter vedtak ($vedtakId)")

        val pensjonsinformasjon = pesysService.hentUfoeretidspunktOnVedtak(vedtakId).also {
            logger.debug("pensjonInfo: ${it?.toJsonSkipEmpty()}")
        }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val uforetidspunkt = Uforetidspunkt(
            uforetidspunkt = pensjonsinformasjon?.uforetidspunkt?.let { formatter.format(it) },
            virkningstidspunkt = pensjonsinformasjon?.virkningstidspunkt?.let { formatter.format(it) }
        )

        logger.info("Oppslag på uføretidspunkt ga: $uforetidspunkt")
        return FrontEndResponse(uforetidspunkt, HttpStatus.OK.name)
    }

    data class Uforetidspunkt(
        val uforetidspunkt: String?,
        val virkningstidspunkt: String?
    )

    /**
     * Brukes for å henter sakliste for aktoer fra journalføring
     */
    @GetMapping("/saklisteFraPesys")
    fun hentsakListeFraPesys(@RequestHeader("fnr") fnr: String): List<EessiPensjonSak> = pensjonControllerHentSakListe.measure {
        secureLog.info("Henter sakliste for fnr: $fnr")

        try {
            val brukersSakerListe = pesysService.hentSakListe(fnr)
            if (brukersSakerListe.isEmpty()) {
                logger.warn("Ingen brukersSakerListe funnet i pensjoninformasjon for ${fnr.takeLast(4)}")
            }
            brukersSakerListe.also { logger.info("BrukersSakerListe funnet: $it") }
        } catch (ex: Exception) {
            logger.error("Feil under henting av pensjoninformasjon kunne hentes", ex)
            throw ex
        }
    }
}
