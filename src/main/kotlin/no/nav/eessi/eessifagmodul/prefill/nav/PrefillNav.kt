package no.nav.eessi.eessifagmodul.prefill.nav

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.Prefill
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.simpleFormat
import no.nav.eessi.eessifagmodul.utils.typeRefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class PrefillNav(private val preutfyllingPersonFraTPS: PrefillPersonDataFromTPS) : Prefill<Nav> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillNav::class.java) }
    private val barnSEDlist = listOf("P2000", "P2100", "P2200")

    @Value("\${eessi.pensjon_lokalid}")
    lateinit var institutionid: String

    @Value("\${eessi.pensjon_lokalnavn}")
    lateinit var institutionnavn: String

    override fun prefill(prefillData: PrefillDataModel): Nav {

        return Nav(
                //1.0
                eessisak = createLokaltsaksnummer(prefillData),

                //createBrukerfraTPS død hvis etterlatt (etterlatt aktoerregister fylt ut)
                //2.0 For levende, eller hvis person er dod (hvis dod flyttes levende til 3.0)
                //3.0 Anstalleseforhold og
                //8.0 Bank
                bruker = createBrukerfraTPS(prefillData),

                //4.0 Ytelser ligger under pensjon object (P2000)

                //5.0 ektefelle eller partnerskap
                ektefelle = createEktefelleEllerPartnerfraTPS(prefillData),

                //6.0 skal denne kjøres hver gang? eller kun under P2000? P2100
                barn = createBarnlistefraTPS(prefillData),

                //7.0 verge
                verge = createVerge(prefillData),

                //8.0 Bank lagt in på bruker (P2000)

                //9.0  - Tillgeggsinfo og kravdata. benyttes i P2x000
                krav = createDiverseOgKravDato(prefillData)
        )
    }

    //7.0  TODO: 7. Informasjon om representant/verge hva kan vi hente av informasjon? fra hvor
    private fun createVerge(prefillData: PrefillDataModel): Verge? {
        logger.debug("7.0           (IKKE NOE HER ENNÅ!!) Informasjon om representant/verge")
        return null
    }

    //8.0 Bank detalsjer om bank betalinger.
    private fun createBankData(prefillData: PrefillDataModel): Bank? {
        logger.debug("8.0           Informasjon om betaling")
        return createBetaling(prefillData)
    }

    //
    //TODO: Dette må hentes fra sak/krav
    private fun createDiverseOgKravDato(prefillData: PrefillDataModel): Krav {
        logger.debug("9.1           (FRA V1SAK?) Dato for krav")
        return Krav(Date().simpleFormat())
    }

    // kan denne utfylling benyttes på alle SED?
    // etterlatt pensjon da er dette den avdøde.(ikke levende)
    // etterlatt pensjon da er den levende i pk.3 sed (gjenlevende) (pensjon.gjenlevende)
    private fun createBrukerfraTPS(utfyllingData: PrefillDataModel): Bruker {
        if (utfyllingData.erGyldigEtterlatt()) {
            logger.debug("2.0           Avdod person (Gjenlevende pensjon)")
            val bruker = preutfyllingPersonFraTPS.prefillBruker(utfyllingData.avdod)

            logger.debug("3.0           Informasjon om personens ansettelsesforhold og selvstendige næringsvirksomhet")
            bruker.arbeidsforhold = createInformasjonOmAnsettelsesforhold(utfyllingData)

            //logger.debug("8.0           Informasjon om betaling")
            bruker.bank = createBankData(utfyllingData)
            return bruker
        }

        logger.debug("2.0           Forsikret person")
        val bruker = preutfyllingPersonFraTPS.prefillBruker(utfyllingData.personNr)

        //Denne finnes ikke i PK-553333
        logger.debug("3.0           Informasjon om personens ansettelsesforhold og selvstendige næringsvirksomhet")
        bruker.arbeidsforhold = createInformasjonOmAnsettelsesforhold(utfyllingData)

        //logger.debug("8.0           Informasjon om betaling")
        bruker.bank = createBankData(utfyllingData)

        return bruker
    }

    //utfylling av liste av barn under 18år
    private fun createBarnlistefraTPS(utfyllingData: PrefillDataModel): List<BarnItem>? {
        //sjekke om SED er P2x00 for utfylling av BARN
        //sjekke punkt for barn. pkt. 6.0 for P2000 og P2200 pkt. 8.0 for P2100
        if (barnSEDlist.contains(utfyllingData.getSEDid()).not()) {
            logger.debug("6.0/8.0           SKIP Preutfylling barn, ikke P2x00")
            return null
        }
        val barnaspin = preutfyllingPersonFraTPS.hentBarnaPinIdFraBruker(utfyllingData.personNr)
        val barnlist = mutableListOf<BarnItem>()
        logger.debug("6.0/8.0           Preutfylling barn, antall: (${barnaspin.size}")
        barnaspin.forEach {
            logger.debug("                  Legger til barn")
            val barnBruker = preutfyllingPersonFraTPS.prefillBruker(it)
            val barn = BarnItem(
                    person = barnBruker.person,
                    far = barnBruker.far,
                    mor = barnBruker.mor,
                    relasjontilbruker = "BARN"
            )
            barnlist.add(barn)
        }
        if (barnlist.isNotEmpty()) {
            return barnlist
        }
        return null
    }

    //ektefelle / partner
    private fun createEktefelleEllerPartnerfraTPS(utfyllingData: PrefillDataModel): Ektefelle? {
        logger.debug("5.0           Utfylling av ektefelle")
        return preutfyllingPersonFraTPS.hentEktefelleEllerPartnerFraBruker(utfyllingData)
    }

    //lokal sak pkt 1.0 i gjelder alle SED
    private fun createLokaltsaksnummer(prefillData: PrefillDataModel): List<EessisakItem> {
        logger.debug("1.1           Lokalt saksnummer (hvor hentes disse verider ifra?")
        return listOf(EessisakItem(
                institusjonsid = institutionid,
                institusjonsnavn = institutionnavn,
                saksnummer = prefillData.penSaksnummer,
                land = "NO"
        ))
    }


    private fun createInformasjonOmAnsettelsesforhold(prefillData: PrefillDataModel): List<ArbeidsforholdItem>? {
        logger.debug("3.1           Informasjon om ansettelsesforhold og selvstendig næringsvirksomhet ")
        val personInfo = hentPersonInformasjon(prefillData) ?: return null
        return listOf(createAnsettelsesforhold(personInfo))
    }

    fun createAnsettelsesforhold(personInfo: BrukerInformasjon): ArbeidsforholdItem {
        logger.debug("3.1           Ansettelseforhold/arbeidsforhold")

        return ArbeidsforholdItem(

                //3.1.1.
                yrke = "",

                //3.1.2
                type = personInfo.workType ?: "",

                //3.1.3
                planlagtstartdato = personInfo.workStartDate?.simpleFormat() ?: "",

                //3.1.4
                sluttdato = personInfo.workEndDate?.simpleFormat() ?: "",

                //3.1.5
                planlagtpensjoneringsdato = personInfo.workEstimatedRetirementDate?.simpleFormat() ?: "",

                //3.1.6
                arbeidstimerperuke = personInfo.workHourPerWeek ?: ""

        )
    }

    private fun createBetaling(prefillData: PrefillDataModel): Bank? {
        logger.debug("8.1           Informasjon om betaling")
        val personInfo = hentPersonInformasjon(prefillData) ?: return null

        return Bank(
                navn = personInfo.bankName,
                konto = Konto(
                        innehaver = Innehaver(
                                rolle = "01", //forsikkret bruker .. avventer med Verge "02",
                                navn = personInfo.bankName
                        ),
                        sepa = Sepa(
                                iban = personInfo.bankIban,
                                swift = personInfo.bankBicSwift
                        )

                ),
                adresse = Adresse(
                        gate = personInfo.bankAddress,
                        land = personInfo.bankCountry?.currencyLabel
                )
        )

    }

    //henter ut PersonInfo payload fra UI
    fun hentPersonInformasjon(prefillData: PrefillDataModel): BrukerInformasjon? {
        val persinfo = prefillData.getPartSEDasJson("PersonInfo") ?: return null
        return mapJsonToAny(persinfo, typeRefs())
    }

}

