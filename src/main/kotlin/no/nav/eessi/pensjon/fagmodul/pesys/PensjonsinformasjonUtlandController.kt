package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.model.sed.*
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
import java.util.Locale.getDefault


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
    private val euxKlientAsSystemUser: EuxKlientAsSystemUser,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private var pensjonUtland: MetricsHelper.Metric = metricsHelper.init("pensjonUtland")
    private var trygdeTidMetric: MetricsHelper.Metric = metricsHelper.init("trygdeTidMetric")
    private var p6000Metric: MetricsHelper.Metric = metricsHelper.init("p6000Metric")

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonUtlandController::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    private val liste = mutableListOf<Pair<String, String>>()

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
                    val sedMetaData = euxKlientAsSystemUser.hentSedMetadata(p6000Detaljer.rinaSakId, p6000)
                    hentetP6000.retning = sedMetaData?.status
                    hentetP6000.let { listeOverP6000FraGcp.add(it) }
                }
            }
                .onFailure { e -> logger.error("Feil ved parsing av trygdetid linje 129", e) }
                .onSuccess { logger.info("Hentet nye dok detaljer fra Rina for $pesysId") }

            val nyesteP6000 = listeOverP6000FraGcp.sortedWith(
                compareBy(
                    { it.pensjon?.tilleggsinformasjon?.dato },
                    { it.pensjon?.vedtak?.firstOrNull()?.virkningsdato }
                )
            ).reversed()

            val innvilgedePensjoner = innvilgedePensjoner(listeOverP6000FraGcp).also { secureLog.info("innvilgedePensjoner: " +it.toJson()) }
            val avslaatteUtenlandskePensjoner = avslaatteUtenlandskePensjoner(listeOverP6000FraGcp).also { secureLog.info("avslaatteUtenlandskePensjoner: " + it.toJson()) }

            if(innvilgedePensjoner.isEmpty() && avslaatteUtenlandskePensjoner.isEmpty()) {
                logger.error("Ingen gyldige pensjoner funnet i P6000er for pesysId: $pesysId")
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen gyldige pensjoner funnet i P6000er for pesysId: $pesysId")
            }

            if (innvilgedePensjoner.size + avslaatteUtenlandskePensjoner.size != listeOverP6000FraGcp.size) {
                logger.warn("Mismatch: innvilgedePensjoner (${innvilgedePensjoner.size}) + avslåtteUtenlandskePensjoner (${avslaatteUtenlandskePensjoner.size}) != utenlandskeP6000er (${listeOverP6000FraGcp.size})")
            }

            val innehaverPin = hentPin(GJENLEVENDE,  nyesteP6000)
            val forsikredePin = hentPin(FORSIKRET,  nyesteP6000)

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
            .sortedByDescending { it.nav?.eessisak?.isNotEmpty() == true }
        val flereEnnEnNorsk =  erDetFlereNorskeInstitusjoner(p6000er)
        val retList = mutableListOf<InnvilgetPensjon>()

        ip6000Innvilgede.map { p6000 ->
            val vedtak = p6000.pensjon?.vedtak?.first()

            if (p6000.retning.isNorsk() && flereEnnEnNorsk && retList.count { it.retning.isNorsk()  } >= 1) {
                logger.error(" OBS OBS; Her kommer det inn mer enn 1 innvilget pensjon fra Norge")
                secureLog.info("Hopper over innvilget pensjon P6000: $p6000")
            } else {
                logger.info("Legger til innvilget pensjon fra sed med retning: ${p6000.retning}")
                retList.add(
                    InnvilgetPensjon(
                        institusjon = eessiInstitusjoner(p6000),
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
                        retning = p6000.retning
                    )
                )
            }
        }
        return retList
    }

    private fun erDetFlereNorskeInstitusjoner(p6000er: List<P6000>): Boolean {
        return p6000er.count { norskSed(it) }.let { antallUt ->
            if (antallUt > 1) {
                logger.error("OBS OBS; Her kommer det inn mer enn 1 P6000 med retning UT i Seden")
                return true
            }
            false
        }
    }

    private fun norskSed(p6000: P6000): Boolean =
        SED_RETNING.valueOf(p6000.retning) in listOf(SED_RETNING.NEW, SED_RETNING.SENT)

    //TODO: refaktorere metoden; for kompleks
    private fun eessiInstitusjoner(p6000: P6000): List<EessisakItem>? {
        val saksnummerFraTilleggsInformasjon = p6000.pensjon?.tilleggsinformasjon?.saksnummer
        val norskeEllerUtlandskeInstitusjoner = if(p6000.retning.isNorsk()) {
            // primært hentes norske institusjoner fra eessisak
            if(p6000.nav?.eessisak?.isNotEmpty() == true) {
                p6000.nav?.eessisak?.map {
                    EessisakItem(it.institusjonsid, it.institusjonsnavn, it.saksnummer, "NO")
                }
            }
            // benytter andreinstitusjoner, hvis ingen norske institusjoner i eessisak
            else {
                p6000.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.filter { it.land == "NO" }?.map {
                    EessisakItem(it.institusjonsid, it.institusjonsnavn, saksnummerFraTilleggsInformasjon, it.land)
                }
            }
        }
        else {
            // primært hentes utenlandske institusjoner fra andreinstitusjoner
            val utenlandsk = p6000.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.filter { it.land != "NO" }?.map {
                EessisakItem(it.institusjonsid, it.institusjonsnavn, saksnummerFraTilleggsInformasjon, it.land)
            }
            if(utenlandsk?.isEmpty() == true) {
                p6000.nav?.eessisak?.filter { it.land != "NO" }?.map {
                    EessisakItem(it.institusjonsid, it.institusjonsnavn, it.saksnummer, it.land)
                }
            }
            utenlandsk
        }

        val eessisakItems = p6000.nav?.eessisak?.map {
            EessisakItem(institusjonsid = it.institusjonsid, institusjonsnavn = it.institusjonsnavn, land = it.land, saksnummer = it.saksnummer)
        }

        val institusjon = when {
            eessisakItems?.isNotEmpty() == true && norskeEllerUtlandskeInstitusjoner?.any { it.land != "NO" } == true -> norskeEllerUtlandskeInstitusjoner

            eessisakItems == null && norskeEllerUtlandskeInstitusjoner?.isNotEmpty() == true && norskeEllerUtlandskeInstitusjoner.size > 1 -> {
                logger.error("OBS OBS; Her kommer det inn mer enn 1 innvilget pensjon fra Norge (andreInstitusjoner); i Seden")
                emptyList()
            }

            eessisakItems == null && norskeEllerUtlandskeInstitusjoner?.isNotEmpty() == true -> {
                logger.warn("Det finnes ingen institusjon fra eessisak; henter institusjon fra andreInstitusjoner")
                norskeEllerUtlandskeInstitusjoner
            }

            eessisakItems?.isNotEmpty() == true && eessisakItems.count { it.land == "NO" } > 1 || (norskeEllerUtlandskeInstitusjoner?.count { it.land == "NO" } ?: 0) > 1 -> {
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
            .sortedByDescending { it.pensjon?.tilleggsinformasjon?.dato }

        val flereEnnEnNorsk = erDetFlereNorskeInstitusjoner(p6000erAvslaatt)
        val retList = mutableListOf<AvslaattPensjon>()

        p6000erAvslaatt.map { p6000 ->
            val vedtak = p6000.pensjon?.vedtak?.first()

            if (p6000.retning.isNorsk() && flereEnnEnNorsk && retList.count { it.retning.isNorsk()  } >= 1) {
                logger.error(" OBS OBS; Her kommer det inn mer enn 1 avslått pensjon fra Norge")
                secureLog.info("Hopper over denne avslåtte seden: $p6000")
            } else {
                logger.info("Legger til avslått pensjon fra sed med retning: ${p6000.retning}")
                val institusjon = eessiInstitusjoner(p6000)
                retList.add(
                    AvslaattPensjon(
                        institusjon = institusjon,
                        pensjonstype = vedtak?.type,
                        avslagsbegrunnelse = vedtak?.avslagbegrunnelse?.first { !it.begrunnelse.isNullOrEmpty() }?.begrunnelse,
                        vurderingsperiode = p6000.pensjon?.sak?.kravtype?.first()?.datoFrist,
                        adresseNyVurdering = p6000.pensjon?.tilleggsinformasjon?.andreinstitusjoner?.map { adresse(it) },
                        vedtaksdato = p6000.pensjon?.tilleggsinformasjon?.dato,
                        retning = p6000.retning
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

    enum class SED_RETNING (val value: String) {
        SENT("SENT"),
        RECEIVED("RECEIVED"),
        NEW("NEW"),
        EMPTY("EMPTY");

        override fun toString(): String = value

        companion object {
            fun valueOf(value: String?): SED_RETNING {
                return entries.find { it.value.equals(value, ignoreCase = true) }
                    ?: EMPTY
            }
            fun norskSed()= setOf(NEW, SENT)
            fun utenlandskSed()= setOf(RECEIVED)
        }
    }

    class EmptyStringToNullDeserializer : JsonDeserializer<String?>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? {
            return p.valueAsString.takeIf { !it.isNullOrBlank() }
        }
    }

    /**
     * Hent PIN for bruker eller gjenlevende basert på SED-retning
     * @param brukerEllerGjenlevende enten FORSIKRET eller GJENLEVENDE
     * Henter norske pin fra norsk SED og utenlandske pin fra utenlandsk SED
     */
    private fun hentPin(brukerEllerGjenlevende: BrukerEllerGjenlevende, seds: List<P6000>): List<PinItem>? {
        return seds.flatMap { sed ->
            val person = hentBrukerEllerGjenlevende(brukerEllerGjenlevende, sed)
            when {
                sed.retning.isNorsk() -> person?.pin?.filter { it.land == "NO" }.orEmpty()
                else -> person?.pin?.filter { it.land != "NO" }.orEmpty()
            }
        }.distinct()
    }

    private fun String?.isNorsk(): Boolean {
        return this != null && SED_RETNING.valueOf(this.uppercase(getDefault())) in SED_RETNING.norskSed()
    }

}


