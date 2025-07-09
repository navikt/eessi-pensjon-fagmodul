package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonUtlandController::class.java)

    @GetMapping("/hentKravUtland/{bucId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun hentKravUtland(@PathVariable("bucId", required = true) bucId: Int): KravUtland {
        return pensjonUtland.measure {
            pensjonsinformasjonUtlandService.hentKravUtland(bucId)!!
        }
    }

    @GetMapping("/hentTrygdetid")
    fun hentTrygdetid(
        @RequestParam("aktorId") aktoerId: String,
        @RequestParam("rinaNr") rinaNr: String
    ): TygdetidForPesys? {
        logger.info("Henter trygdetid for aktoerId: $aktoerId, rinaNr: $rinaNr")
        return trygdeTidMetric.measure {
            gcpStorageService.hentTrygdetid(aktoerId, rinaNr)?.let {
                runCatching { parseTrygdetid(it) }
                    .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                    .getOrNull()
            }?.let { trygdetid ->
                TygdetidForPesys(aktoerId, rinaNr, trygdetid)
            } ?: TygdetidForPesys(
                aktoerId, rinaNr, emptyList(),
                "Det finnes ingen registrert trygdetid for rinaNr: $rinaNr, aktoerId: $aktoerId"
            )
        }
    }

    fun parseTrygdetid(jsonString: String): List<Trygdetid> {
        val cleanedJson = jsonString.trim('"').replace("\\n", "").replace("\\\"", "\"")
        return mapJsonToAny(cleanedJson)
    }

    data class TygdetidForPesys(
        val aktoerId: String?,
        val rinaNr: String,
        val trygdetid: List<Trygdetid> = emptyList(),
        val error: String? = null
    )

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class Trygdetid(
        val land: String,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val acronym: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val type: String?,
        val startdato: String,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val sluttdato: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val aar: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val mnd: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val dag: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val dagtype: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val ytelse: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val ordning: String?,
        @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
        val beregning: String?
    )

    class EmptyStringToNullDeserializer : JsonDeserializer<String?>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? {
            return p.valueAsString.takeIf { !it.isNullOrBlank() }
        }
    }
}
