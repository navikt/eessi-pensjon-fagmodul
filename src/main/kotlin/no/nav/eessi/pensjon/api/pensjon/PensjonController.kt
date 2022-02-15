package no.nav.eessi.pensjon.api.pensjon

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.v3.oas.annotations.Operation
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonValiderKrav
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.toJson
import no.nav.pensjon.v1.vilkarsvurdering.V1Vilkarsvurdering
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import javax.annotation.PostConstruct
import javax.xml.datatype.XMLGregorianCalendar

@Protected
@RestController
@RequestMapping("/pensjon")
class PensjonController(
    private val pensjonsinformasjonClient: PensjonsinformasjonClient,
    private val auditlogger: AuditLogger,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(PensjonController::class.java)

    private lateinit var PensjonControllerHentSakType: MetricsHelper.Metric
    private lateinit var PensjonControllerHentSakListe: MetricsHelper.Metric
    private lateinit var PensjonControllerValidateSak: MetricsHelper.Metric
    private lateinit var PensjonControllerKravDato: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        PensjonControllerHentSakType = metricsHelper.init("PensjonControllerHentSakType")
        PensjonControllerHentSakListe = metricsHelper.init("PensjonControllerHentSakListe")
        PensjonControllerValidateSak = metricsHelper.init("PensjonControllerValidateSak")
        PensjonControllerKravDato = metricsHelper.init("PensjonControllerKravDato")
    }

    @Operation(description = "Henter ut saktype knyttet til den valgte sakId og aktoerId")
    @GetMapping("/saktype/{sakId}/{aktoerId}")
    fun hentPensjonSakType(@PathVariable("sakId", required = true) sakId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String>? {
        auditlogger.log("hentPensjonSakType", aktoerId)

        return PensjonControllerHentSakType.measure {
            logger.info("Henter sakstype på $sakId / $aktoerId")

            ResponseEntity.ok(mapAnyToJson(pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId)))
        }
    }

    @Operation(description = "Henter ut kravdato der det ikke eksisterer et vedtak")
    @GetMapping("/kravdato/saker/{saksId}/krav/{kravId}/aktor/{aktoerId}")
    fun hentKravDatoFraAktor(@PathVariable("saksId", required = true) sakId: String, @PathVariable("kravId", required = true) kravId: String, @PathVariable("aktoerId", required = true) aktoerId: String) : ResponseEntity<String>? {
        val xid =  MDC.get("x_request_id").toString()
        return PensjonControllerKravDato.measure {
            if (sakId.isEmpty() || kravId.isEmpty() || aktoerId.isEmpty()) {
                logger.warn("Det mangler verdier: saksId $sakId, kravId: $kravId, aktørId: $aktoerId")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    errorBody("Mangler gyldige verdier for å hente kravdato, prøv heller fra vedtakskonteks", xid))
            }
            try {
               pensjonsinformasjonClient.hentKravDatoFraAktor(aktorId = aktoerId, kravId = kravId, saksId = sakId)?.let {
                   return@measure ResponseEntity.ok("""{ "kravDato": "$it" }""")
               }
               return@measure ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("Feiler å hente kravDato", xid))
            } catch (e: Exception) {
                logger.warn("Feil ved henting av kravdato på saksid: $sakId")
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.message!!, xid))
            }
        }
    }


    @Operation(description = "Validerer pensjonssaker for å forhindre feil under prefill")
    @GetMapping("/validate/{aktoerId}/sakId/{sakId}/buctype/{buctype}")
    fun validerKravPensjon(@PathVariable("aktoerId", required = true) aktoerId: String, @PathVariable("sakId", required = true) sakId: String, @PathVariable("buctype", required = true) bucType: String): Boolean {
        return PensjonControllerValidateSak.measure {

            val pendata = pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId)
            if (pendata.brukersSakerListe == null) {
                logger.warn("Ingen gyldig brukerSakerListe funnet")
                throw PensjoninformasjonException("Ingen gyldig brukerSakerListe, mangler data fra pesys")
            }

            val sak = PensjonsinformasjonClient.finnSak(sakId, pendata) ?: return@measure false

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

    @GetMapping("/vedtak/{vedtakid}/vilkarsvurdering")
    fun hentVedtakforVilkarsVurderingList(@PathVariable("vedtakid", required = true) vedtakId: String): List<V1Vilkarsvurdering> {
        val pensjonsinformasjon = pensjonsinformasjonClient.hentAltPaaVedtak(vedtakId)
        logger.debug("--".repeat(100))
        logger.debug("vilkarsliste sizze : ${pensjonsinformasjon.vilkarsvurderingListe.vilkarsvurderingListe.size}")

        val vilkarsvurderingListe = pensjonsinformasjon.vilkarsvurderingListe.vilkarsvurderingListe

        val vilkarsvurderingUforetrygdListe = vilkarsvurderingListe.mapNotNull { it.vilkarsvurderingUforetrygd }
        logger.debug("vilkarsvurdering ufore: ${vilkarsvurderingUforetrygdListe.size}")

        vilkarsvurderingUforetrygdListe.forEach { v1ufore ->
            logger.debug("--".repeat(100))
            logger.debug("Uforetidspunkt: ${v1ufore.uforetidspunkt}")

            logger.debug("ungUfor: ${v1ufore.ungUfor}")
            logger.debug("isYrkesskade: ${v1ufore.isYrkesskade}")
            logger.debug("hensiktsmessigArbeidsrettedeTiltak: ${v1ufore.hensiktsmessigArbeidsrettedeTiltak}")
        }

        return vilkarsvurderingListe
    }

    @GetMapping("/vedtak/{vedtakid}/pensjoninfo")
    fun hentVedtakforPensjonsinformasjon(@PathVariable("vedtakid", required = true) vedtakId: String): String {
        val vedtak = pensjonsinformasjonClient.hentAltPaaVedtak(vedtakId)
        val mapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerModule(SimpleModule().addSerializer(XMLGregorianCalendar::class.java, LocalDateSerializer()))
        val jsonobj: Any = mapper.readValue(mapper.writeValueAsString(vedtak), Any::class.java)
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonobj)
    }

    @GetMapping("/sak/aktoer/{ident}/sakid/{sakid}/pensjonsak")
    fun hentSakPensjonsinformasjon(@PathVariable("ident", required = true) ident: String, @PathVariable("sakid", required = true) sakid: String): String {
        val saker = pensjonsinformasjonClient.hentAltPaaAktoerId(ident)
        logger.info("saker: ${saker.brukersSakerListe.brukersSakerListe.size}")
        val sak = saker?.let { PensjonsinformasjonClient.finnSak(sakid, it) }
        logger.info("den fakiske sak: ${sak != null}")
        sak?.let {
            val mapper = ObjectMapper()
                .registerModule(JavaTimeModule())
                .registerModule(SimpleModule().addSerializer(XMLGregorianCalendar::class.java, LocalDateSerializer()))
            val jsonobj: Any = mapper.readValue(mapper.writeValueAsString(it), Any::class.java)
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonobj)
        }
        return "NA"
    }

    private class LocalDateSerializer(): JsonSerializer<XMLGregorianCalendar>() {
        override fun serialize(value: XMLGregorianCalendar?, gen: JsonGenerator?, serializers: SerializerProvider?) {
            gen?.let { jGen ->
                value?.let { xmldate ->
                    val localDate = LocalDate.of(
                        xmldate.getYear(),
                        xmldate.getMonth(),
                        xmldate.getDay());
                    jGen.writeString(localDate.toString())
                } ?: jGen.writeNull()
            }
        }
    }

    @GetMapping("/vedtak/{vedtakid}/uforetidspunkt")
    fun hentVedtakforForUfor(@PathVariable("vedtakid", required = true) vedtakId: String): String? {
        val pensjonsinformasjon = pensjonsinformasjonClient.hentAltPaaVedtak(vedtakId)
        val vilkarsvurderingListe = pensjonsinformasjon.vilkarsvurderingListe.vilkarsvurderingListe
        val vilkarsvurderingUforetrygdListe = vilkarsvurderingListe.mapNotNull { it.vilkarsvurderingUforetrygd }
        val uforetidspunkt = vilkarsvurderingUforetrygdListe.map { v1ufore ->
            logger.debug("Uforetidspunkt: ${v1ufore.uforetidspunkt}")
            if (v1ufore.uforetidspunkt != null) {
                val uftdato = transformXMLGregorianCalendarToJson(v1ufore.uforetidspunkt)
                mapOf("uforetidspunkt" to uftdato.toString()).toJson()
           } else {
                mapOf("uforetidspunkt" to null).toJson()
            }
        }.firstOrNull()

        logger.info("Uforetidspunkt: $uforetidspunkt")
        return uforetidspunkt
    }

    fun transformXMLGregorianCalendarToJson(v1uforetidpunkt: XMLGregorianCalendar): LocalDate {
        val xmltimezone = v1uforetidpunkt.timezone
        logger.debug("xmlTimeZone: $xmltimezone, xmlUforetidspunkt: $v1uforetidpunkt")

        val uft = LocalDateTime.of(v1uforetidpunkt.year, v1uforetidpunkt.month, v1uforetidpunkt.day, v1uforetidpunkt.hour, v1uforetidpunkt.minute, v1uforetidpunkt.second)
        logger.info("Uforetidspunkt: $uft")

        val uforetidspunkt = if (xmltimezone == 0) {
            logger.info("Konverterer uføretidspunkt tidssone +1time")
            uft.plusHours(1).toLocalDate()
        } else {
            logger.info("Ingen konverterer av uføretidspunkt")
            uft.toLocalDate()
        }

        if (uforetidspunkt?.dayOfMonth != 1) logger.error("Feiler ved uføretidspunkt: $uforetidspunkt, Dag er ikke første i mnd")
        logger.debug("utføretidspunkt: ${uforetidspunkt.toString()}")
        return uforetidspunkt
    }

    @Operation(description = "Henter ut en liste over alle saker på valgt aktoerId")
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