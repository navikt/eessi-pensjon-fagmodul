package no.nav.eessi.pensjon.fagmodul.prefill.person

import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.fagmodul.prefill.model.BrukerInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.utils.simpleFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class PrefillNav(private val preutfyllingPersonFraTPS: PrefillPersonDataFromTPS,
                 @Value("\${eessi.pensjon_lokalid}") private val institutionid: String,
                 @Value("\${eessi.pensjon_lokalnavn}") private val institutionnavn: String) : Prefill<Nav> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillNav::class.java) }
    private val barnSEDlist = listOf("P2000", "P2100", "P2200")

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
                verge = createVerge(),

                //8.0 Bank lagt in på bruker (P2000)

                //9.0  - Tillgeggsinfo og kravdata. benyttes i P2x000
                krav = createDiverseOgKravDato()
        )
    }

    //7.0  TODO: 7. Informasjon om representant/verge hva kan vi hente av informasjon? fra hvor
    private fun createVerge(): Verge? {
        logger.debug("7.0           (IKKE NOE HER ENNÅ!!) Informasjon om representant/verge")
        return null
    }

    //8.0 Bank detalsjer om bank betalinger.
    private fun createBankData(prefillData: PrefillDataModel): Bank? {
        logger.debug("8.0           Informasjon om betaling")
        logger.debug("8.1           Informasjon om betaling")
        return prefillData.getPersonInfo()?.let { personInfo ->
            Bank(
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
    }

    //
    //TODO: Dette må hentes fra sak/krav
    private fun createDiverseOgKravDato(): Krav {
        logger.debug("9.1           (FRA V1SAK?) Dato for krav")
        return Krav(Date().simpleFormat())
    }

    // kan denne utfylling benyttes på alle SED?
    // etterlatt pensjon da er dette den avdøde.(ikke levende)
    // etterlatt pensjon da er den levende i pk.3 sed (gjenlevende) (pensjon.gjenlevende)
    private fun createBrukerfraTPS(utfyllingData: PrefillDataModel): Bruker {
        val subjektIdent =
                if (utfyllingData.erGyldigEtterlatt()) {
                    logger.debug("2.0           Avdod person (Gjenlevende pensjon)")
                    utfyllingData.avdod
                } else {
                    logger.debug("2.0           Forsikret person")
                    utfyllingData.personNr
                }
        return preutfyllingPersonFraTPS.prefillBruker(
                subjektIdent,
                createBankData(utfyllingData),
                createInformasjonOmAnsettelsesforhold(utfyllingData)
        )
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
        logger.debug("3.0           Informasjon om personens ansettelsesforhold og selvstendige næringsvirksomhet")
        logger.debug("3.1           Informasjon om ansettelsesforhold og selvstendig næringsvirksomhet ")
        val personInfo = prefillData.getPersonInfo() ?: return null
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
}

