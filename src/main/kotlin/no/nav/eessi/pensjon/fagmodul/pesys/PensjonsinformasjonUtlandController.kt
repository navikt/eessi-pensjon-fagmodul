package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandService.BrukerEllerGjenlevende.FORSIKRET
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandService.BrukerEllerGjenlevende.GJENLEVENDE
import no.nav.eessi.pensjon.fagmodul.pesys.krav.P1Dto
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
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
    private val penInfoUtlandService: PensjonsinformasjonUtlandService,
    private val gcpStorageService: GcpStorageService,
    private val euxInnhentingService: EuxInnhentingService,
    private val trygdeTidService: TrygdeTidService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private var pensjonUtland: MetricsHelper.Metric = metricsHelper.init("pensjonUtland")
    private var trygdeTidMetric: MetricsHelper.Metric = metricsHelper.init("trygdeTidMetric")
    private var p6000Metric: MetricsHelper.Metric = metricsHelper.init("p6000Metric")

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonUtlandController::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    @GetMapping("/hentKravUtland/{bucId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun hentKravUtland(@PathVariable("bucId", required = true) bucId: Int): KravUtland {
        return pensjonUtland.measure {
            penInfoUtlandService.hentKravUtland(bucId)!!
        }
    }

    @PostMapping("/hentTrygdetid")
    fun hentTrygdetid(@RequestBody request: TrygdetidRequest): TrygdetidForPesys{
        logger.debug("Henter trygdetid for fnr: ${request.fnr.takeLast(4)}, rinaNr: ${request.rinaNr}")
        return trygdeTidMetric.measure {
            gcpStorageService.hentTrygdetidFraGcp(request.fnr)?.let {
                runCatching { trygdeTidService.parseTrygdetid(it) }
                    .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                    .getOrNull()
            }?.let { trygdetid ->
                val trygdetidFraAlleBuc = trygdetid.flatMap { it.second }.sortedBy { it.startdato }
                if (trygdetidFraAlleBuc.isEmpty()) {
                    TrygdetidForPesys(request.fnr, emptyList(), "Det finnes ingen registrert trygdetid for fnr: ${request.fnr}")
                } else {
                    TrygdetidForPesys(request.fnr, trygdetidFraAlleBuc).also { logger.debug("Trygdetid response: $it") }
                }
            } ?: TrygdetidForPesys(
                request.fnr, emptyList(), "Det finnes ingen registrert trygdetid for fnr: ${request.fnr}"
            )
        }
    }

    @PostMapping("/hentTrygdetidV2")
    fun hentTrygdetidV2(@RequestBody request: TrygdetidRequest): TrygdetidForPesys {
        logger.debug("Henter trygdetid for fnr: ${request.fnr.takeLast(4)}, rinaNr: ${request.rinaNr}")
        return trygdeTidMetric.measure {
                runCatching { trygdeTidService.hentBucFraEux(request.rinaNr, request.fnr) }
                    .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                    .getOrNull() ?: TrygdetidForPesys(request.fnr, emptyList(), "Det finnes ingen registrert trygdetid for rinaNr: $request.rinaNr, aktoerId: $request.fnr")
        }
    }

    @GetMapping("/hentP6000Detaljer")
    fun hentP6000Detaljer(
        @RequestParam("pesysId") pesysId: String
    ) : P1Dto {
        logger.info("Henter P6000 detaljer fra bucket for pesysId: $pesysId")
        return p6000Metric.measure {
            val p6000FraGcp = gcpStorageService.hentGcpDetlajerForP6000(pesysId) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ingen P6000-detaljer funnet for pesysId: $pesysId"
            )
            val listeOverP6000FraGcp = mutableListOf<P6000>()
            val p6000Detaljer = mapJsonToAny<P6000Detaljer>(p6000FraGcp).also { logger.info("P6000Detaljer: ${it.toJson()}") }
            runCatching {
                p6000Detaljer.dokumentId.forEach { p6000 ->
                    val hentetJsonP6000 = euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(p6000Detaljer.rinaSakId, p6000)
                    val hentetP6000 = hentetJsonP6000 as P6000
                    val sedMetaData = euxInnhentingService.hentSedMetadata(p6000Detaljer.rinaSakId, p6000)
                    hentetP6000.retning = sedMetaData?.status
                    hentetP6000.let { listeOverP6000FraGcp.add(it) }
                }
            }
                .onFailure { e -> logger.error("Feil ved parsing av trygdetid linje 129", e) }
                .onSuccess { logger.info("Hentet nye dok detaljer fra Rina for $pesysId") }

            val innvilgedePensjoner = penInfoUtlandService.innvilgedePensjoner(listeOverP6000FraGcp).also { secureLog.info("innvilgedePensjoner: " +it.toJson()) }
            val avslaatteUtenlandskePensjoner = penInfoUtlandService.avslaatteUtenlandskePensjoner(listeOverP6000FraGcp).also { secureLog.info("avslaatteUtenlandskePensjoner: " + it.toJson()) }

            penInfoUtlandService.sjekkPaaGyldigeInnvElAvslPensjoner(innvilgedePensjoner, avslaatteUtenlandskePensjoner, listeOverP6000FraGcp, pesysId)

            val innehaverPin = penInfoUtlandService.hentPin(GJENLEVENDE, penInfoUtlandService.nyesteP6000(listeOverP6000FraGcp))
            val forsikredePin = penInfoUtlandService.hentPin(FORSIKRET, penInfoUtlandService.nyesteP6000(listeOverP6000FraGcp))

           P1Dto(
                innehaver = penInfoUtlandService.person(penInfoUtlandService.nyesteP6000(listeOverP6000FraGcp).first(), GJENLEVENDE, innehaverPin),
                forsikrede = penInfoUtlandService.person(penInfoUtlandService.nyesteP6000(listeOverP6000FraGcp).first(), FORSIKRET, forsikredePin),
                sakstype = "Gjenlevende",
                kravMottattDato = null,
                innvilgedePensjoner = innvilgedePensjoner,
                avslaattePensjoner = avslaatteUtenlandskePensjoner,
                utfyllendeInstitusjon = ""
            ).also { secureLog.info("P1Dto: " + it.toJson())}
        }
    }

    class EmptyStringToNullDeserializer : JsonDeserializer<String?>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? {
            return p.valueAsString.takeIf { !it.isNullOrBlank() }
        }
    }

}


