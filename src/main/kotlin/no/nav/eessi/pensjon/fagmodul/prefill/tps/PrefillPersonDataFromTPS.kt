package no.nav.eessi.pensjon.fagmodul.prefill.tps

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PrefillPersonDataFromTPS(private val personV3Service: PersonV3Service,
                               private val prefillAdresse: PrefillAdresse,
                               private val eessiInfo: EessiInformasjon) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPersonDataFromTPS::class.java) }

    private enum class RelasjonEnum(val relasjon: String) {
        FAR("FARA"),
        MOR("MORA"),
        BARN("BARN");

        fun erSamme(relasjonTPS: String): Boolean {
            return relasjon == relasjonTPS
        }
    }

    fun isPersonAvdod(personTPS: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person) : Boolean {
        val personstatus = hentPersonStatus(personTPS)

        if (personstatus == "DØD") {
            logger.debug("Person er avdod (ingen adresse å hente).")
            return true
        }
        return false
    }

    fun prefillBruker(ident: String, bank: Bank? = null, ansettelsesforhold: List<ArbeidsforholdItem>? = null): Bruker {
        logger.debug("              Bruker")
        return try {
            val brukerTPS = hentBrukerTPS(ident)
            var adresse: Adresse? = null

            if(isPersonAvdod(brukerTPS))
                adresse = prefillAdresse.hentPersonAdresse(brukerTPS)

            Bruker(
                    person = personData(brukerTPS),
                    far = hentRelasjon(RelasjonEnum.FAR, brukerTPS),
                    mor = hentRelasjon(RelasjonEnum.MOR, brukerTPS),
                    adresse = adresse,
                    bank = bank,
                    arbeidsforhold = ansettelsesforhold
            )
        } catch (ex: Exception) {
            logger.error("Feil ved henting av Bruker fra TPS, sjekk ident?")
            Bruker()
        }

    }

    //henter kun personnNr (brukerNorIdent/pin) for alle barn under person
    fun hentBarnaPinIdFraBruker(brukerNorIdent: String): List<String> {
        //brukerPin henter ut persondetalj fra TPS. med liste over barna
        try {
            val brukerTPS = hentBrukerTPS(brukerNorIdent)

            val person = brukerTPS as no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
            val resultat = mutableListOf<String>()

            person.harFraRolleI.forEach {
                val tpsvalue = it.tilRolle.value   //mulig nullpoint? kan tilRolle være null?
                if (RelasjonEnum.BARN.erSamme(tpsvalue)) {
                    val persontps = it.tilPerson as no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
                    val norIdent = hentNorIdent(persontps)
                    if (NavFodselsnummer(norIdent).validate()) {
                        resultat.add(norIdent)
                    } else {
                        logger.error("følgende ident funnet ikke gyldig: $norIdent")
                    }
                }
            }
            return resultat.toList()
        } catch (ex: Exception) {
            logger.error("feiler ved henting av TPS")
            return listOf()
        }
    }

    fun hentEktefelleEllerPartnerFraBruker(utfyllingData: PrefillDataModel): Ektefelle? {
        val fnr = utfyllingData.personNr
        val bruker = hentBrukerTPS(fnr)

        var ektepinid = ""
        var ekteTypeValue = ""

        bruker.harFraRolleI.forEach {
            if (it.tilRolle.value == "EKTE") {

                ekteTypeValue = it.tilRolle.value
                val tilperson = it.tilPerson
                val pident = tilperson.aktoer as PersonIdent

                ektepinid = pident.ident.ident
                if (ektepinid.isNotBlank()) {
                    return@forEach
                }
            }
        }
        if (ektepinid.isBlank()) return null

        //hente ut og genere en bruker ut i fra ektefelle/partner fnr
        val ektefellpartnerbruker = prefillBruker(ektepinid)

        return Ektefelle(
                //type
                //5.1   -- 01 - ektefelle, 02, part i partnerskap, 3, samboer
                type = createEktefelleType(ekteTypeValue),
                //ektefelle (personobj kjører på nytt)
                person = ektefellpartnerbruker.person,
                //foreldre
                far = ektefellpartnerbruker.far,
                //foreldre
                mor = ektefellpartnerbruker.mor
        )
    }

    private fun createEktefelleType(typevalue: String): String {
        logger.debug("5.1           Ektefelle/Partnerskap-type")
        return when (typevalue) {
            "EKTE" -> "01"
            "PART" -> "02"
            else -> "03"
        }
    }

    //bruker fra TPS
    private fun hentBrukerTPS(ident: String): no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker {
        val response = personV3Service.hentPerson(ident)
        return response.person as no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
    }

    //personnr fnr
    private fun hentNorIdent(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String {
        logger.debug("2.1.7.1.2         Personal Identification Number (PIN) personnr")
        val persident = person.aktoer as PersonIdent
        val pinid: NorskIdent = persident.ident
        return pinid.ident
    }

    //fdato i rinaformat
    private fun datoFormat(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String? {
        logger.debug("2.1.3         Date of birth")
        val fdato = person.foedselsdato
        logger.debug("              Date of birth: $fdato")
        return fdato?.foedselsdato?.simpleFormat()
    }

    fun hentFodested(bruker: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): Foedested? {
        logger.debug("2.1.8.1       Fødested")

        val fsted = Foedested(
                land = bruker.foedested ?: "Unknown",
                by = "Unkown",
                region = ""
        )
        if (fsted.land == "Unknown") {
            return null
        }
        return fsted
    }

    //mor / far
    private fun hentRelasjon(relasjon: RelasjonEnum, person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): Foreldre? {
        person.harFraRolleI.forEach {
            val tpsvalue = it.tilRolle.value

            if (relasjon.erSamme(tpsvalue)) {
                logger.debug("              Relasjon til : $tpsvalue")
                val persontps = it.tilPerson as no.nav.tjeneste.virksomhet.person.v3.informasjon.Person

                val navntps = persontps.personnavn as Personnavn
                val relasjonperson = Person(
                        pin = listOf(
                                PinItem(
                                        institusjonsnavn = eessiInfo.institutionnavn,
                                        institusjonsid = eessiInfo.institutionid,
                                        identifikator = hentNorIdent(persontps),
                                        land = "NO"
                                )
                        ),
                        fornavn = navntps.fornavn,
                        etternavnvedfoedsel = if (RelasjonEnum.MOR.erSamme(tpsvalue)) null else navntps.etternavn
                )
                return Foreldre(person = relasjonperson)
            }
        }
        return null
    }

    //persondata - nav-sed format
    private fun personData(brukerTps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): Person {
        logger.debug("2.1           Persondata (forsikret person / gjenlevende person / barn)")

        val navn = brukerTps.personnavn as Personnavn
        val kjonn = brukerTps.kjoenn

        return Person(
                //2.1.1     familiy name
                etternavn = navn.etternavn,
                //2.1.2     forname
                fornavn = navn.fornavn,
                //2.1.3
                foedselsdato = datoFormat(brukerTps),
                //2.1.4     //sex
                kjoenn = mapKjonn(kjonn),
                //2.1.6
                fornavnvedfoedsel = navn.fornavn,
                //2.1.7
                pin = hentPersonPinNorIdent(brukerTps),
                //2.2.1.1
                statsborgerskap = listOf(hentStatsborgerskapTps(brukerTps)),
                //2.1.8.1           place of birth
                foedested = hentFodested(brukerTps),
                //2.2.2
                sivilstand = hentSivilstand(brukerTps)
        )
    }

    private fun hentPersonPinNorIdent(brukerTps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): List<PinItem> {
        logger.debug("2.1.7         Fodselsnummer/Personnummer")
        return listOf(
                PinItem(
                        //hentet lokal NAV insitusjondata fra applikasjon properties.
                        institusjonsnavn = eessiInfo.institutionnavn,
                        institusjonsid = eessiInfo.institutionid,

                        //NAV/Norge benytter ikke seg av sektor, setter denne til null

                        //personnr
                        identifikator = hentNorIdent(brukerTps),

                        // norsk personnr settes alltid til NO da vi henter NorIdent
                        land = "NO"
                )
        )
    }

    //hjelpe funkson for personstatus.
    private fun hentPersonStatus(personTPS: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String? {
        return personTPS.personstatus?.personstatus?.value
    }

    //Sivilstand ENKE, PENS, SINGLE Familiestatus
    fun hentSivilstand(brukerTps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): List<SivilstandItem> {
        logger.debug("2.2.2           Sivilstand / Familiestatus (01 Enslig, 02 Gift, 03 Samboer, 04 Partnerskal, 05 Skilt, 06 Skilt partner, 07 Separert, 08 Enke)")
        val sivilstand = brukerTps.sivilstand as Sivilstand

        val status = mapOf("GIFT" to "02", "REPA" to "04", "ENKE" to "08", "SAMB" to "03", "SEPA" to "07", "UGIF" to "01", "SKIL" to "05", "SKPA" to "06")

        return listOf(SivilstandItem(
                fradato = sivilstand.fomGyldighetsperiode.simpleFormat(),
                status = status[sivilstand.sivilstand.value]
        ))
    }

    //knytes til nasjonalitet for utfylling P2x00
    private fun hentStatsborgerskapTps(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): StatsborgerskapItem {
        logger.debug("2.2.1.1         Land / Statsborgerskap")

        val statsborgerskap = person.statsborgerskap as Statsborgerskap
        val land = statsborgerskap.land as Landkoder

        return StatsborgerskapItem(
                land = prefillAdresse.hentLandkode(land)
        )
    }

    //TODO: Mapping av kjønn skal defineres i codemapping i EUX
    private fun mapKjonn(kjonn: Kjoenn): String {
        logger.debug("2.1.4         Kjønn")
        val ktyper = kjonn.kjoenn
        return ktyper.value
    }
}