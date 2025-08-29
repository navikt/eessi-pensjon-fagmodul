package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.pesys.krav.P1Dto
import no.nav.eessi.pensjon.fagmodul.pesys.krav.P1Person
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate


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
    private val kodeverkClient: KodeverkClient,
    private val trygdeTidService: HentTrygdeTid,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

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

    data class TrygdetidRequest(
        val fnr: String,
        val rinaNr: Int? = null
    )

    @PostMapping("/hentTrygdetid")
    fun hentTrygdetid(@RequestBody request: TrygdetidRequest): TrygdetidForPesys{
        logger.debug("Henter trygdetid for fnr: ${request.fnr.takeLast(4)}, rinaNr: ${request.rinaNr}")
        return trygdeTidMetric.measure {
            gcpStorageService.hentTrygdetid(request.fnr)?.let {
                runCatching { parseTrygdetid(it) }
                    .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                    .getOrNull()
            }?.let { trygdetid ->
                val trygdetidFraAlleBuc = trygdetid.flatMap { it.second }.sortedBy { it.startdato }
                TrygdetidForPesys(request.fnr, trygdetidFraAlleBuc).also { logger.debug("Trygdetid response: $it") }
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
    ) : List<P1Dto> {
        logger.info("Henter P6000 detaljer fra bucket for pesysId: $pesysId")
        return p6000Metric.measure {
            val p6000FraGcp = gcpStorageService.hentGcpDetlajerForP6000(pesysId) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ingen P6000-detaljer funnet for pesysId: $pesysId"
            )
            val listeOverP6000FraGcp = mutableListOf<P6000>()
            val p6000Detaljer = mapJsonToAny<P6000Detaljer>(p6000FraGcp)
            logger.info("P6000Detaljer: ${p6000Detaljer.toJson()}")
            runCatching {
                p6000Detaljer.dokumentId.forEach { p6000 ->
                    val hentetP6000 = euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(p6000Detaljer.rinaSakId, p6000) as P6000
                    hentetP6000.let { listeOverP6000FraGcp.add(it) }
                }
            }
                .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                .onSuccess { logger.info("Hentet nye dok detaljer fra Rina for ${it.toJson()}") }
            listeOverP6000FraGcp.map { sed -> P1Dto(
                innehaver = person(sed),
                forsikrede = person(sed),
                sakstype = "Gjenlevende",
                kravMottattDato = LocalDate.now(),
                innvilgedePensjoner = emptyList(),
                avslaattePensjoner = emptyList(),
                utfyllendeInstitusjon = ""
            ) }
        }
    }

    private fun person(sed: P6000) : P1Person {
        val person = sed.pensjon?.gjenlevende?.person
        val adresse = sed.pensjon?.gjenlevende?.adresse

        return P1Person(
            fornavn = person?.fornavn,
            etternavn = person?.etternavn,
            etternavnVedFoedsel = person?.etternavnvedfoedsel,
            foedselsdato = dato(person?.foedselsdato),
            adresselinje = adresse?.postadresse,
            poststed = kodeverkClient.hentPostSted(adresse?.postnummer)?.sted,
            postnummer = adresse?.postnummer,
            landkode = adresse?.land
        )
    }

    private fun dato(foedselsdato: String?): LocalDate? {
        if (foedselsdato == null) return null
        return try {
            LocalDate.parse(foedselsdato)
        } catch (ex: Exception) {
            null
        }
    }

    fun parseTrygdetid(input: List<Pair<String, String?>>): List<Pair<String?, List<Trygdetid>>>{
        return input.mapNotNull { jsonString ->
            val trygdetid = jsonString.first
            val rinaNr = jsonString.second?.split(Regex("\\D+"))?.lastOrNull { it.isNotEmpty() }
            val cleanedJson = trygdetid.trim('"').replace("\\n", "").replace("\\\"", "\"")
            val trygdeTidListe = mapJsonToAny<List<Trygdetid>>(cleanedJson).map {
                if (it.land.length == 2) {
                    it.copy(land = kodeverkClient.finnLandkode(it.land) ?: it.land)
                } else it
            }
            Pair(rinaNr, trygdeTidListe)
        }
    }

    data class P6000Detaljer(
        val pesysId: String,
        val rinaSakId: String,
        val dokumentId: List<String>
    )

    class EmptyStringToNullDeserializer : JsonDeserializer<String?>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? {
            return p.valueAsString.takeIf { !it.isNullOrBlank() }
        }
    }
}
