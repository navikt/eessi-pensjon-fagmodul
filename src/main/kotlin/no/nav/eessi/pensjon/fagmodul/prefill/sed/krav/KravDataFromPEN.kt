package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.sedmodel.BeloepItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Institusjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.YtelserItem
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.ytelsepermaaned.V1YtelsePerMaaned
import no.nav.pensjon.v1.ytelseskomponent.V1Ytelseskomponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Hjelpe klasse for sak som fyller ut NAV-SED-P2000 med pensjondata fra PESYS.
 */
open class KravDataFromPEN(private val dataFromPEN: PensjonsinformasjonHjelper) : Prefill<Pensjon> {
    private val logger: Logger by lazy { LoggerFactory.getLogger(KravDataFromPEN::class.java) }

    //gyldige kravhistorikk status og typer.
    private val TIL_BEHANDLING = "TIL_BEHANDLING"
    private val F_BH_MED_UTL = "F_BH_MED_UTL"
    private val FORSTEG_BH = "FORSTEG_BH"

    val REVURD = "REVURD"


    //K_SAK_T Kodeverk fra PESYS
    enum class KSAK {
        ALDER,
        UFOREP,
        GJENLEV,
        BARNEP;

        companion object {
            @JvmStatic
            fun isValid(input: String): Boolean {
                return try {
                    valueOf(input)
                    true
                } catch (ia: IllegalArgumentException) {
                    false
                }
            }
        }
    }

    override fun prefill(prefillData: PrefillDataModel): Pensjon {
        val pendata: Pensjonsinformasjon = getPensjoninformasjonFraSak(prefillData)

        //hent korrekt sak fra context
        val pensak: V1Sak = getPensjonSak(prefillData, pendata)

        //val pensak = getPensjonSak(prefillData, pendata)
        //nyere metdoe mye å omgjøre på for å ta denne i bruk!

        //4.0
        return createInformasjonOmYtelserList(prefillData, pensak)

    }

    /**
     * 9.1- 9.2
     *
     *  Fra PSAK, kravdato på alderspensjonskravet
     *  Fra PSELV eller manuell kravblankett:
     *  Fyller ut fra hvilket tidspunkt bruker ønsker å motta pensjon fra Norge.
     *  Det er et spørsmål i søknadsdialogen og på manuell kravblankett. Det er ikke nødvendigvis lik virkningstidspunktet på pensjonen.
     */
    private fun createKravDato(valgtSak: V1Sak, valgtKrav: V1KravHistorikk): Krav? {
        logger.debug("9.1        Dato Krav (med korrekt data fra PESYS krav.virkningstidspunkt)")

        logger.debug("--------------------------------------------------------------------------------------------------------")
        logger.debug("SakId      :  ${valgtSak.sakId}")
        logger.debug("SakType    :  ${valgtSak.sakType}")
        logger.debug("Status     :  ${valgtSak.status}")
        logger.debug("KravType   :  ${valgtKrav.kravType}")
        logger.debug("mottattDato:  ${valgtKrav.mottattDato}")
        logger.debug("--------------------------------------------------------------")

        logger.debug("Prøver å sette kravDato til Virkningstidpunkt: ${valgtKrav.kravType} og dato: ${valgtKrav.mottattDato}")
        return Krav(
                dato = valgtKrav.mottattDato?.simpleFormat() ?: ""
        )
    }

    /**
     *
     *  4.1
     *  Vi må hente informasjon fra PSAK:
     *  - Hvilke saker bruker har
     *  - Status på hver sak
     *  - Hvilke kravtyper det finnes på hver sak
     *  - Saksnummer på hver sak
     *  - første virk på hver sak
     *  Hvis bruker mottar en løpende ytelse, er det denne ytelsen som skal vises.
     *  Hvis bruker mottar både uføretrygd og alderspensjon, skal det vises alderspensjon.
     *  Hvis bruker ikke mottar løpende ytelse, skal man sjekke om han har søkt om en norsk ytelse.
     *  Hvis han har søkt om flere ytelser, skal man bruke den som det sist er søkt om.
     *  Det skal vises resultatet av denne søknaden, dvs om saken er avslått eller under behandling.
     *  For å finne om han har søkt om en norsk ytelse, skal man se om det finnes krav av typen:  «Førstegangsbehandling», «Førstegangsbehandling Norge/utland»,
     *  «Førstegangsbehandling bosatt utland» eller «Mellombehandling».
     *  Obs, krav av typen «Førstegangsbehandling kun utland» eller Sluttbehandling kun utland» gjelder ikke norsk ytelse.
     */
    private fun createInformasjonOmYtelserList(prefillData: PrefillDataModel,
                                               pensak: V1Sak): Pensjon {
        logger.debug("4.1           Informasjon om ytelser")

        val spesialStatusList = listOf("TIL_BEHANDLING")
        //INNV
        var krav: Krav? = null

        val ytelselist = mutableListOf<YtelserItem>()

        logger.debug("--------------------------------------------------------------------------------------------------------")
        logger.debug("SakId:  ${pensak.sakId}")
        logger.debug("SakType:  ${pensak.sakType}")
        logger.debug("Status:  ${pensak.status}")
        logger.debug("forsteVirkningstidspunkt:  ${pensak.forsteVirkningstidspunkt}")
        logger.debug("--------------------------------------------------------------")
        logger.debug("KravHistorikk\n")

        sortertKravHistorikk(pensak).forEach {
            logger.debug("KravType: ${it.kravType}")
            logger.debug("mottatDato: ${it.mottattDato}")
            logger.debug("Virkningstidspunkt: ${it.virkningstidspunkt}")
            logger.debug("Status: ${it.status}")
        }

        logger.debug("--------------------------------------------------------------")
        logger.debug("YtelsePerMaaned\n")

        hentYtelsePerMaanedSortert(pensak).forEach {
            logger.debug("Fom:    ${it.fom} ")
            logger.debug("Fom:    ${it.belop} ")
            logger.debug("VinnendenMetode:   ${it.vinnendeBeregningsmetode}")
        }
        logger.debug("--------------------------------------------------------------------------------------------------------")

        if (spesialStatusList.contains(pensak.status)) {
            logger.debug("Valgtstatus")
            //kjøre ytelselist forkortet
            ytelselist.add(createYtelseMedManglendeYtelse(prefillData, pensak))

            if (krav == null) {
                val kravHistorikkMedUtland = hentKravHistorikkMedKravStatusTilBehandling(pensak)
                krav = createKravDato(pensak, kravHistorikkMedUtland)
                logger.warn("9.1        Opprettett P2000 med mulighet for at denne mangler KravDato!")
            }

        } else {
            if (pensak.sakType == KSAK.ALDER.toString()) {
                try {
                    val kravHistorikkMedUtland = hentKravHistorikkForsteGangsBehandlingUtlandEllerForsteGang(pensak)
                    val ytelseprmnd = hentYtelsePerMaanedDenSisteFraKrav(kravHistorikkMedUtland, pensak)

                    //kjøre ytelselist på normal
                    if (krav == null) {
                        krav = createKravDato(pensak, kravHistorikkMedUtland)
                    }

                    ytelselist.add(createYtelserItem(prefillData, ytelseprmnd, pensak))
                } catch (ex: Exception) {
                    logger.error(ex.message, ex)
                    ytelselist.add(createYtelseMedManglendeYtelse(prefillData, pensak))
                }
            }

            if (pensak.sakType == KSAK.UFOREP.toString()) {
                try {
                    val kravHistorikk = hentKravHistorikkSisteRevurdering(pensak)
                    val ytelseprmnd = hentYtelsePerMaanedDenSisteFraKrav(kravHistorikk, pensak)

                    ytelselist.add(createYtelserItem(prefillData, ytelseprmnd, pensak))
                } catch (ex: Exception) {
                    logger.error(ex.message, ex)
                    ytelselist.add(createYtelseMedManglendeYtelse(prefillData, pensak))
                }
            }
        }

        return Pensjon(
                ytelser = ytelselist,
                kravDato = krav
        )
    }

    /**
     *  4.1 (for kun_uland,mangler inngangsvilkår)
     */
    private fun createYtelseMedManglendeYtelse(prefillData: PrefillDataModel, pensak: V1Sak): YtelserItem {
        return YtelserItem(
                //4.1.1
                ytelse = creatYtelser(pensak),
                //4.1.3 - fast satt til søkt
                status = "01",
                //4.1.4
                pin = createInstitusjonPin(prefillData),
                //4.1.4.1.4
                institusjon = createInstitusjon(prefillData)
        )
    }

    /**
     *  4.1
     */
    private fun createYtelserItem(prefillData: PrefillDataModel, ytelsePrmnd: V1YtelsePerMaaned, pensak: V1Sak): YtelserItem {
        logger.debug("4.1   YtelserItem")
        return YtelserItem(

                //4.1.1
                ytelse = creatYtelser(pensak),

                //4.1.2.1 - nei
                annenytelse = null, //ytelsePrmnd.vinnendeBeregningsmetode,

                //4.1.3 (dekkes av pkt.4.1.1)
                status = createPensionStatus(pensak),
                //4.1.4
                pin = createInstitusjonPin(prefillData),
                //4.1.4.1.4
                institusjon = createInstitusjon(prefillData),

                //4.1.5
                startdatoutbetaling = ytelsePrmnd.fom?.simpleFormat(),
                //4.1.6
                sluttdatoutbetaling = null,
                //4.1.7 (sak - forstevirkningstidspunkt)
                startdatoretttilytelse = createStartdatoForRettTilYtelse(pensak),
                //4.1.8 -- NEI
                sluttdatoretttilytelse = null, // ytelsePrmnd.tom?.let { it.simpleFormat() },

                //4.1.9 - 4.1.9.5.1
                beloep = createYtelseItemBelop(ytelsePrmnd, ytelsePrmnd.ytelseskomponentListe),

                //4.1.10.1
                mottasbasertpaa = createPensionBasedOn(prefillData, pensak),
                //4.1.10.2 - nei
                totalbruttobeloepbostedsbasert = null,
                //4.1.10.3
                totalbruttobeloeparbeidsbasert = ytelsePrmnd.belop.toString(),

                //N/A
                ytelseVedSykdom = null //7.2 //P2100
        )
    }

    /**
     *  4.1.7 Start date of entitlement to benefits  - trenger ikke fylles ut
     */
    private fun createStartdatoForRettTilYtelse(pensak: V1Sak): String? {
        logger.debug("4.1.7         Startdato for ytelse (forsteVirkningstidspunkt) ")
        return pensak.forsteVirkningstidspunkt?.simpleFormat()
    }

    private fun createInstitusjon(prefillData: PrefillDataModel): Institusjon? {
        logger.debug("4.1.4.1.4     Institusjon")
        return Institusjon(
                institusjonsid = prefillData.andreInstitusjon?.institusjonsid,
                institusjonsnavn = prefillData.andreInstitusjon?.institusjonsnavn,
                saksnummer = prefillData.penSaksnummer
        )
    }

    /**
     *  4.1.1
     */
    private fun creatYtelser(pensak: V1Sak): String? {
        logger.debug("4.1.1         Ytelser")

        return when (KSAK.valueOf(pensak.sakType)) {
            KSAK.ALDER -> "10"
            KSAK.GJENLEV -> "11"
            KSAK.UFOREP -> "08"
            else -> "07"
        }
    }

    /**
     *  4.1.3
     *
     *  Dekkes av kravene på pkt 4.1.1
     *  Her skal vises status på den sist behandlede ytelsen, dvs om kravet er blitt avslått, innvilget eller er under behandling.
     *  Hvis bruker mottar en løpende ytelse, skal det alltid vises Innvilget.
     *  [01] Søkt
     *  [02] Innvilget
     *  [03] Avslått
     *  [04] Foreløpig
     */
    private fun createPensionStatus(pensak: V1Sak): String? {
        logger.debug("4.1.3         Status")

        return when (pensak.status) {
            "INNV" -> "02"
            "AVSL" -> "03"
            else -> "01"
        }
    }

    /**
     *  4.1.10.1
     *
     *  Fra PSAK. Det må settes opp forretningsregler. Foreløpig forslag:
     *  Hvis bruker har Dnr, hukes kun av for Working
     *  Hvis bruker har Fnr:
     *  Hvis UT: Hvis bruker har minsteytelse, velges kun Residence. Ellers velges både Residence og Working.
     *  Hvis AP: Hvis bruker mottar tilleggspensjon, velges både Residence og Working. Ellers velges kun Residence.
     *  Hvis GJP: Hvis bruker mottar tilleggspensjon, velges både Residence og Working. Ellers velges kun Residence.
     */
    private fun createPensionBasedOn(prefillData: PrefillDataModel, pensak: V1Sak): String? {
        logger.debug("4.1.10.1      Pensjon basertpå")
        val navfnr = NavFodselsnummer(prefillData.personNr)

        val sakType = KSAK.valueOf(pensak.sakType)

        if (navfnr.isDNumber()) {
            return "01" // Botid
        }
        return when (sakType) {
            KSAK.ALDER -> "02"
            KSAK.UFOREP -> "01"
            KSAK.GJENLEV -> "01"
            else -> null
        }
    }

  /**
	 *  4.1.4.1
	 *
	 *  4.1.4.1.1
	 *  preutfylles med Norge.Dette kan gjøres av EESSI-pensjon
	 *  4.1.4.1.2
	 *  preutfylles med norsk PIN
	 *  4.1.4.1.3
	 *  kan preutfylles med Pensjon (men det er vel opplagt at pensjonsytelse tilhører sektor Pensjon)  Dette kan gjøres av EESSI-pensjon
	 *  4.1.4.1.4
	 *  nei
	 *  4.1.4.1.4.1
	 *  Preutfylles med NAV sin Institusjons-ID fra IR.    Dette kan gjøres av EESSI-pensjon
	 *  4.1.4.1.4.2
	 *  preutfylles med NAV Dette kan gjøres av EESSI-pensjon
	 */
    private fun createInstitusjonPin(prefillData: PrefillDataModel): PinItem {
        logger.debug("4.1.4.1       Institusjon Pin")
        return PinItem(
                //4.1.4.1.1
                land = "NO",
                //4.1.4.1.2
                identifikator = prefillData.personNr,
                //4.1.4.1.3
                sektor = "04", //(kun pensjon)
                institusjon = null
        )
    }

    /**
     *  4.1.9
     *
     *  4.1.9 Fra PSAK
     *  Denne seksjonen (4.1.9) er gjentakende.
     *  Vi skal vise beløpshistorikk 5 år tilbake i tid.
     *  Hvis bruker mottar en løpende ytelse med beløp større enn 0 kr, skal det nåværende beløpet vises her.
     *  Det skal gjentas et beløp for hver beløpsendring, inntil 5 år tilbake i tid.
     *  4.1.9.2 Currency Fra PSAK.
     *  Her fylles ut FOM-dato for hvert beløp i beløpshistorikk 5 år tilbake i tid.
     *  4.1.9.4 Payment frequency     Preutfylt med Månedlig
     *  OBS – fra år 2021 kan det bli aktuelt med årlige utbetalinger, pga da kan brukere få utbetalt kap 20-pensjoner med veldig små beløp (ingen nedre grense)
     *  4.1.9.5.1  nei
     */
    private fun createYtelseItemBelop(ytelsePrMnd: V1YtelsePerMaaned, ytelsekomp: List<V1Ytelseskomponent>): List<BeloepItem> {
        logger.debug("4.1.9         Beløp")
        val list = mutableListOf<BeloepItem>()
        ytelsekomp.forEach {
            list.add(BeloepItem(

                    //4.1.9.1
                    beloep = it.belopTilUtbetaling.toString(),

                    //4.1.9.2
                    valuta = "NOK",

                    //4.1.9.3
                    gjeldendesiden = createGjeldendesiden(ytelsePrMnd),

                    //4.1.9.4
                    betalingshyppighetytelse = createBetalingshyppighe(),

                    //4.1.9.5
                    annenbetalingshyppighetytelse = null

            ))
        }
        return list
    }

    /**
     *  4.1.9.3
     *
     *  Fra PSAK.
     *  Her fylles ut FOM-dato for hvert beløp i beløpshistorikk 5 år tilbake i tid.
     */
    private fun createGjeldendesiden(ytelsePrMnd: V1YtelsePerMaaned): String? {
        logger.debug("4.1.9.3         Gjeldendesiden")
        return ytelsePrMnd.fom.simpleFormat()
    }

    /**
     *  4.1.9.4
     *
     *  Preutfylt med Månedlig
     *  OBS – fra år 2021 kan det bli aktuelt med årlige utbetalinger, pga da kan brukere få utbetalt kap 20-pensjoner med veldig små beløp (ingen nedre grense)
     *
     *  01: Årlig
     *  02: Kvartal
     *  03: Månded 12/år
     *  04: Måned 13/år
     *  05: Måned 14/år
     *  06: Ukentlig
     *  99: Annet
     */
    private fun createBetalingshyppighe(): String {
        logger.debug("4.1.9.4         Betalingshyppighetytelse")
        return "03"
    }

    /**
     *  Henter ut pensjoninformasjon med brukersSakerListe
     */
    fun getPensjoninformasjonFraSak(prefillData: PrefillDataModel): Pensjonsinformasjon {
        return dataFromPEN.hentPensjoninformasjonMedPinid(prefillData)
    }

    /**
     *  Henter ut v1Sak på brukersSakerListe ut ifra valgt sakid i prefilldatamodel
     */
    fun getPensjonSak(prefillData: PrefillDataModel, pendata: Pensjonsinformasjon): V1Sak {
        return dataFromPEN.hentMedSak(prefillData, pendata)
    }

    /**
     *  Henter ut liste av gyldige sakkType fra brukerSakListe
     */
    fun getPensjonSakTypeList(pendata: Pensjonsinformasjon): List<KSAK> {
        val ksaklist = mutableListOf<KSAK>()

        pendata.brukersSakerListe.brukersSakerListe.forEach {
            if (KSAK.isValid(it.sakType)) {
                val ksakType = KSAK.valueOf(it.sakType)
                ksaklist.add(ksakType)
            }
        }
        return ksaklist
    }

    private fun hentYtelsePerMaanedSortert(pensak: V1Sak): List<V1YtelsePerMaaned> {
        val ytelseprmnd = pensak.ytelsePerMaanedListe
        val liste = mutableListOf<V1YtelsePerMaaned>()
        if (ytelseprmnd != null) {
            liste.addAll(ytelseprmnd.ytelsePerMaanedListe)
        }
        return liste.asSequence().sortedBy { it.fom.toGregorianCalendar() }.toList()
    }

    private fun hentYtelsePerMaanedDenSisteFraKrav(fraKravhistorik: V1KravHistorikk, pensak: V1Sak): V1YtelsePerMaaned {
        val ytelseprmnd = pensak.ytelsePerMaanedListe
        val liste = ytelseprmnd.ytelsePerMaanedListe as List<V1YtelsePerMaaned>
        val sortList = liste.asSequence().sortedBy { it.fom.toGregorianCalendar() }.toList()

        logger.debug("-----------------------------------------------------")
        sortList.forEach {
            logger.debug("Sammenligner ytelsePerMaaned: ${it.fom}  Med virkningtidpunkt: ${fraKravhistorik.virkningstidspunkt}")
            if (it.fom.toGregorianCalendar() >= fraKravhistorik.virkningstidspunkt.toGregorianCalendar()) {
                logger.debug("Return følgende ytelsePerMaaned: ${it.fom}")
                return it
            }
            logger.debug("-----------------------------------------------------")
        }
        return V1YtelsePerMaaned()
    }

    private fun sortertKravHistorikk(pensak: V1Sak): List<V1KravHistorikk> {
        val list = pensak.kravHistorikkListe.kravHistorikkListe.toList()
        return list.asSequence().sortedBy { it.mottattDato.toGregorianCalendar() }.toList()
    }

    private fun hentKravHistorikkSisteRevurdering(pensak: V1Sak): V1KravHistorikk {
        val sortList = sortertKravHistorikk(pensak)

        for (i in sortList) {
            logger.debug("leter etter $REVURD i  ${i.kravType} med dato ${i.virkningstidspunkt}")
            if (i.kravType == "REVURD") {
                logger.debug("Fant Kravhistorikk med $i.kravType")
                return i
            }
        }
        return V1KravHistorikk()
    }

    private fun hentKravHistorikkForsteGangsBehandlingUtlandEllerForsteGang(pensak: V1Sak): V1KravHistorikk {
        return hentKravHistorikkMedKravType(listOf(F_BH_MED_UTL, FORSTEG_BH), pensak)
    }

    private fun hentKravHistorikkMedKravType(kravType: List<String>, pensak: V1Sak): V1KravHistorikk {
        val sortList = sortertKravHistorikk(pensak)
        sortList.forEach {
            logger.debug("leter etter Kravtype: $kravType, fant ${it.kravType} med dato i ${it.virkningstidspunkt}")
            if (kravType.contains(it.kravType)) {
                logger.debug("Fant Kravhistorikk med $kravType")
                return it
            }
        }
        logger.error("Fant ikke noe Kravhistorikk. med $kravType HVA GJØR VI NÅ?")
        return V1KravHistorikk()
    }

    private fun hentKravHistorikkMedKravStatusTilBehandling(pensak: V1Sak): V1KravHistorikk {
        val sortList = sortertKravHistorikk(pensak)
        sortList.forEach {
            logger.debug("leter etter Krav status med TIL_BEHANDLING, fant ${it.kravType} med virkningstidspunkt dato : ${it.virkningstidspunkt}")
            if (TIL_BEHANDLING == it.status) {
                logger.debug("Fant Kravhistorikk med ${it.status}")
                return it
            }
        }
        logger.error("Fant ikke noe Kravhistorikk..$TIL_BEHANDLING HVA GJØR VI NÅ?")
        return V1KravHistorikk()
    }
}
