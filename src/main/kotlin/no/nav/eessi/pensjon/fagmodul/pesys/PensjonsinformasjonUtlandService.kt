package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.pesys.RinaTilPenMapper.parsePensjonsgrad
import no.nav.eessi.pensjon.fagmodul.pesys.mockup.MockSED001
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.services.kodeverk.KodeverkService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

//TODO bytt ut Mocks med ekte kode
@Service
class PensjonsinformasjonUtlandService(private val kodeverkService: KodeverkService) {

    private val mockSed = MockSED001()

    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonUtlandService::class.java) }

    companion object {
        @JvmStatic
        val mockmap = mutableMapOf<Int, KravUtland>()
    }

    fun mockDeleteKravUtland(buckId: Int) {
        try {
            mockmap.remove(buckId)
        } catch (ex: Exception) {
            logger.error(ex.message)
            throw ex
        }
    }

    fun hentKravUtland(bucId: Int): KravUtland {
        logger.debug("Starter prosess for henting av krav fra utloand (P2000...)")
        //henter ut maping til lokal variabel for enkel uthenting.0

        return if (bucId < 1000) {
            logger.debug("henter ut type fra mockMap<type, KravUtland> som legges inn i mockPutKravFraUtland(key, KravUtland alt under 1000)")
            hentKravUtlandFraMap(bucId)
        } else {
            logger.debug("henter ut type fra mock SED, p2000, p3000, p4000 og p5000 (alle kall fra type 1000..n.. er lik")

            val seds = mapSeds(bucId)
            //finner rette hjelep metode for utfylling av KravUtland
            //ut ifra hvilke SED/saktype det gjelder.
            when {
                erAlderpensjon(seds) -> {
                    logger.debug("type er alderpensjon")
                    kravAlderpensjonUtland(seds)

                }
                erUforpensjon(seds) -> {
                    logger.debug("type er utføre")
                    kravUforepensjonUtland(seds)

                }
                else -> {
                    logger.debug("type er gjenlevende")
                    kravGjenlevendeUtland(seds)
                }
            }
        }
    }

    fun mockGetKravUtlandKeys(): Set<Int> {
        return mockmap.keys
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

    //funksjon for P2000
    fun kravAlderpensjonUtland(seds: Map<SEDType, SED>): KravUtland {

        val p2000 = getSED(SEDType.P2000, seds) ?: return KravUtland(errorMelding = "Ingen P2000 funnet")
        val p3000no = getSED(SEDType.P3000, seds) ?: return KravUtland(errorMelding = "Ingen P3000no funnet")
        logger.debug("oppretter KravUtland")

        //https://confluence.adeo.no/pages/viewpage.action?pageId=203178268
        //Kode om fra Alpha2 - Alpha3 teng i Avtaleland (eu, eøs og par andre)  og Statborgerskap (alle verdens land)
//        val landAlpha3 = landkodeService.finnLandkode3(p2000.nav?.bruker?.person?.statsborgerskap?.first()?.land ?: "N/A")
        val landAlpha3 = kodeverkService.finnLandkode3(p2000.nav?.bruker?.person?.statsborgerskap?.first()?.land ?: "N/A")

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
                soknadFraLand = kodeverkService.finnLandkode3("SE"),
                //avtale mellom land? SED sendes kun fra EU/EØS? blir denne alltid true?
                vurdereTrygdeavtale = true,

                initiertAv = hentInitiertAv(p2000)
        )
    }

    fun finnLandkode3(p2000: SED): String? {
        return kodeverkKlient.finnLandkode3(p2000.nav?.bruker?.person?.statsborgerskap?.first()?.land ?: "N/A")
    }

    //finnes verge ktp 7.1 og 7.2 settes VERGE hvis ikke BRUKER
    fun hentInitiertAv(p2000: SED): String {
        val vergeetter = p2000.nav?.verge?.person?.etternavn.orEmpty()
        val vergenavn = p2000.nav?.verge?.person?.fornavn.orEmpty()
        logger.debug("vergeetter: $vergeetter , vergenavn: $vergenavn")
        val verge = vergeetter + vergenavn
        logger.debug("verge: $verge")
        if (verge.isEmpty()) {
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
        val status = mapOf("01" to "UGIF", "02" to "GIFT", "03" to "SAMB", "04" to "REPA", "05" to "SKIL", "06" to "SKPA", "07" to "SEPA", "08" to "ENKE")
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
        val list = prosessUtlandsOpphold(seds)
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
        arbeidList?.

                forEach {
            if ("NO" != it.adresseFirma?.land) {
                filterArbeidUtenNorgeList.add(it)
            }
        }

        filterArbeidUtenNorgeList.forEach {
            val arbeid = it

            val landAlpha2 = arbeid.adresseFirma?.land ?: "N/A"
            val landAlpha3 = kodeverkService.finnLandkode3(landAlpha2) ?: ""

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

            logger.debug("oppretter arbeid P4000")
            list.add(
                    Utlandsoppholditem(
                            land = landAlpha3,
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
            val landAlpha3 = kodeverkService.finnLandkode3(bo.land ?: "N/A") ?: ""

            val periode = hentFomEllerTomFraPeriode(bo.periode)
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
                    land = kodeverkService.finnLandkode3(it.land ?: "N/A"),
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

    private fun getSED(sedType: SEDType, maps: Map<SEDType, SED>) = maps[sedType]

    //finne ut som type er for P2000
    fun erAlderpensjon(maps: Map<SEDType, SED>) = getSED(SEDType.P2000, maps) != null

    //finne ut som type er for P2200
    fun erUforpensjon(maps: Map<SEDType, SED>) = getSED(SEDType.P2200, maps) != null

    //henter de nødvendige SEDer fra Rina, legger de på maps med bucId som Key.
    fun mapSeds(bucId: Int): Map<SEDType, SED> {
        logger.debug("Henter ut alle nødvendige SED for lettere utfylle tjenesten")
        return mapOf(SEDType.P2000 to fetchDocument(bucId, SEDType.P2000),
                SEDType.P3000 to fetchDocument(bucId, SEDType.P3000),
                SEDType.P4000 to fetchDocument(bucId, SEDType.P4000))
    }

    //Henter inn valgt sedType fra Rina og returerer denne
    //returnerer generell ERROR sed hvis feil!
    fun fetchDocument(buc: Int, sedType: SEDType): SED {

        when (buc) {
            1050 -> {
                logger.debug("henter ut SED data for type: $buc og sedType: $sedType")
                return when (sedType) {
                    SEDType.P2000 -> mockSed.mockP2000()
                    SEDType.P3000 -> mockSed.mockP3000NO()
                    SEDType.P4000 -> mockSed.mockP4000()
                    else -> SED("ERROR")
                }
            }
            else -> {
                logger.debug("henter ut SED data for type: $buc og sedType: $sedType")
                return when (sedType) {
                    SEDType.P2000 -> mockSed.mockP2000()
                    SEDType.P3000 -> mockSed.mockP3000NO("03")
                    SEDType.P4000 -> mockSed.mockP4000()
                    else -> SED("ERROR")
                }
            }
        }
    }
}