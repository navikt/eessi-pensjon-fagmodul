package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRawValue
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.PreviewPdf
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.net.URLDecoder

@Protected
@RestController
@RequestMapping("/sed")
class SedController(
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
    @Value("\${eessipen-eux-rina.url}")
    private val euxrinaurl: String,
    private val gcpStorageService: GcpStorageService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private val logger = LoggerFactory.getLogger(SedController::class.java)

    private lateinit var sedsendt: MetricsHelper.Metric
    private var pdfGenerert: MetricsHelper.Metric

    init {
        pdfGenerert = metricsHelper.init("PdfGenerert")
    }

    @GetMapping("/getP6000/{euxcaseid}")
    fun getDocumentP6000list(
        @PathVariable("euxcaseid", required = true) euxcaseid: String
    ): FrontEndResponse<List<P6000Dokument>?> {
        val bucUtils = BucUtils(euxInnhentingService.getBuc(euxcaseid))
        return FrontEndResponse(bucUtils.getAllP6000AsDocumentItem(euxrinaurl), HttpStatus.OK.name)
    }

    @GetMapping("/get/{euxcaseid}/{documentid}/pdf")
    fun getPdfFromRina(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String
    ): FrontEndResponse<PreviewPdf> {
        return FrontEndResponse(euxInnhentingService.getPdfContents(euxcaseid, documentid), HttpStatus.OK.name)
    }

    @GetMapping("/get/{euxcaseid}/{documentid}")
    fun getDocument(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String
    ): FrontEndResponse<Any> {
        auditlogger.logBuc("getDocument", " euxCaseId: $euxcaseid documentId: $documentid")
        logger.info("Hente SED innhold for /${euxcaseid}/${documentid} ")
        val sed = euxInnhentingService.getSedOnBucByDocumentId(euxcaseid, documentid)

        if (sed is P8000) {
            val lagretOptions = gcpStorageService.hentGcpDetlajerPaaId(documentid)
            if (lagretOptions != null) {
                logger.info("Henter options for: ${sed.type}, rinaid: $euxcaseid, options: $lagretOptions")
            } else {
                logger.warn("Henter P8000 uten options")
            }
            val response = P8000FrontendResponse(sed.type, sed.nav, sed.p8000Pensjon, lagretOptions)
            return FrontEndResponse(response, HttpStatus.OK.name)
        }
        return FrontEndResponse(sed, HttpStatus.OK.name)
    }

    @PutMapping("/put/{euxcaseid}/{documentid}")
    fun updateSed(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String,
        @RequestBody sedPayload: String
    ): FrontEndResponse<Boolean> {
        val validsed = try {
            secureLog.info("Følgende SED payload: $sedPayload")

            val sed = SED.fromJsonToConcrete(sedPayload).also { secureLog.info("Følgende SED: ${it.toJson()}") }
            logger.info("Følgende SED prøves å oppdateres: ${sed.type}, rinaid: $euxcaseid")

            when (sed) {
                is P8000 -> {
                    val sedP8000Frontend = mapJsonToAny<P8000Frontend>(sedPayload)
                    sedP8000Frontend.options?.let {
                        val jsonDecoded = URLDecoder.decode(it, "UTF-8")
                        logger.info("Lagrer options for: ${sed.type}, rinaid: $euxcaseid, options: $jsonDecoded")
                        gcpStorageService.lagreP8000Options(documentid, jsonDecoded)
                    }
                    sed
                }
                is P5000 -> sed.updateFromUI() //må alltid kjøres. sjekk og oppdatert trydetid. punkt 5.2.1.3.1
                else -> sed
            }
        } catch (ex: Exception) {
            logger.error("Feil ved oppdatering av SED", ex)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Lagring av P8000 feilet, ugyldig SED: ${ex.message}", ex)
        }
        val jsonToRina = validsed.toJsonSkipEmpty().also { logger.debug("Følgende SED prøves å oppdateres til RINA: rinaID: $euxcaseid, documentid: $documentid, validsed: $it") }
        val updated = euxInnhentingService.updateSedOnBuc(euxcaseid, documentid, jsonToRina).also { logger.info("Oppdatering av SED: $it") }
        return FrontEndResponse(updated, HttpStatus.OK.name)
    }

    @GetMapping("/seds/{buctype}/{rinanr}")
    fun getSeds(
        @PathVariable(value = "buctype", required = true) bucType: String,
        @PathVariable(value = "rinanr", required = true) euxCaseId: String
    ): FrontEndResponse<List<SedType>> {
        val resultListe = BucUtils(euxInnhentingService.getBuc(euxCaseId)).getFiltrerteGyldigSedAksjonListAsString()
        logger.info("Henter liste over SED som kan opprettes på buctype: $bucType seds: $resultListe")
        return FrontEndResponse(resultListe, HttpStatus.OK.name)
    }

    //Ektend P5000 updateFromUI før den sendes og oppdateres i Rina.
    //punkt 5.2.1.3.1 i settes til "0" når gyldigperiode == "0"
    fun P5000.updateFromUI(): P5000 {
        val pensjon = this.pensjon
        val medlemskapboarbeid = pensjon?.medlemskapboarbeid
        val gyldigperiode = medlemskapboarbeid?.gyldigperiode
        val erTom = medlemskapboarbeid?.medlemskap.let { it == null || it.isEmpty() }
        if (gyldigperiode == "0" && erTom) {
           logger.info("P5000 setter 5.2.1.3.1 til 0 ")
            return this.copy(pensjon = P5000Pensjon(
                trygdetid = listOf(MedlemskapItem(sum = TotalSum(aar = "0"))),
                medlemskapboarbeid = medlemskapboarbeid,
                separatP5000sendes = "0"
            ))
        }
        return this
    }

    @PostMapping("/pdf")
    fun lagPdf(@RequestBody pdfJson: String): FrontEndResponse<PreviewPdf?> {
        logger.info("Lager PDF")
        val pdf = euxInnhentingService.lagPdf(pdfJson).also { logger.info("SED for generering av PDF: $it") }
        return FrontEndResponse(pdf, HttpStatus.OK.name)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class P8000Frontend(
    @JsonProperty("sed")
    type: SedType = SedType.P8000,
    nav: Nav? = null,
    @JsonProperty("pensjon")
    p8000Pensjon: P8000Pensjon?,
    @JsonInclude(JsonInclude.Include.ALWAYS)
    var options: String? = null,
) : P8000(type, nav, p8000Pensjon)

/**
 * Response variant of [P8000Frontend] used by `GET /sed/get/{euxcaseid}/{documentid}`.
 * `options` is exposed as a JSON object (the raw JSON stored in GCP) instead of a quoted
 * string, so the frontend can consume it directly without a second JSON.parse.
 *
 * Uses `@JsonRawValue` on a `String` field rather than a `JsonNode` field, because Spring's
 * default Jackson configuration serializes `JsonNode` properties via bean introspection in
 * this code path (emitting `isObject`, `nodeType`, etc. accessors) instead of the node's
 * actual tree content. This class is response-only so the round-trip concerns that apply to
 * [P8000Frontend] (URL-encoded inbound `options`) do not apply here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class P8000FrontendResponse(
    @JsonProperty("sed")
    type: SedType = SedType.P8000,
    nav: Nav? = null,
    @JsonProperty("pensjon")
    p8000Pensjon: P8000Pensjon?,
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonRawValue
    var options: String? = null,
) : P8000(type, nav, p8000Pensjon)