package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import no.nav.eessi.pensjon.eux.model.sed.AndreinstitusjonerItem
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
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
    private val secureLog = LoggerFactory.getLogger("secureLog")

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
                    hentetP6000.let { listeOverP6000FraGcp.add(it) }
                }
            }
                .onFailure { e -> logger.error("Feil ved parsing av trygdetid", e) }
                .onSuccess { logger.info("Hentet nye dok detaljer fra Rina for $pesysId") }

            val nyesteP6000 = listeOverP6000FraGcp.sortedWith(
                compareBy<P6000> ( {it.pensjon?.tilleggsinformasjon?.dato }, { it.pensjon?.vedtak?.firstOrNull()?.virkningsdato})).reversed()

            val innvilgedePensjoner = innvilgedePensjoner(listeOverP6000FraGcp).also { secureLog.info("innvilgedePensjoner: " +it.toJson()) }
            val avslaatteUtenlandskePensjoner = avslaatteUtenlandskePensjoner(listeOverP6000FraGcp).also { secureLog.info("avslaatteUtenlandskePensjoner: " + it.toJson()) }

            if(innvilgedePensjoner.isEmpty() && avslaatteUtenlandskePensjoner.isEmpty()) {
                logger.error("Ingen gyldige pensjoner funnet i P6000er for pesysId: $pesysId")
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen gyldige pensjoner funnet i P6000er for pesysId: $pesysId")
            }

            if (innvilgedePensjoner.size + avslaatteUtenlandskePensjoner.size != listeOverP6000FraGcp.size) {
                logger.warn("Mismatch: innvilgedePensjoner (${innvilgedePensjoner.size}) + avslåtteUtenlandskePensjoner (${avslaatteUtenlandskePensjoner.size}) != utenlandskeP6000er (${listeOverP6000FraGcp.size})")
            }

            val innehaverPin = hentPin(
                hentBrukerEllerGjenlevende(GJENLEVENDE, nyesteP6000.first())
            )
            val forsikredePin = hentPin(
                hentBrukerEllerGjenlevende(FORSIKRET, nyesteP6000.first())
            )
           P1Dto(
                innehaver = person(nyesteP6000.first(), GJENLEVENDE, innehaverPin),
                forsikrede = person(nyesteP6000.first(), FORSIKRET, forsikredePin),
                sakstype = "Gjenlevende",
                kravMottattDato = null,
                innvilgedePensjoner = innvilgedePensjoner,
                avslaattePensjoner = avslaatteUtenlandskePensjoner,
                utfyllendeInstitusjon = ""
            ).also { secureLog.info("P1Dto: " + it.toJson())}
        }
    }


    private fun innvilgedePensjoner(p6000er: List<P6000>) : List<InnvilgetPensjon>{
        val ip6000Innvilgede = p6000er.filter { sed -> sed.pensjon?.vedtak?.any { it.resultat in listOf("01","03","04") } == true }
        val flereEnnEnNorsk =  erDetFlereNorskeInstitusjoner(p6000er)
        val retList = mutableListOf<InnvilgetPensjon>()

        ip6000Innvilgede.map { p6000 ->
            val vedtak = p6000.pensjon?.vedtak?.first()
            val institusjonFraSed = eessiInstitusjoner(p6000)
            if (flereEnnEnNorsk && retList.any { it.institusjon?.any { it.land == "NO" } == true } && institusjonFraSed?.any { it.land == "NO" } == true) {
                logger.error(" OBS OBS; Her kommer det inn mer enn 1 innvilget pensjon fra Norge")
                secureLog.info("Hopper over denne sed: $p6000")
            } else {
                retList.add(
                    InnvilgetPensjon(
                        institusjon = institusjonFraSed,
                        pensjonstype = vedtak?.type ?: "",
                        datoFoersteUtbetaling = dato(vedtak?.beregning?.first()?.periode?.fom),
                        bruttobeloep = vedtak?.beregning?.first()?.beloepBrutto?.beloep,
                        valuta = vedtak?.beregning?.first()?.valuta,
                        utbetalingsHyppighet = vedtak?.beregning?.first()?.utbetalingshyppighet,
                        grunnlagInnvilget = vedtak?.artikkel,
                        reduksjonsgrunnlag = p6000.pensjon?.sak?.artikkel54,
                        vurderingsperiode = p6000.pensjon?.sak?.kravtype?.first()?.datoFrist,
                        adresseNyVurdering = p6000.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.map { adresse(it) },
                        vedtaksdato = p6000.pensjon?.tilleggsinformasjon?.dato,
                    )
                )
            }
        }
        return retList
    }

    private fun hentAlleInstitusjoner(p6000er: List<P6000>): List<EessisakItem> {
        val eessisakItems = p6000er.flatMap { eessiInstitusjoner(it).orEmpty() }
        return eessisakItems
    }

    private fun erDetFlereNorskeInstitusjoner(p6000er: List<P6000>): Boolean{
        val alle = mutableListOf<EessisakItem>()
        p6000er.forEach { p6000 ->
            p6000.nav?.eessisak?.map {
                alle.add(EessisakItem(institusjonsid = it.institusjonsid, institusjonsnavn = it.institusjonsnavn, land = it.land, saksnummer = it.saksnummer))
            }
            val saksnummerFraTilleggsInformasjon = p6000.pensjon?.tilleggsinformasjon?.saksnummer
            p6000.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.map {
                alle.add(EessisakItem(institusjonsid = it.institusjonsid, institusjonsnavn = it.institusjonsnavn, land = it.land, saksnummer = saksnummerFraTilleggsInformasjon))
            }
        }
        val eessisakItems = p6000er.flatMap { alle }
        return eessisakItems.count { it.land == "NO" } > 1
    }

    private fun eessiInstitusjoner(p6000: P6000): List<EessisakItem>? {
        val eessisakItems = p6000.nav?.eessisak?.map {
            EessisakItem(institusjonsid = it.institusjonsid, institusjonsnavn = it.institusjonsnavn, land = it.land, saksnummer = it.saksnummer)
        }
        val saksnummerFraTilleggsInformasjon = p6000.pensjon?.tilleggsinformasjon?.saksnummer
        val andreInstitusjoner = p6000.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.map {
            EessisakItem(institusjonsid = it.institusjonsid, institusjonsnavn = it.institusjonsnavn, land = it.land, saksnummer = saksnummerFraTilleggsInformasjon)
        }

        val institusjon = when {
            eessisakItems?.isNotEmpty() == true && eessisakItems.any { it.land == "NO" } && andreInstitusjoner?.any { it.land != "NO" } == true -> andreInstitusjoner

            eessisakItems == null && andreInstitusjoner?.isNotEmpty() == true && andreInstitusjoner.size > 1 -> {
                logger.error("OBS OBS; Her kommer det inn mer enn 1 innvilget pensjon fra Norge (andreInstitusjoner); i Seden")
                emptyList()
            }

            eessisakItems == null && andreInstitusjoner?.isNotEmpty() == true -> {
                logger.warn("Det finnes ingen institusjon fra eessisak; henter institusjon fra andreInstitusjoner")
                andreInstitusjoner
            }

            eessisakItems?.isNotEmpty() == true && eessisakItems.count { it.land == "NO" } > 1 || (andreInstitusjoner?.count { it.land == "NO" } ?: 0) > 1 -> {
                logger.error("OBS OBS; Her kommer det inn mer enn 1 innvilget pensjon fra Norge i Seden")
                emptyList()
            }
            else -> eessisakItems
        }
        return institusjon
    }

    private fun adresse(p6000: AndreinstitusjonerItem) =
        AndreinstitusjonerItem(
            institusjonsid = p6000.institusjonsid,
            institusjonsnavn = p6000.institusjonsnavn,
            institusjonsadresse = p6000.institusjonsadresse,
            postnummer = p6000.postnummer,
            bygningsnavn = p6000.bygningsnavn,
            land = p6000.land,
            region = p6000.region,
            poststed = p6000.poststed
        )

    private fun avslaatteUtenlandskePensjoner(p6000er: List<P6000>): List<AvslaattPensjon> {
        val p6000erAvslaatt = p6000er.filter { sed -> sed.pensjon?.vedtak?.any { it.resultat == "02" } == true }
        val flereEnnEnNorsk = erDetFlereNorskeInstitusjoner(p6000erAvslaatt)
        val retList = mutableListOf<AvslaattPensjon>()

        p6000erAvslaatt.map { p6000 ->
            val vedtak = p6000.pensjon?.vedtak?.first()
            if (flereEnnEnNorsk && retList.any { it.institusjon?.any { it.land == "NO" } == true } &&
                eessiInstitusjoner(p6000)?.any { it.land == "NO" } == true) {
                logger.error(" OBS OBS; Her kommer det inn mer enn 1 avslått pensjon fra Norge")
                secureLog.info("Hopper over denne avslåtte seden: $p6000")
            } else {
                val institusjon = eessiInstitusjoner(p6000)
                retList.add(
                    AvslaattPensjon(
                        institusjon = institusjon,
                        pensjonstype = vedtak?.type,
                        avslagsbegrunnelse = vedtak?.avslagbegrunnelse?.first { !it.begrunnelse.isNullOrEmpty() }?.begrunnelse,
                        vurderingsperiode = p6000.pensjon?.sak?.kravtype?.first()?.datoFrist,
                        adresseNyVurdering = p6000.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.map { adresse(it) },
                        vedtaksdato = p6000.pensjon?.tilleggsinformasjon?.dato
                    )
                )
            }
        }
        return retList
    }



    private fun person(sed: P6000, brukerEllerGjenlevende: BrukerEllerGjenlevende, innehaverPin: List<PinItem>?) : P1Person {
        val personBruker = if (brukerEllerGjenlevende == FORSIKRET)
            Pair(sed.nav?.bruker?.person, sed.nav?.bruker?.adresse)
        else
            Pair(sed.pensjon?.gjenlevende?.person, sed.pensjon?.gjenlevende?.adresse)

        return P1Person(
            fornavn = personBruker.first?.fornavn,
            etternavn = personBruker.first?.etternavn,
            etternavnVedFoedsel = personBruker.first?.etternavnvedfoedsel,
            foedselsdato = dato(personBruker.first?.foedselsdato),
            adresselinje = personBruker.second?.postadresse,
            poststed = kodeverkClient.hentPostSted(personBruker.second?.postnummer)?.sted,
            postnummer = personBruker.second?.postnummer,
            landkode = personBruker.second?.land,
            pin = innehaverPin
        )
    }

    private fun hentBrukerEllerGjenlevende(
        brukerEllerGjenlevende: BrukerEllerGjenlevende,
        sed: P6000
    ): Person? = if (brukerEllerGjenlevende == FORSIKRET)
        sed.nav?.bruker?.person
    else
        sed.pensjon?.gjenlevende?.person

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

    fun parseTrygdetid(lagretTrygdetid: List<Pair<String, String?>>): List<Pair<String?, List<Trygdetid>>>? {
        return lagretTrygdetid.map { (trygdetid, rinaNr) ->
            val json = trygdetid.trim('"')
                .replace("\\n", "")
                .replace("\\\"", "\"")

            val trygdeTidListe = hentLandFraKodeverk(json)

            val rinaId = rinaNr?.split(Regex("\\D+"))
                ?.lastOrNull { it.isNotEmpty() }

            rinaId to trygdeTidListe
        }
    }

    private fun hentLandFraKodeverk(json: String): List<Trygdetid> = mapJsonToAny<List<Trygdetid>>(json).map { trygdetid ->
        trygdetid.takeIf { it.land.length != 2 } ?: trygdetid.copy(
            land = kodeverkClient.finnLandkode(trygdetid.land) ?: trygdetid.land
        )
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

    private fun hentPin(person: Person?): List<PinItem>? {
        return person?.pin?.asSequence()
            ?.filter { pinItem -> pinItem.land != null}
            ?.toList()
            ?.distinct() //TODO: Kan fjernes?
    }
}

