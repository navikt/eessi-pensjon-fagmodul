package no.nav.eessi.eessifagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.config.TimingService
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.pesys.mockup.MockSED001
import no.nav.eessi.eessifagmodul.services.LandkodeService
import no.nav.eessi.eessifagmodul.utils.getCounter
import no.nav.security.oidc.api.Protected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@CrossOrigin
@RestController
@RequestMapping("/pesys")
@Protected
//@ProtectedWithClaims(issuer = "pesys")

/**
 * tjeneste for opprettelse av automatiske krav ved mottakk av Buc/Krav fra utland.
 * Se PK-55797 , EESSIPEN-68
 */
class PensjonsinformasjonUtlandController(private val timingService: TimingService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonUtlandController::class.java) }

    private val mockSed = MockSED001()

    private val landkodeService = LandkodeService()

    //no.nav.eessi.eessifagmodul.pesys.PensjonsinformasjonMottakController
    companion object {
        @JvmStatic
        val mockmap = mutableMapOf<Int, KravUtland>()
    }

    fun hentAlpha3Land(land: String): String? {
        return landkodeService.finnLandkode3(land)
    }


    //TODO: vil trenge en innhentSedFraRinaService..
    //TODO: vil trenge en navSED->PESYS regel.

    fun hentKravUtlandFraMap(buckey: Int): KravUtland {
        logger.debug("prøver å hente ut KravUtland fra map med key: $buckey")
        return mockmap.getValue(buckey)
    }

    fun putKravUtlandMap(buckey: Int, kravUtland: KravUtland) {
        logger.debug("legger til kravUtland til map, hvis det ikke finnes fra før. med følgende key: $buckey")
        mockmap.putIfAbsent(buckey, kravUtland)
    }

    @ApiOperation(httpMethod = "PUT", value = "legger mock KravUtland til på map med bucid som key, KravUtland som verdi", response = KravUtland::class)
    @PutMapping("/mockPutKravUtland/{bucId}")
    fun mockPutKravUtland(@PathVariable("bucId", required = true) bucId: Int, @RequestBody kravUtland: KravUtland): KravUtland {
        if (bucId > 0 && bucId < 1000) {
            putKravUtlandMap(bucId, kravUtland)
            return hentKravUtland(bucId)
        }
        return KravUtland(errorMelding = "feil ved opprettelse av mock KravUtland, bucId må være mellom 1 og 999")
    }

    @ApiOperation(httpMethod = "DELETE", value = "sletter mock KravUtland fra map med buckid som key.", response = KravUtland::class)
    @DeleteMapping("/mockDeleteKravUtland/{bucId}")
    fun mockDeleteKravUtland(@PathVariable("bucId", required = true) buckId: Int) {
        try {
            mockmap.remove(buckId)
        } catch (ex: Exception) {
            logger.error(ex.message)
            throw ex
        }
    }

    @ApiOperation(httpMethod = "GET", value = "henter liste av keys fra mockMap med KravUtland", response = Set::class)
    @GetMapping("/mockHentKravUtlandKeys")
    fun mockGetKravUtlandKeys(): Set<Int> {
        return mockmap.keys
    }

    @ApiOperation(httpMethod = "GET", value = "Henter ut kravhode fra innkommende SEDER fra EU/EØS. Nødvendig data for å automatisk opprette et krav i Pesys", response = KravUtland::class)
    @GetMapping("/hentKravUtland/{bucId}")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    fun hentKravUtland(@PathVariable("bucId", required = true) bucId: Int): KravUtland {
        logger.debug("Starter prosess for henting av krav fra utloand (P2000...)")
        //henter ut maping til lokal variabel for enkel uthenting.

        val pesystime = timingService.timedStart("pesys_hentKravUtland")
        return if (bucId < 1000) {
            logger.debug("henter ut type fra mockMap<type, KravUtland> som legges inn i mockPutKravFraUtland(key, KravUtland alt under 1000)")
            timingService.timesStop(pesystime)
            hentKravUtlandFraMap(bucId)
        } else {
            logger.debug("henter ut type fra mock SED, p2000, p3000, p4000 og p5000 (alle kall fra type 1000..n.. er lik")

            val seds = mapSeds(bucId)
            //finner rette hjelep metode for utfylling av KravUtland
            //ut ifra hvilke SED/saktype det gjelder.
            if (erAlderpensjon(seds)) {
                logger.debug("type er alderpensjon")
                timingService.timesStop(pesystime)
                getCounter("HENTKRAVUTLANDOK").increment()
                kravAlderpensjonUtland(seds)

            } else if (erUforpensjon(seds)) {
                logger.debug("type er utføre")
                timingService.timesStop(pesystime)
                getCounter("HENTKRAVUTLANDOK").increment()
                kravUforepensjonUtland(seds)

            } else {
                logger.debug("type er gjenlevende")
                timingService.timesStop(pesystime)
                getCounter("HENTKRAVUTLANDOK").increment()
                kravGjenlevendeUtland(seds)
            }
        }

    }

    //funksjon for P2000
    fun kravAlderpensjonUtland(seds: Map<SEDType, SED>): KravUtland {

        val p2000 = getSED(SEDType.P2000, seds) ?: return KravUtland(errorMelding = "Ingen P2000 funnet")
        val p3000no = getSED(SEDType.P3000, seds) ?: return KravUtland(errorMelding = "Ingen P3000no funnet")
        logger.debug("oppretter KravUtland")

        //https://confluence.adeo.no/pages/viewpage.action?pageId=203178268
        //Kode om fra Alpha2 - Alpha3 teng i Avtaleland (eu, eøs og par andre)  og Statborgerskap (alle verdens land)
        //val statsborgerskapItem = p2000.nav?.bruker?.person?.statsborgerskap?.first()
        //statsborgerskap = hentAlpha3Land(p2000.nav?.bruker?.person?.statsborgerskap?.first()?.land ?: "N/A") ?: "N/A",
        var landAlpha3 = hentAlpha3Land(p2000.nav?.bruker?.person?.statsborgerskap?.first()?.land ?: "N/A")

        return KravUtland(
                //P2000 9.1
                mottattDato = LocalDate.parse(p2000.nav?.krav?.dato) ?: null,

                //P2000 ?? kravdatao?
                iverksettelsesdato = hentRettIverksettelsesdato(p2000),

                //P3000_NO 4.6.1. Forsikredes anmodede prosentdel av full pensjon
                uttaksgrad = parsePensjonsgrad(p3000no.pensjon?.landspesifikk?.norge?.alderspensjon?.pensjonsgrad),

                //P2000 2.2.1.1
                personopplysninger = SkjemaPersonopplysninger(
                        statsborgerskap = landAlpha3
                ),

                //P2000 - 2.2.2
                sivilstand = SkjemaFamilieforhold(
                        valgtSivilstatus = hentFamilieStatus("01"),
                        sivilstatusDatoFom = LocalDate.now()
                ),

                //P4000 - P5000 opphold utland (norge filtrert bort)
                utland = hentSkjemaUtland(seds),

                //denne må hentes utenfor SED finne orginal avsender-land for BUC/SED..
                soknadFraLand = hentAlpha3Land("SE"),
                //avtale mellom land? SED sendes kun fra EU/EØS? blir denne alltid true?
                vurdereTrygdeavtale = true,

                initiertAv = hentInitiertAv(p2000)
        )

    }

    //finnes verge ktp 7.1 og 7.2 settes VERGE hvis ikke BRUKER
    fun hentInitiertAv(p2000: SED): String {
        val vergeetter = p2000.nav?.verge?.person?.etternavn.orEmpty()
        val vergenavn = p2000.nav?.verge?.person?.fornavn.orEmpty()
        logger.debug("vergeetter: $vergeetter , vergenavn: $vergenavn")
        val verge = vergeetter + vergenavn
        logger.debug("verge: $verge")
        if (verge.length == 0) {
            return "BRUKER"
        }
        return "VERGE"
    }

    //iverksettelsesdato
    //p2000 9.4.1 - 9.4.4
    fun hentRettIverksettelsesdato(p2000: SED): LocalDate {
        val startDatoUtbet = p2000.pensjon?.forespurtstartdato
        return if (p2000.pensjon?.angitidligstdato == "1") {
            LocalDate.parse(startDatoUtbet)
        } else {
            val kravdato = LocalDate.parse(p2000.nav?.krav?.dato) ?: LocalDate.now()
            kravdato.withDayOfMonth(1).plusMonths(1)
        }
    }

    fun hentFamilieStatus(key: String): String {
        val status = mapOf<String, String>("01" to "UGIF", "02" to "GIFT", "03" to "SAMB", "04" to "REPA", "05" to "SKIL", "06" to "SKPA", "07" to "SEPA", "08" to "ENKE")
        //Sivilstand for søker. Må være en gyldig verdi fra T_K_SIVILSTATUS_T:
        //ENKE, GIFT, GJES, GJPA, GJSA, GLAD, PLAD, REPA,SAMB, SEPA, SEPR, SKIL, SKPA, UGIF.
        //Pkt p2000 - 2.2.2.1. Familiestatus
        //var valgtSivilstatus: String? = null,
        return status[key].orEmpty()
    }

    //P2200
    fun kravUforepensjonUtland(seds: Map<SEDType, SED>): KravUtland {
        return KravUtland()
    }

    //P2100
    fun kravGjenlevendeUtland(seds: Map<SEDType, SED>): KravUtland {
        return KravUtland()
    }


    fun hentSkjemaUtland(seds: Map<SEDType, SED>): SkjemaUtland {
        logger.debug("oppretter SkjemaUtland")
        var list = prosessUtlandsOpphold(seds)
        logger.debug("liste Utlandsoppholditem er størrelse : ${list.size}")
        return SkjemaUtland(
                utlandsopphold = list
        )
    }

    //P4000-P5000 logic
    fun prosessUtlandsOpphold(seds: Map<SEDType, SED>): List<Utlandsoppholditem> {

        val p4000 = getSED(SEDType.P4000, seds)
        val p5000 = getSED(SEDType.P5000, seds)

        val list = mutableListOf<Utlandsoppholditem>()
        logger.debug("oppretter utlandopphold P4000")
        list.addAll(hentUtlandsOppholdFraP4000(p4000))
        logger.debug("oppretter utlandopphold P5000")
        list.addAll(hentUtlandsOppholdFraP5000(p5000))

        return list
    }

    //oppretter UtlandsOpphold fra P4000 (uten Norge)
    fun hentUtlandsOppholdFraP4000(p4000: SED?): List<Utlandsoppholditem> {
        val list = mutableListOf<Utlandsoppholditem>()

        if (p4000 == null) {
            return listOf()
        }

        //P4000 -- arbeid
        val arbeidList = p4000.trygdetid?.ansattSelvstendigPerioder
        val filterArbeidUtenNorgeList = mutableListOf<AnsattSelvstendigItem>()
        arbeidList?.forEach {
            if ("NO" != it.adresseFirma?.land) {
                filterArbeidUtenNorgeList.add(it)
            }
        }


        filterArbeidUtenNorgeList.forEach {
            val arbeid = it

            val landAlpha2 = arbeid.adresseFirma?.land ?: "N/A"
            val landAlpha3 = hentAlpha3Land(landAlpha2) ?: ""


            val periode = hentFomEllerTomFraPeriode(arbeid.periode)
            var fom: LocalDate? = null
            var tom: LocalDate? = null

            try {
                fom = LocalDate.parse(periode.fom)
            } catch (ex: Exception) {
                logger.error(ex.message)
            }
            try {
                tom = LocalDate.parse(periode.tom)
            } catch (ex: Exception) {
                logger.error(ex.message)
            }
            val land = landAlpha3

            logger.debug("oppretter arbeid P4000")
            list.add(
                    Utlandsoppholditem(
                            land = land,
                            fom = fom,
                            tom = tom,
                            arbeidet = true,
                            bodd = false,
                            utlandPin = hentPinIdFraBoArbeidLand(p4000, landAlpha2),
                            //kommer ut ifa avsenderLand (hvor orginal type kommer ifra)
                            pensjonsordning = hentPensjonsOrdning(p4000, landAlpha2)
                    )
            )

        }

        //P4000 - bo
        val boList = p4000.trygdetid?.boPerioder
        val filterBoUtenNorgeList = mutableListOf<StandardItem>()
        boList?.forEach {
            if ("NO" != it.land) {
                filterBoUtenNorgeList.add(it)
            }
        }
        filterBoUtenNorgeList.forEach {
            val bo = it

            val landA2 = bo.land ?: "N/A"
            val landAlpha3 = hentAlpha3Land(bo.land ?: "N/A") ?: ""

            val periode = hentFomEllerTomFraPeriode(bo.periode)
            val land = bo.land ?: ""
            logger.debug("oppretter bo P4000")
            var fom: LocalDate? = null
            var tom: LocalDate? = null

            try {
                fom = LocalDate.parse(periode.fom)
            } catch (ex: Exception) {
                logger.error(ex.message)
            }
            try {
                tom = LocalDate.parse(periode.tom)
            } catch (ex: Exception) {
                logger.error(ex.message)
            }

            list.add(
                    Utlandsoppholditem(
                            land = landAlpha3,
                            fom = fom,
                            tom = tom,
                            arbeidet = false,
                            bodd = true,
                            utlandPin = hentPinIdFraBoArbeidLand(p4000, landA2),
                            pensjonsordning = hentPensjonsOrdning(p4000, landA2) // "Hva?"
                    )
            )
        }
        return list
    }


    fun hentFomEllerTomFraPeriode(openLukketPeriode: TrygdeTidPeriode?): Periode {
        //var periode: Periode? = null
        val open = openLukketPeriode?.openPeriode
        val lukket = openLukketPeriode?.lukketPeriode

        if (open?.fom != null && lukket?.tom == null) {
            return open
        } else if (open?.fom == null && lukket?.tom != null) {
            return lukket
        }
        return Periode()
    }

    fun hentPensjonsOrdning(psed: SED, land: String): String {
        //prøvr å hente ut sektor (ytelse/pensjonordning)
        psed.nav?.bruker?.person?.pin?.forEach {
            if (land == it.land) {
                return it.institusjon?.institusjonsnavn ?: ""
            }
        }
        return ""

    }

    fun hentPinIdFraBoArbeidLand(psed: SED, land: String): String {
        //p2000.nav?.bruker?.person?.pin?.get(0)?.land
        //p2000 eller p4000?
        psed.nav?.bruker?.person?.pin?.forEach {
            if (land == it.land) {
                return it.identifikator ?: ""
            }
        }
        return ""
    }

    //oppretter UtlandsOpphold fra P5000 (trygdeland)
    fun hentUtlandsOppholdFraP5000(p5000: SED?): List<Utlandsoppholditem> {
        val list = mutableListOf<Utlandsoppholditem>()
        //P5000
        val trygdetidList = p5000?.pensjon?.trygdetid

        trygdetidList?.forEach {
            var fom: LocalDate? = null
            var tom: LocalDate? = null
            try {
                fom = LocalDate.parse(it.periode?.fom)
            } catch (ex: Exception) {
                logger.error(ex.message)
            }
            try {
                tom = LocalDate.parse(it.periode?.tom)
            } catch (ex: Exception) {
                logger.error(ex.message)
            }

            val pin = hentPinIdFraBoArbeidLand(p5000, it.land ?: "N/A")

            list.add(Utlandsoppholditem(
                    land = hentAlpha3Land(it.land ?: "N/A"),
                    fom = fom,
                    tom = tom,
                    bodd = true,
                    arbeidet = false,
                    pensjonsordning = "???",
                    utlandPin = pin
            ))
        }

        return list
    }

    private fun getSED(sedType: SEDType, maps: Map<SEDType, SED>): SED? {
        return maps.get(sedType)
    }

    //finne ut som type er for P2000
    private fun erAlderpensjon(maps: Map<SEDType, SED>): Boolean {
        return getSED(SEDType.P2000, maps) != null
    }

    //finne ut som type er for P2200
    private fun erUforpensjon(maps: Map<SEDType, SED>): Boolean {
        return getSED(SEDType.P2200, maps) != null
    }


    //henter de nødvendige SEDer fra Rina, legger de på maps med bucId som Key.
    private fun mapSeds(bucId: Int): Map<SEDType, SED> {
        logger.debug("Henter ut alle nødvendige SED for lettere utfylle tjenesten")
        val map = mapOf<SEDType, SED>(SEDType.P2000 to fetchDocument(bucId, SEDType.P2000),
                SEDType.P3000 to fetchDocument(bucId, SEDType.P3000),
                SEDType.P4000 to fetchDocument(bucId, SEDType.P4000))
        val keys = map.keys
        return map
    }

    //Henter inn valgt sedType fra Rina og returerer denne
    //returnerer generell ERROR sed hvis feil!
    fun fetchDocument(buc: Int, sedType: SEDType): SED {

        when (buc) {
            1050 -> {
                logger.debug("henter ut SED data for type: $buc og sedType: $sedType")
                return when (sedType) {
                    SEDType.P2000 -> mockSed.mockP2000()
                    SEDType.P3000 -> {
                        val p3000 = mockSed.mockP3000NO()
                        p3000.pensjon?.landspesifikk?.norge?.alderspensjon?.pensjonsgrad = null
                        p3000
                    }
                    SEDType.P4000 -> mockSed.mockP4000()
                    else -> SED("ERROR")
                }
            }
            else -> {
                logger.debug("henter ut SED data for type: $buc og sedType: $sedType")
                return when (sedType) {
                    SEDType.P2000 -> mockSed.mockP2000()
                    SEDType.P3000 -> mockSed.mockP3000NO()
                    SEDType.P4000 -> mockSed.mockP4000()
                    else -> SED("ERROR")
                }
            }
        }
    }


    //pensjon utatksgrad mapping fra P3000 til pesys verdi.
    fun parsePensjonsgrad(pensjonsgrad: String?): String? {
        return when (pensjonsgrad) {
            "01" -> "20"
            "02" -> "40"
            "03" -> "50"
            "04" -> "60"
            "05" -> "80"
            "06" -> "100"
            else -> null
        }
    }


}