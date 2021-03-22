package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Service
class PensjonsinformasjonUtlandService(
    private val kodeverkClient: KodeverkClient,
    private val euxService: EuxService,
    @Value("\${NAIS_NAMESPACE}")
    private val nameSpace: String) {

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonUtlandService::class.java)

    private final val validBuc = listOf("P_BUC_01","P_BUC_02","P_BUC_03")
    private final val kravSedBucmap = mapOf("P_BUC_01" to SEDType.P2000, "P_BUC_02" to SEDType.P2100, "P_BUC_03" to SEDType.P2200)

    /**
     * funksjon for å hente buc-metadata fra RINA (eux-rina-api)
     * lese inn KRAV-SED P2xxx for så å plukke ut nødvendige data for så
     * returnere en KravUtland model
     */
    fun hentKravUtland(bucId: Int): KravUtland {
        logger.debug("Starter prosess for henting av krav fra utland (P2000, P2100?, P2200)")

        //fra srvpensjon --> til --> srveessipensjon --> for kall til EUX-RINA-API -> RINA

        //bucUtils
        val buc = euxService.getBuc(bucId.toString())
        val bucUtils = BucUtils(buc)

        logger.debug("BucType : ${bucUtils.getProcessDefinitionName()}")
        logger.debug("Funnet KravTypeSED i buc: ${kravSedBucmap[bucUtils.getProcessDefinitionName()]}")

        if (!validBuc.contains(bucUtils.getProcessDefinitionName())) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ulydig BUC, ikke av rett type KRAV-om BUC.")
        if (bucUtils.getCaseOwner() == null) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen CaseOwner funnet på BUC med id: $bucId")

        val sedDoc = getKravSedDocument(bucUtils, kravSedBucmap[bucUtils.getProcessDefinitionName()]) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingen dokument metadata funnet i BUC med id: $bucId.")
        val kravSed = sedDoc.id?.let { sedDocId -> euxService.getSedOnBucByDocumentId(bucId.toString(), sedDocId) } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen gyldig kravSed i BUC med id: $bucId funnet.")

        //finner rette hjelep metode for utfylling av KravUtland
        //ut ifra hvilke SED/saktype det gjelder.
        logger.info("*** Starter kravUtlandpensjon: ${kravSed.type} bucId: $bucId bucType: ${bucUtils.getProcessDefinitionName()} ***")

        return if(erAlderpensjon(kravSed)) {
            logger.debug("type er alderpensjon")
            kravAlderpensjonUtland(kravSed, bucUtils)
        } else {
            logger.debug("type er uførepensjon")
            kravUforepensjonUtland(kravSed, bucUtils)
        }
    }

    fun getKravSedDocument(bucUtils: BucUtils, sedType: SEDType?) = bucUtils.getAllDocuments().firstOrNull { it.status == "received" && it.type == sedType }
    fun erAlderpensjon(sed: SED) = sed.type == SEDType.P2000
    fun erUforepensjon(sed: SED) = sed.type == SEDType.P2200


    fun finnStatsborgerskapsLandkode3(kravSed: SED): String? {
        val statsborgerskap = kravSed.nav?.bruker?.person?.statsborgerskap?.firstOrNull { it.land != null }
        return statsborgerskap?.let { kodeverkClient.finnLandkode3(it.land!!) } ?: ""
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
        val kravdato = LocalDate.parse(p2000.nav?.krav?.dato) ?: LocalDate.now()
        return kravdato.withDayOfMonth(1).plusMonths(1)
    }

    //TODO: vil trenge en innhentSedFraRinaService..
    //TODO: vil trenge en navSED->PESYS regel.
    //funksjon for P2000
    fun kravAlderpensjonUtland(kravSed: SED, bucUtils: BucUtils): KravUtland {

        val p2000 = kravSed
        logger.debug("oppretter KravUtland")

        //https://confluence.adeo.no/pages/viewpage.action?pageId=203178268
        //Kode om fra Alpha2 - Alpha3 teng i Avtaleland (eu, eøs og par andre)  og Statborgerskap (alle verdens land)
        val landAlpha3 = finnStatsborgerskapsLandkode3(p2000)

        return KravUtland(
            //P2000 9.1
            mottattDato = LocalDate.parse(p2000.nav?.krav?.dato) ?: null,

            //P2000 ?? kravdatao?
            iverksettelsesdato = hentRettIverksettelsesdato(p2000),

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
            utland = hentSkjemaUtland(null),

            //denne må hentes utenfor SED finne orginal avsender-land for BUC/SED..
            soknadFraLand = kodeverkClient.finnLandkode3("SE"),
            //avtale mellom land? SED sendes kun fra EU/EØS? blir denne alltid true?
            vurdereTrygdeavtale = true,

            initiertAv = hentInitiertAv(p2000)
        )
    }

    fun hentFamilieStatus(key: String?): String {
        val status = mapOf("01" to "UGIF", "02" to "GIFT", "03" to "SAMB", "04" to "REPA", "05" to "SKIL", "06" to "SKPA", "07" to "SEPA", "08" to "ENKE")
        //Sivilstand for søker. Må være en gyldig verdi fra T_K_SIVILSTATUS_T:
        //ENKE, GIFT, GJES, GJPA, GJSA, GLAD, PLAD, REPA,SAMB, SEPA, SEPR, SKIL, SKPA, UGIF.
        //Pkt p2000 - 2.2.2.1. Familiestatus
        //var valgtSivilstatus: String? = null,
        return status[key].orEmpty()
    }

    //P2200
    fun kravUforepensjonUtland(kravSed: SED, bucUtils: BucUtils): KravUtland {

        val caseOwner = bucUtils.getCaseOwner()!!
        val caseOwnerCountryBuc = if (nameSpace == "q2" || nameSpace == "test") {
            "SE"
        } else {
            caseOwner.country
        }
        val caseOwnerCountry = kodeverkClient.finnLandkode3( caseOwnerCountryBuc )
        logger.debug("CaseOwnerCountry: $caseOwnerCountry")
        logger.debug("CaseOwnerId     : ${caseOwner.institution}")
        logger.debug("CaseOwnerName   : ${caseOwner.name}")

        val sivilstand = kravSed.nav?.bruker?.person?.sivilstand?.maxByOrNull { LocalDate.parse(it.fradato) }
        logger.debug("Sivilstand: $sivilstand")
        val sivilstatus = hentFamilieStatus( sivilstand?.status ?: "01" )


        return KravUtland(
            mottattDato = LocalDate.parse(kravSed.nav?.krav?.dato) ?: null,
            iverksettelsesdato = LocalDate.parse(kravSed.nav?.krav?.dato) ?: null,
            fremsattKravdato = fremsettKravDato(kravSed),

            uttaksgrad = "0", //P3000_NO //4.6.1 - uttaksgrad alderpensjon
            vurdereTrygdeavtale = false,

            personopplysninger = SkjemaPersonopplysninger(
                statsborgerskap = finnStatsborgerskapsLandkode3(kravSed)
            ),

            //P4000-P5000
            utland = SkjemaUtland(
                utlandsopphold = emptyList()
            ),
            sivilstand = SkjemaFamilieforhold(
               valgtSivilstatus = sivilstatus,
               sivilstatusDatoFom = sivilstand?.fradato?.let { LocalDate.parse(it) }
            ),
            soknadFraLand = caseOwnerCountry
        )
    }


    fun fremsettKravDato(kravSed: SED): LocalDate {
       val kravdato = LocalDate.parse(kravSed.nav?.krav?.dato) ?: LocalDate.now()
       return kravdato.withDayOfMonth(1).minusMonths(3)
    }

    //P2100
    fun kravGjenlevendeUtland(seds: Map<SEDType, SED>): KravUtland {
        return KravUtland()
    }


    fun hentSkjemaUtland(seds: SED? = null ): SkjemaUtland {
        logger.debug("oppretter SkjemaUtland")
        val list = prosessUtlandsOpphold(null)
        logger.debug("liste Utlandsoppholditem er størrelse : ${list.size}")
        return SkjemaUtland(
                utlandsopphold = list
        )
    }

    //P4000-P5000 logic
    fun prosessUtlandsOpphold(seds: Map<SEDType, SED>? = null): List<Utlandsoppholditem> {

        val p4000: P4000? = null
        val p5000: P5000? = null

        val list = mutableListOf<Utlandsoppholditem>()
        logger.debug("oppretter utlandopphold P4000")
        list.addAll(hentUtlandsOppholdFraP4000(p4000))
        logger.debug("oppretter utlandopphold P5000")
        list.addAll(hentUtlandsOppholdFraP5000(p5000))

        return list
    }

    //oppretter UtlandsOpphold fra P4000 (uten Norge)
    fun hentUtlandsOppholdFraP4000(p4000: P4000?): List<Utlandsoppholditem> {
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
            val landAlpha3 = kodeverkClient.finnLandkode3(landAlpha2) ?: ""

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
            val landAlpha3 = kodeverkClient.finnLandkode3(bo.land ?: "N/A") ?: ""

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
    fun hentUtlandsOppholdFraP5000(p5000: P5000?): List<Utlandsoppholditem> {
        val list = mutableListOf<Utlandsoppholditem>()
        //P5000
        val trygdetidList = p5000?.p5000Pensjon?.trygdetid

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
                    land = kodeverkClient.finnLandkode3(it.land ?: "N/A"),
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

}