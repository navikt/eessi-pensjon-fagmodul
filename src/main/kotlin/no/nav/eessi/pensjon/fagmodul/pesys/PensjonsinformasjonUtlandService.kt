package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.sed.AndreinstitusjonerItem
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandService.BrukerEllerGjenlevende.*
import no.nav.eessi.pensjon.fagmodul.pesys.krav.AlderpensjonUtlandKrav
import no.nav.eessi.pensjon.fagmodul.pesys.krav.AvslaattPensjon
import no.nav.eessi.pensjon.fagmodul.pesys.krav.EessisakItemP1
import no.nav.eessi.pensjon.fagmodul.pesys.krav.InnvilgetPensjon
import no.nav.eessi.pensjon.fagmodul.pesys.krav.P1Person
import no.nav.eessi.pensjon.fagmodul.pesys.krav.UforeUtlandKrav
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import kotlin.collections.orEmpty

@Service
class PensjonsinformasjonUtlandService(
    private val alderpensjonUtlandsKrav: AlderpensjonUtlandKrav,
    private val uforeUtlandKrav: UforeUtlandKrav,
    private val euxInnhentingService: EuxInnhentingService,
    private val kodeverkClient: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonUtlandService::class.java)
    private val liste = mutableListOf<Pair<String, String>>()
    private val secureLog = LoggerFactory.getLogger("secureLog")

    private final val validBuc = listOf("P_BUC_01", "P_BUC_03")
    private final val kravSedBucmap = mapOf("P_BUC_01" to P2000, "P_BUC_03" to P2200)

    /**
     * funksjon for å hente buc-metadata fra RINA (eux-rina-api)
     * lese inn KRAV-SED P2xxx for så å plukke ut nødvendige data for så
     * returnere en KravUtland model
     */
    fun hentKravUtland(bucId: Int): KravUtland? {
        logger.info("** innhenting av kravdata for buc: $bucId **")

        val buc = euxInnhentingService.getBucAsSystemuser(bucId.toString())
        if(buc == null){
            logger.error("Buc: $bucId kan ikke hentes og vi er ikke i stand til å hente krav")
            return null
        }
        val bucUtils = BucUtils(buc)

        logger.debug("Starter prosess for henting av krav fra utland (P2000, P2200)")

        if (!validBuc.contains(bucUtils.getProcessDefinitionName())) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ugyldig BUC, Ikke korrekt type KRAV.")
        if (bucUtils.getCaseOwner() == null) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen CaseOwner funnet på BUC med id: $bucId").also { logger.error(it.message) }

        val sedDoc = getKravSedDocument(bucUtils, kravSedBucmap[bucUtils.getProcessDefinitionName()])
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingen dokument metadata funnet i BUC med id: $bucId.").also { logger.error(it.message) }

        val kravSed = sedDoc.id?.let { sedDocId -> euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(bucId.toString(), sedDocId) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen gyldig kravSed i BUC med id: $bucId funnet.").also { logger.error(it.message) }

        // finner rette korrekt metode for utfylling av KravUtland ut ifra hvilke SED/saktype det gjelder.
        logger.info("*** Starter kravUtlandpensjon: ${kravSed.type} bucId: $bucId bucType: ${bucUtils.getProcessDefinitionName()} ***")

        return when {
            erAlderpensjon(kravSed) -> {
                logger.debug("Kravtype er alderpensjon")
                alderpensjonUtlandsKrav.kravAlderpensjonUtland(kravSed, bucUtils, sedDoc).also {
                    debugPrintout(it)
                }
            }
            erUforepensjon(kravSed) -> {
                logger.debug("Kravtype er uførepensjon")
                uforeUtlandKrav.kravUforepensjonUtland(kravSed, bucUtils, sedDoc).also {
                    debugPrintout(it)
                }
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ikke støttet request")
        }

    }

    fun finnSaksnummer(p6000: P6000): String? {
        val saksnummerFraEessisak = p6000.nav?.eessisak?.firstOrNull()?.saksnummer
        val saksnummerFraTilleggsInformasjon = p6000.pensjon?.tilleggsinformasjon?.saksnummer
        return saksnummerFraEessisak ?: saksnummerFraTilleggsInformasjon

    }

    fun eessiInstitusjoner(p6000: P6000): EessisakItemP1 = EessisakItemP1 (
        institusjonsid = p6000.avsender?.id,
        institusjonsnavn = p6000.avsender?.navn,
        saksnummer = finnSaksnummer(p6000),
        land = p6000.avsender?.land,
        identifikatorForsikrede = hentPin(FORSIKRET, p6000)?.firstOrNull()?.identifikator,
        identifikatorInnehaver = hentPin(GJENLEVENDE, p6000)?.firstOrNull()?.identifikator,
    )

    fun innvilgedePensjoner(p6000er: List<P6000>) : List<InnvilgetPensjon>{
        val flereEnnEnNorsk =  erDetFlereNorskeInstitusjoner(p6000er)
        val retList = mutableListOf<InnvilgetPensjon>()

        hentInnvilgedePensjonerFraP6000er(p6000er).map { p6000 ->
            val vedtak = p6000.pensjon?.vedtak?.first()

            if (p6000.avsender?.land.isNorsk() && flereEnnEnNorsk && retList.count { it.avsender?.land.isNorsk() } >= 1) {
                logger.error(" OBS OBS; Her kommer det inn mer enn 1 innvilget pensjon fra Norge")
                secureLog.info("Hopper over innvilget pensjon P6000: $p6000")
            } else {
                logger.info("Legger til innvilget pensjon fra land: ${p6000.avsender?.land}")
                retList.add(
                    InnvilgetPensjon(
                        institusjon = listOf(eessiInstitusjoner(p6000)),
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
                        avsender = p6000.avsender
                    )
                )
            }
        }
        return retList
    }

    fun sjekkPaaGyldigeInnvElAvslPensjoner(
        innvilgedePensjoner: List<InnvilgetPensjon>,
        avslaatteUtenlandskePensjoner: List<AvslaattPensjon>,
        listeOverP6000FraGcp: MutableList<P6000>,
        pesysId: String
    ) {
        if (innvilgedePensjoner.isEmpty() && avslaatteUtenlandskePensjoner.isEmpty()) {
            logger.error("Ingen gyldige pensjoner funnet i P6000er for pesysId: $pesysId")
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ingen gyldige pensjoner funnet i P6000er for pesysId: $pesysId"
            )
        }
        if (innvilgedePensjoner.size + avslaatteUtenlandskePensjoner.size != listeOverP6000FraGcp.size) {
            logger.warn("Mismatch: innvilgedePensjoner (${innvilgedePensjoner.size}) + avslåtteUtenlandskePensjoner (${avslaatteUtenlandskePensjoner.size}) != utenlandskeP6000er (${listeOverP6000FraGcp.size})")
        }
    }

    private fun hentInnvilgedePensjonerFraP6000er(p6000er: List<P6000>): List<P6000> = p6000er.filter { sed ->
        sed.pensjon?.vedtak?.any { it.resultat in listOf("01", "03", "04")
        } == true || sed.pensjon?.vedtak?.any { it.beregning?.any { it.beloepBrutto != null } == true } == true
    }.sortedByDescending { it.nav?.eessisak?.isNotEmpty() == true }

    private fun erDetFlereNorskeInstitusjoner(p6000er: List<P6000>): Boolean {
        return p6000er.count { it.avsender?.land.isNorsk() }.let { antallUt ->
            if (antallUt > 1) {
                logger.error("OBS OBS; Her kommer det inn mer enn 1 P6000 med retning UT i Seden")
                return true
            }
            false
        }
    }

    fun String?.isNorsk(): Boolean = this != null && this == "NO"
    fun String?.isUtenlandsk(): Boolean = this != null && this != "NO"

    fun debugPrintout(kravUtland: KravUtland) {
        logger.info(
            """Følgende krav utland returneres:
            ${kravUtland.toJson()}
            """.trimIndent()
        )
    }

    fun avslaatteUtenlandskePensjoner(p6000er: List<P6000>): List<AvslaattPensjon> {
        val p6000erAvslaatt = p6000er.filter { sed -> sed.pensjon?.vedtak?.any { it.resultat == "02" } == true}
            .sortedByDescending { it.pensjon?.tilleggsinformasjon?.dato }


        val flereEnnEnNorsk = erDetFlereNorskeInstitusjoner(p6000erAvslaatt)
        val retList = mutableListOf<AvslaattPensjon>()
        p6000erAvslaatt.map { p6000 ->
            if (p6000.avsender?.land.isNorsk() && flereEnnEnNorsk && retList.count { it.avsender?.land.isNorsk()  } >= 1) {
                logger.error(" OBS OBS; Her kommer det inn mer enn 1 avslått pensjon fra Norge")
                secureLog.info("Hopper over denne avslåtte seden: $p6000")
            } else {
                logger.info("Legger til avslått pensjon fra sed med avsender?.land: ${p6000.avsender?.land}")
                val pensjon = p6000.pensjon
                retList.add(
                    AvslaattPensjon(
                        institusjon = listOf(eessiInstitusjoner(p6000)),
                        pensjonstype = pensjon?.vedtak?.first()?.type,
                        avslagsbegrunnelse = pensjon?.vedtak?.first()?.avslagbegrunnelse?.first { !it.begrunnelse.isNullOrEmpty() }?.begrunnelse,
                        vurderingsperiode = pensjon?.sak?.kravtype?.first()?.datoFrist,
                        adresseNyVurdering = pensjon?.tilleggsinformasjon?.andreinstitusjoner?.map { adresse(it) },
                        vedtaksdato = pensjon?.tilleggsinformasjon?.dato,
                        avsender = p6000.avsender
                    )
                )
            }
        }
        return retList
    }

    fun person(sed: P6000, brukerEllerGjenlevende: BrukerEllerGjenlevende) : P1Person {
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
            landkode = personBruker.second?.land
        )
    }

    fun adresse(p6000: AndreinstitusjonerItem) =
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

    /**
     * Hent PIN for bruker eller gjenlevende basert på land i SED
     * @param brukerEllerGjenlevende enten FORSIKRET eller GJENLEVENDE
     * Henter norske pin fra norsk SED og utenlandske pin fra utenlandsk SED
     */
    fun hentPin(brukerEllerGjenlevende: BrukerEllerGjenlevende, sed: P6000): List<PinItem>? {
        val person = hentBrukerEllerGjenlevende(brukerEllerGjenlevende, sed)
        return when {
            sed.avsender?.land.isNorsk() -> person?.pin?.filter { it.land == "NO" }.orEmpty()
            else -> person?.pin?.filter { it.land != "NO"  && it.land == sed.avsender?.land}.orEmpty()
        }
    }

    fun nyesteP6000(listeOverP6000FraGcp: MutableList<P6000>): List<P6000> = listeOverP6000FraGcp.sortedWith(
        compareBy(
            { it.pensjon?.tilleggsinformasjon?.dato },
            { it.pensjon?.vedtak?.firstOrNull()?.virkningsdato }
        )
    ).reversed()

    private fun hentBrukerEllerGjenlevende(
        brukerEllerGjenlevende: BrukerEllerGjenlevende,
        sed: P6000
    ): Person? = if (brukerEllerGjenlevende == FORSIKRET)
        sed.nav?.bruker?.person
    else
        sed.pensjon?.gjenlevende?.person

    fun dato(foedselsdato: String?): LocalDate? {
        return try {
            foedselsdato?.let { LocalDate.parse(it) }
        } catch (ex: Exception) {
            null
        }
    }

    fun getKravSedDocument(bucUtils: BucUtils, SedType: SedType?) =
        bucUtils.getAllDocuments().firstOrNull { it.status == "received" && it.type == SedType }

    fun erAlderpensjon(sed: SED) = sed.type == P2000

    fun erUforepensjon(sed: SED) = sed.type == P2200

    enum class BrukerEllerGjenlevende(val person: String) {
        FORSIKRET ("forsikret"),
        GJENLEVENDE ("gjenlevende")
    }


}