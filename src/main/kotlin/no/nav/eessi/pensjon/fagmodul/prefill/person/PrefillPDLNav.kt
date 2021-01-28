package no.nav.eessi.pensjon.fagmodul.prefill.person

import no.nav.eessi.pensjon.fagmodul.prefill.model.BrukerInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.Adresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.ArbeidsforholdItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bank
import no.nav.eessi.pensjon.fagmodul.sedmodel.BarnItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.EessisakItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Ektefelle
import no.nav.eessi.pensjon.fagmodul.sedmodel.Foedested
import no.nav.eessi.pensjon.fagmodul.sedmodel.Foreldre
import no.nav.eessi.pensjon.fagmodul.sedmodel.Innehaver
import no.nav.eessi.pensjon.fagmodul.sedmodel.Konto
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Sepa
import no.nav.eessi.pensjon.fagmodul.sedmodel.StatsborgerskapItem
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import no.nav.eessi.pensjon.utils.simpleFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person as PDLPerson


private val UGYLDIGE_LAND_RINA = listOf("XXK")

@Component
class PrefillPDLNav(private val prefillAdresse: PrefillPDLAdresse,
                    @Value("\${eessi-pensjon-institusjon}") private val institutionid: String,
                    @Value("\${eessi-pensjon-institusjon-navn}") private val institutionnavn: String) {

    companion object {
        private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPDLNav::class.java) }

        private fun PDLPerson.norskIdent() = this.identer.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident

        private fun PDLPerson.kortKjonn() = when(this.kjoenn?.kjoenn) {
            KjoennType.MANN -> "M"
            KjoennType.KVINNE -> "K"
            else -> "U"
        }
        private fun PDLPerson.foedseldato() = this.foedsel?.foedselsdato.toString()


        fun createFodested(pdlperson: PDLPerson): Foedested? {
            logger.debug("2.1.8.1       Fødested")

            logger.debug("              foedsel : ${pdlperson.foedsel}")

            val fsted = Foedested(
                    land = pdlperson.foedsel?.foedeland ?: "Unknown",
                    by = "Unkown",
                    region = ""
            )

            return fsted.takeUnless { it.land == "Unknown" }
        }

        fun isPersonAvdod(pdlperson: PDLPerson) : Boolean {
            return pdlperson.erDoed()
                .also {
                 if (it)
                  logger.debug("Person er avdod (ingen adresse å hente).")
                }
        }

        fun createPersonPinNorIdent(
                personpdl: PDLPerson,
                institusjonId: String,
                institusjonNavn: String): List<PinItem> {
            logger.debug("2.1.7         Fodselsnummer/Personnummer")
            return listOf(
                    PinItem(
                            //hentet lokal NAV insitusjondata fra applikasjon properties.
                            institusjonsnavn = institusjonNavn,
                            institusjonsid = institusjonId,

                            //NAV/Norge benytter ikke seg av sektor, setter denne til null
                            //personnr
                            identifikator = personpdl.identer.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident,

                            // norsk personnr settes alltid til NO da vi henter NorIdent
                            land = "NO"
                    )
            )
        }


        //8.0 Bank detalsjer om bank betalinger.
        private fun createBankData(personInfo: BrukerInformasjon): Bank {
            logger.debug("8.0           Informasjon om betaling")
            logger.debug("8.1           Informasjon om betaling")
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

        //utfylling av liste av barn under 18år
        private fun createBarnliste(barn: List<Bruker?>): List<BarnItem>? {
            return barn
                    .filterNotNull()
                    .map { BarnItem(mor = it.mor, person = it.person, far = it.far, relasjontilbruker = Familierelasjonsrolle.BARN.name) }
                    .takeIf { it.isNotEmpty() }
        }

        private fun createEktefelleType(typevalue: Sivilstandstype): String {
            logger.debug("5.1           Ektefelle/Partnerskap-type : $typevalue")
            return when (typevalue) {
                Sivilstandstype.GIFT -> "ektefelle"
                Sivilstandstype.PARTNER -> "part_i_et_registrert_partnerskap"
                else -> "" // endring fra TPS istede for SAMBOER så blank hvis ukjent/ikke gift/partner.
            }
        }



        private fun createAnsettelsesforhold(personInfo: BrukerInformasjon): ArbeidsforholdItem {
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

        private fun createInformasjonOmAnsettelsesforhold(personInfo: BrukerInformasjon): List<ArbeidsforholdItem>? {
            logger.debug("3.0           Informasjon om personens ansettelsesforhold og selvstendige næringsvirksomhet")
            logger.debug("3.1           Informasjon om ansettelsesforhold og selvstendig næringsvirksomhet ")
            return listOf(createAnsettelsesforhold(personInfo))
        }

        //lokal sak pkt 1.0 i gjelder alle SED
        private fun createEssisakItem(penSaksnummer: String, institusjonId: String, institusjonNavn: String): List<EessisakItem> {
            logger.debug("1.1           Lokalt saksnummer (hvor hentes disse verider ifra?")
            return listOf(EessisakItem(
                    institusjonsid = institusjonId,
                    institusjonsnavn = institusjonNavn,
                    saksnummer = penSaksnummer,
                    land = "NO"
            ))
        }
    }

    fun prefill(penSaksnummer: String, bruker: PersonId, avdod: PersonId?, personData: PersonDataCollection, brukerInformasjon: BrukerInformasjon?): Nav {
        val forsikretPerson = personData.forsikretPerson
        val avdodEllerGjenlevende = personData.gjenlevendeEllerAvdod
        val ektefellePerson = personData.ektefellePerson
        val sivilstandstype = personData.sivilstandstype
        val barnPersonList = personData.barnPersonList

        val personInfo = brukerInformasjon

        return Nav(
                //1.0
                eessisak = createEssisakItem(penSaksnummer, institutionid, institutionnavn),

                //createBrukerfraTPS død hvis etterlatt (etterlatt aktoerregister fylt ut)
                //2.0 For levende, eller hvis person er dod (hvis dod flyttes levende til 3.0)
                //3.0 Anstalleseforhold og
                //8.0 Bank
                bruker = if (avdodEllerGjenlevende == null) null else
                            createBruker(
                                    avdodEllerGjenlevende,
                                    if (personInfo == null) null else createBankData(personInfo),
                                    if (personInfo == null) null else createInformasjonOmAnsettelsesforhold(personInfo)),

                //4.0 Ytelser ligger under pensjon object (P2000)

                //5.0 ektefelle eller partnerskap
                ektefelle = if (ektefellePerson == null) null else
                        createEktefellePartner(createBruker(ektefellePerson, null, null), sivilstandstype),

                //6.0 skal denne kjøres hver gang? eller kun under P2000? P2100
                //sjekke om SED er P2x00 for utfylling av BARN
                //sjekke punkt for barn. pkt. 6.0 for P2000 og P2200 pkt. 8.0 for P2100
                barn = createBarnliste(barnPersonList.map { createPersonBarn(it, personData) })
        )
    }

    fun createBruker(pdlperson: PDLPerson,
                     bank: Bank?,
                     ansettelsesforhold: List<ArbeidsforholdItem>?): Bruker? {
            return Bruker(
                person = createPersonData(pdlperson),
                adresse = if (isPersonAvdod(pdlperson)) null else prefillAdresse.createPersonAdresse(pdlperson),
                //far = if (isPersonAvdod(brukerTPS)) null else createRelasjon(RelasjonEnum.FAR, brukerTPS),
                //mor = if (isPersonAvdod(brukerTPS)) null else createRelasjon(RelasjonEnum.MOR, brukerTPS),
                bank = bank,
                arbeidsforhold = ansettelsesforhold)
    }

    fun createPersonBarn(pdlperson: PDLPerson, personData: PersonDataCollection): Bruker? {
        logger.debug("6.0 barn og familierelasjoner far/mor")
        return Bruker(
            person = createPersonData(pdlperson),
            far = createFamilieRelasjon(Familierelasjonsrolle.FAR, pdlperson, personData),
            mor = createFamilieRelasjon(Familierelasjonsrolle.MOR, pdlperson, personData)
        )
    }


    //mor / far
    private fun createFamilieRelasjon(relasjon: Familierelasjonsrolle, barnpdlperson: PDLPerson, personData: PersonDataCollection): Foreldre? {

        //ident til relasjon (FAR/MOR)
        val relasjonIdent = barnpdlperson.familierelasjoner.firstOrNull { it.relatertPersonsRolle == relasjon }?.relatertPersonsIdent

        val forsikretPerson = personData.forsikretPerson
        val ektePerson = personData.ektefellePerson

        //hvem er foreldre for denne relasjonen (FAR/MOR)
        val foreldrePerson = if (ektePerson?.norskIdent() == relasjonIdent) {
            ektePerson
        } else if (forsikretPerson?.norskIdent() == relasjonIdent) {
            forsikretPerson
        } else {
            null
        }

        if (foreldrePerson == null) return null

        logger.debug("              Relasjon til : ${foreldrePerson.navn?.sammensattNavn}")
        val navn = foreldrePerson.navn
        val relasjonperson = Person(
            pin = listOf(
                PinItem(
                    institusjonsnavn = institutionnavn,
                    institusjonsid = institutionid,
                    identifikator = foreldrePerson.norskIdent(),
                    land = "NO"
                )
            ),
            fornavn = navn?.fornavn,
            etternavnvedfoedsel = null //if (RelasjonEnum.MOR.erSamme(tpsvalue)) null else navntps.etternavn
        )
        return Foreldre(person = relasjonperson)
    }

    //persondata - nav-sed format
    private fun createPersonData(pdlperson: PDLPerson): Person {
        logger.debug("2.1           Persondata (forsikret person / gjenlevende person / barn)")

        val landKode = pdlperson.statsborgerskap
            .filterNot { it.gyldigFraOgMed == null }
            .maxBy { it.gyldigFraOgMed!! }?.land

        return Person(
                //2.1.1     familiy name
                etternavn = pdlperson.navn?.etternavn,
                //2.1.2     forname
                fornavn = createFornavnMellomNavn(pdlperson.navn),
                //2.1.3
                foedselsdato = pdlperson.foedseldato() ,
                //2.1.4     //sex
                kjoenn = pdlperson.kortKjonn(),
                //2.1.6
                fornavnvedfoedsel = null,
                //2.1.7
                pin = createPersonPinNorIdent(pdlperson, institutionid, institutionnavn),
                //2.2.1.1
                statsborgerskap = listOf(createStatsborgerskap(landKode)),
                //2.1.8.1           place of birth
                foedested = createFodested(pdlperson),
                //2.2.2 -   P2100 = 5.2.2.
                sivilstand = null
        )
    }

    private fun createFornavnMellomNavn(personnavn: Navn?): String? {
       return listOfNotNull(personnavn?.fornavn, personnavn?.mellomnavn)
           .joinToString(separator = " ")
    }

    private fun createEktefellePartner(ektefellpartnerbruker: Bruker?, ekteTypeValue: Sivilstandstype?): Ektefelle? {
        logger.debug("5.0           Utfylling av ektefelle")
        if (ektefellpartnerbruker == null) return null
        return Ektefelle(
                //type
                //5.1   -- 01 - ektefelle, 02, part i partnerskap, 3, samboer
                type = createEktefelleType(ekteTypeValue!!),
                //ektefelle (personobj kjører på nytt)
                person = ektefellpartnerbruker.person
        )
    }

    /**
     * Prefiller to-bokstavs statsborgerskap
     * Hopper over Kosovo (XXK) fordi Rina ikke støttet Kosovo
     */
    private fun createStatsborgerskap(landkode: String?): StatsborgerskapItem {
        logger.debug("2.2.1.1         Land / Statsborgerskap")
        if(UGYLDIGE_LAND_RINA.contains(landkode)){
            return StatsborgerskapItem()
        }
        return StatsborgerskapItem(prefillAdresse.hentLandkode(landkode))
    }
}