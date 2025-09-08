package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandController.BrukerEllerGjenlevende.FORSIKRET
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandController.BrukerEllerGjenlevende.GJENLEVENDE
import no.nav.eessi.pensjon.fagmodul.pesys.krav.AvslaattPensjon
import no.nav.eessi.pensjon.fagmodul.pesys.krav.InnvilgetPensjon
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
    ) : P1Dto {
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
                    val hentetJsonP6000 = euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(p6000Detaljer.rinaSakId, p6000)
                            val somP6000 = hentetJsonP6000 as P6000
                    logger.info("somP6000: $somP6000")
                    somP6000.let { listeOverP6000FraGcp.add(it) }

                }
            }
                .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                .onSuccess { logger.info("Hentet nye dok detaljer fra Rina for ${it.toJson()}") }
            val nyesteP6000 = listeOverP6000FraGcp.sortedBy { it.pensjon?.tilleggsinformasjon?.dato }.first()
            val utenlandskeP6000er = listeOverP6000FraGcp.filter { it -> it.nav?.eessisak?.any { it.land != "NO" } == true }
            val innvilgedePensjoner = innvilgedePensjoner(utenlandskeP6000er.filter { sed-> sed.pensjon?.vedtak?.any { it.resultat == "1" } == true })
            val avslaatteUtenlandskePensjoner = avslaatteUtenlandskePensjoner(utenlandskeP6000er.filter { sed -> sed.pensjon?.vedtak?.any { it.resultat == "2" } == true })

            if (innvilgedePensjoner.size + avslaatteUtenlandskePensjoner.size != utenlandskeP6000er.size) {
                logger.error("Mismatch: innvilgedePensjoner (${innvilgedePensjoner.size}) + avslåtteUtenlandskePensjoner (${avslaatteUtenlandskePensjoner.size}) != utenlandskeP6000er (${utenlandskeP6000er.size})")
            }
            P1Dto(
                innehaver = person(nyesteP6000, GJENLEVENDE),
                forsikrede = person(nyesteP6000, FORSIKRET),
                sakstype = "Gjenlevende",
                kravMottattDato = null,
                innvilgedePensjoner = innvilgedePensjoner,
                avslaattePensjoner = avslaatteUtenlandskePensjoner,
                utfyllendeInstitusjon = "",
                vedtaksdato = nyesteP6000.pensjon?.tilleggsinformasjon?.dato
            )
        }
    }

    private fun innvilgedePensjoner(p6000er: List<P6000>) : List<InnvilgetPensjon>{
        return p6000er.map {
            val vedtak = it.pensjon?.vedtak?.first()
            InnvilgetPensjon(
                institusjon = it.nav?.eessisak?.joinToString(", "),
                pensjonstype = vedtak?.type ?: "",
                datoFoersteUtbetaling = dato(vedtak?.beregning?.first()?.periode?.fom!!),
                bruttobeloep = vedtak.beregning?.first()?.beloepBrutto?.beloep,
                grunnlagInnvilget = vedtak.artikkel,
                reduksjonsgrunnlag = it.pensjon?.sak?.artikkel54,
                vurderingsperiode = it.pensjon?.sak?.kravtype?.first()?.datoFrist,
                adresseNyVurdering = it.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.joinToString(", ") ?: ""
            )
        }
    }

    private fun avslaatteUtenlandskePensjoner(p6000er: List<P6000>): List<AvslaattPensjon> {
        return p6000er.map {
            val vedtak = it.pensjon?.vedtak?.first()
            AvslaattPensjon(
                institusjon = it.nav?.eessisak?.joinToString(", "),
                pensjonstype = vedtak?.type,
                avslagsbegrunnelse = vedtak?.avslagbegrunnelse?.first {!it.begrunnelse.isNullOrEmpty()}?.begrunnelse ,
                vurderingsperiode = it.pensjon?.sak?.kravtype?.first()?.datoFrist,
                adresseNyVurdering = it.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.joinToString(", ") ?: ""
            )
        }
    }


    private fun person(sed: P6000, brukerEllerGjenlevende: BrukerEllerGjenlevende) : P1Person {
        val personBruker = if (brukerEllerGjenlevende == FORSIKRET) {
            Pair(sed.nav?.bruker?.person, sed.nav?.bruker?.adresse)
        } else {
            Pair(sed.pensjon?.gjenlevende?.person, sed.pensjon?.gjenlevende?.adresse)
        }

        return P1Person(
            fornavn = personBruker.first?.fornavn,
            etternavn = personBruker.first?.etternavn,
            etternavnVedFoedsel = personBruker.first?.etternavnvedfoedsel,
            foedselsdato = dato(personBruker.first?.foedselsdato),
            adresselinje = personBruker.second?.postadresse,
            poststed = kodeverkClient.hentPostSted(personBruker.second?.postnummer)?.sted,
            postnummer = personBruker.second?.postnummer,
            landkode = personBruker.second?.land
        )
    }

    enum class BrukerEllerGjenlevende(val person: String) {
        FORSIKRET ("forsikret"),
        GJENLEVENDE ("gjenlevende")
    }

    private fun dato(foedselsdato: String?): LocalDate? {
        return try {
            foedselsdato?.let { LocalDate.parse(it) }
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
