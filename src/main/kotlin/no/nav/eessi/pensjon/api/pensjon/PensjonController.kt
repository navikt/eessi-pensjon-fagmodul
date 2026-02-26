package no.nav.eessi.pensjon.api.pensjon

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PesysService
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
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
import java.util.UUID
import javax.xml.datatype.XMLGregorianCalendar

@Protected
@RestController
@RequestMapping("/pensjon")
class PensjonController(
    private val pesysService: PesysService,
    private val auditlogger: AuditLogger,
    private val innhentingService: InnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(PensjonController::class.java)

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
    fun hentPensjonSakType(@PathVariable("sakId", required = true) sakId: String, @PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String>? {
        auditlogger.log("hentPensjonSakType", aktoerId)

        return pensjonControllerHentSakType.measure {
            try {
                logger.info("Henter sakstype på $sakId / $aktoerId")
                pesysService.hentSaktype(sakId)?.let {
                    ResponseEntity.ok(it.name)
                } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sakstype ikke funnet for sakId: $sakId")
            } catch (e: Exception) {
                logger.warn("Feil ved henting av sakstype på saksid: $sakId")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("")
            }
        }
    }

    /**
     * Brukes for å hente kravdato fra frontend / EP
     */
    @GetMapping("/kravdato/saker/{saksId}/krav/{kravId}/aktor/{aktoerId}")
    fun hentKravDatoFraAktor(@PathVariable("saksId", required = true) sakId: String, @PathVariable("kravId", required = true) kravId: String, @PathVariable("aktoerId", required = true) aktoerId: String) : ResponseEntity<String>? {
        return pensjonControllerKravDato.measure {
            val xid = MDC.get("x_request_id") ?: UUID.randomUUID().toString()
            if (sakId.isEmpty() || kravId.isEmpty() || aktoerId.isEmpty()) {
                logger.warn("Det mangler verdier: saksId $sakId, kravId: $kravId, aktørId: $aktoerId")
                return@measure ResponseEntity.status(HttpStatus.BAD_REQUEST).body("")
            }
            return@measure try {
                pesysService.hentKravdato(kravId)?.let {
                    ResponseEntity.ok("""{ "kravDato": "$it" }""")
                } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("Feiler å hente kravDato", xid))
            } catch (e: Exception) {
                logger.warn("Feil ved henting av kravdato på saksid: $sakId", e)
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("")
            }
        }
    }


//    @GetMapping("/validate/{aktoerId}/sakId/{sakId}/buctype/{buctype}")
//    fun validerKravPensjon(@PathVariable("aktoerId", required = true) aktoerId: String, @PathVariable("sakId", required = true) sakId: String, @PathVariable("buctype", required = true) bucType: String): Boolean {
//        return pensjonControllerValidateSak.measure {
//
//            val pendata = pensjonsinformasjonService.hentAltPaaAktoerId(aktoerId)
//            if (pendata.brukersSakerListe == null) {
//                logger.warn("Ingen gyldig brukerSakerListe funnet")
//                throw PensjoninformasjonException("Ingen gyldig brukerSakerListe, mangler data fra pesys")
//            }
//
//            val sak = FinnSak.finnSak(sakId, pendata) ?: return@measure false
//
//            return@measure when(bucType) {
//                "P_BUC_01", "P_BUC_03" -> {
//                    PensjoninformasjonValiderKrav.validerGyldigKravtypeOgArsak(sak, bucType)
//                    true
//                }
//                "P_BUC_02" -> {
//                    PensjoninformasjonValiderKrav.validerGyldigKravtypeOgArsakGjenlevnde(sak, bucType)
//                    true
//                }
//                else -> true
//            }
//        }
//    }

//    @GetMapping("/vedtak/{vedtakid}/vilkarsvurdering")
//    fun hentVedtakforVilkarsVurderingList(@PathVariable("vedtakid", required = true) vedtakId: String): List<V1Vilkarsvurdering> {
//        val pensjonsinformasjon = pensjonsinformasjonService.hentAltPaaVedtak(vedtakId).also {
//            logger.debug("pensjonInfo: ${it.toJsonSkipEmpty()}")
//        }
//        logger.debug("--".repeat(100))
//        logger.debug("vilkarsliste sizze : ${pensjonsinformasjon.vilkarsvurderingListe.vilkarsvurderingListe.size}")
//
//        val vilkarsvurderingListe = pensjonsinformasjon.vilkarsvurderingListe.vilkarsvurderingListe
//
//        val vilkarsvurderingUforetrygdListe = vilkarsvurderingListe.mapNotNull { it.vilkarsvurderingUforetrygd }
//        logger.debug("vilkarsvurdering ufore: ${vilkarsvurderingUforetrygdListe.size}")
//
//        vilkarsvurderingUforetrygdListe.forEach { v1ufore ->
//            logger.debug("--".repeat(100))
//            logger.debug("Uforetidspunkt: ${v1ufore.uforetidspunkt}")
//
//            logger.debug("ungUfor: ${v1ufore.ungUfor}")
//            logger.debug("isYrkesskade: ${v1ufore.isYrkesskade}")
//            logger.debug("hensiktsmessigArbeidsrettedeTiltak: ${v1ufore.hensiktsmessigArbeidsrettedeTiltak}")
//        }
//
//        return vilkarsvurderingListe
//    }

//    @GetMapping("/vedtak/{vedtakid}/pensjoninfo")
//    fun hentVedtakforPensjonsinformasjon(@PathVariable("vedtakid", required = true) vedtakId: String): String {
//        val vedtak = pensjonsinformasjonService.hentAltPaaVedtak(vedtakId).also {
//            logger.debug("pensjonInfo: ${it.toJsonSkipEmpty()}")
//        }
//        val mapper = ObjectMapper()
//            .registerModule(JavaTimeModule())
//            .registerModule(SimpleModule().addSerializer(XMLGregorianCalendar::class.java, LocalDateSerializer()))
//        val jsonobj: Any = mapper.readValue(mapper.writeValueAsString(vedtak), Any::class.java)
//        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonobj)
//    }

    private class LocalDateSerializer : JsonSerializer<XMLGregorianCalendar>() {
        override fun serialize(value: XMLGregorianCalendar?, gen: JsonGenerator?, serializers: SerializerProvider?) {
            gen?.let { jGen ->
                value?.let { xmldate ->
                    val localDate = LocalDate.of(
                        xmldate.year,
                        xmldate.month,
                        xmldate.day
                    )
                    jGen.writeString(localDate.toString())
                } ?: jGen.writeNull()
            }
        }
    }

    /**
     * Brukes for å hente vedtak fra frontend / EP
     */
    @GetMapping("/vedtak/{vedtakid}/uforetidspunkt")
    fun hentVedtakforForUfor(@PathVariable("vedtakid", required = true) vedtakId: String): String? {
        val pensjonsinformasjon = pesysService.hentUfoeretidspunktOnVedtak(vedtakId).also {
            logger.debug("pensjonInfo: ${it?.toJsonSkipEmpty()}")
        }

//        val vilkarsvurderingListe = pensjonsinformasjon.vilkarsvurderingListe.vilkarsvurderingListe
//        val vilkarsvurderingUforetrygdListe = vilkarsvurderingListe.mapNotNull { it.vilkarsvurderingUforetrygd }
//        val uforetidspunkt = vilkarsvurderingUforetrygdListe.map { v1ufore ->
//            logger.debug("Uforetidspunkt-kandidat: ${v1ufore.uforetidspunkt}")
//            if (v1ufore.uforetidspunkt != null) transformXMLGregorianCalendarToJson(v1ufore.uforetidspunkt).toString() else null
//        }.firstOrNull()
//
//        val virkningstidspunktXML = pensjonsinformasjon.vedtak?.virkningstidspunkt // Kan teoretisk ikke inneholde vedtak
//        val virkningstidspunkt = if (virkningstidspunktXML != null) transformXMLGregorianCalendarToJson(virkningstidspunktXML).toString() else null

        val resultatJson = mapOf(
            "uforetidspunkt" to pensjonsinformasjon?.uforetidspunkt,
            "virkningstidspunkt" to pensjonsinformasjon?.virkningstidspunkt
        ).toJson()

        logger.info("Oppslag på uføretidspunkt ga: $resultatJson")
        return resultatJson
    }

//    fun transformXMLGregorianCalendarToJson(v1uforetidpunkt: XMLGregorianCalendar): LocalDate {
//        val xmltimezone = v1uforetidpunkt.timezone
//        logger.debug("xmlTimeZone: $xmltimezone, xmlUforetidspunkt: $v1uforetidpunkt")
//
//        val uft = LocalDateTime.of(v1uforetidpunkt.year, v1uforetidpunkt.month, v1uforetidpunkt.day, v1uforetidpunkt.hour, v1uforetidpunkt.minute, v1uforetidpunkt.second)
//        logger.info("Uforetidspunkt: $uft")
//
//        val uforetidspunkt = if (xmltimezone == 0) {
//            logger.info("Konverterer uføretidspunkt tidssone +1time")
//            uft.plusHours(1).toLocalDate()
//        } else {
//            logger.info("Ingen konverterer av uføretidspunkt")
//            uft.toLocalDate()
//        }
//
//        if (uforetidspunkt?.dayOfMonth != 1) logger.error("Feiler ved uføretidspunkt: $uforetidspunkt, Dag er ikke første i mnd")
//        logger.debug("utføretidspunkt: $uforetidspunkt")
//        return uforetidspunkt
//    }

    /**
     * Brukes for å henter sakliste for aktoer fra journalføring
     */
    @GetMapping("/sakliste/{aktoerId}")
    fun hentPensjonSakIder(@PathVariable aktoerId: String): List<PensjonSak> = pensjonControllerHentSakListe.measure {
        logger.info("Henter sakliste for aktoer: $aktoerId")
        try {
            val fnr = innhentingService.hentFnrfraAktoerService(aktoerId)
                ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fant ikke fnr for aktoerId: $aktoerId")
            val brukersSakerListe = pesysService.hentSakListe(fnr.id)
            if (brukersSakerListe.isEmpty()) {
                logger.error("Ingen brukersSakerListe funnet i pensjoninformasjon for aktoer: $aktoerId")
                throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ingen brukersSakerListe funnet i pensjoninformasjon for aktoer: $aktoerId")
            }
            brukersSakerListe.map { sak ->
                logger.debug("PensjonSak for journalføring: sakId: ${sak.sakId} sakType: ${sak.sakType} sakStatus: ${sak.sakStatus}")
                PensjonSak(sak.sakId, sak.sakType.name, SakStatus.from(sak.sakStatus.name))
            }
        } catch (ex: Exception) {
            logger.warn("Ingen pensjoninformasjon kunne hentes", ex)
            emptyList()
        }
    }
}

class PensjonSak (
        val sakId: String,
        val sakType: String,
        val sakStatus: SakStatus
)