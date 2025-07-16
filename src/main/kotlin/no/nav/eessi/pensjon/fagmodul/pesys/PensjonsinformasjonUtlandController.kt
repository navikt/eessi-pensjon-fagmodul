package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException


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
    private val euxInnhentingService: EuxInnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    @Autowired
    private lateinit var kodeverkClient: KodeverkClient
    private var pensjonUtland: MetricsHelper.Metric = metricsHelper.init("pensjonUtland")
    private var trygdeTidMetric: MetricsHelper.Metric = metricsHelper.init("trygdeTidMetric")
    private var p6000Metric: MetricsHelper.Metric = metricsHelper.init("p6000Metric")

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

    @GetMapping("/hentP6000Detaljer")
    fun hentP6000Detaljer(
        @RequestParam("pesysId") pesysId: String
    ): List<P6000> {
        logger.info("Henter P6000 detaljer fra bucket for pesysId: $pesysId")
        return p6000Metric.measure {
            val p6000FraGcp = gcpStorageService.hentGcpDetlajerForP6000(pesysId) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ingen P6000-detaljer funnet for pesysId: $pesysId"
            )
            val listeOverP6000FraGcp = mutableListOf<P6000>()
            val p6000Detaljer = mapJsonToAny<P6000Detaljer>(p6000FraGcp)
            runCatching {
                p6000Detaljer.dokumentId.forEach { p6000 ->
                    val hentetP6000 = euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(p6000Detaljer.rinaSakId, p6000) as P6000
                    hentetP6000.let { listeOverP6000FraGcp.add(it) }
                }
            }
                .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                .onSuccess { logger.info("Hentet nye dok detaljer fra Rina for $it") }
            listeOverP6000FraGcp
        }
    }

    fun parseTrygdetid(jsonString: String): List<Trygdetid> {
        val cleanedJson = jsonString.trim('"').replace("\\n", "").replace("\\\"", "\"")
        return mapJsonToAny<List<Trygdetid>>(cleanedJson).map {
            if (it.land.length == 2) {
                it.copy(land = kodeverkClient.finnLandkode(it.land) ?: it.land)
            }
            else it
        }
    }

    data class TygdetidForPesys(
        val aktoerId: String?,
        val rinaNr: String,
        val trygdetid: List<Trygdetid> = emptyList(),
        val error: String? = null
    )

    data class P6000Detaljer(
        val pesysId: String,
        val rinaSakId: String,
        val dokumentId: List<String>
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
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
