package no.nav.eessi.eessifagmodul.prefill.nav

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.models.Bruker
import no.nav.eessi.eessifagmodul.models.Person
import no.nav.eessi.eessifagmodul.prefill.EessiInformasjon
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.services.LandkodeService
import no.nav.eessi.eessifagmodul.services.PostnummerService
import no.nav.eessi.eessifagmodul.services.personv3.PersonV3Service
import no.nav.eessi.eessifagmodul.utils.NavFodselsnummer
import no.nav.eessi.eessifagmodul.utils.simpleFormat
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PrefillPersonDataFromTPS(private val personV3Service: PersonV3Service,
                               private val postnummerService: PostnummerService,
                               private val landkodeService: LandkodeService,
                               private val eessiInfo: EessiInformasjon) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPersonDataFromTPS::class.java) }
    private val dod = "DØD"

    private var personstatus = ""

    private enum class RelasjonEnum(val relasjon: String) {
        FAR("FARA"),
        MOR("MORA"),
        BARN("BARN");

        fun erSamme(relasjonTPS: String): Boolean {
            return relasjon == relasjonTPS
        }
    }

    fun prefillBruker(ident: String): Bruker {
        logger.debug("              Bruker")
        try {
            val brukerTPS = hentBrukerTPS(ident)
            setPersonStatus(hentPersonStatus(brukerTPS))

            return Bruker(
                    person = personData(brukerTPS),

                    far = hentRelasjon(RelasjonEnum.FAR, brukerTPS),

                    mor = hentRelasjon(RelasjonEnum.MOR, brukerTPS),

//                    far = Foreldre(person = hentRelasjon(RelasjonEnum.FAR, brukerTPS)),
//                    mor = Foreldre(person = hentRelasjon(RelasjonEnum.MOR, brukerTPS)),

                    adresse = hentPersonAdresse(brukerTPS)
            )
        } catch (ex: Exception) {
            logger.error("Feil ved henting av Bruker fra TPS, sjekk ident?")
            return Bruker()
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

    private fun setPersonStatus(status: String = "") {
        this.personstatus = status
    }

    private fun getPersonStatus(): String {
        return this.personstatus
    }

    private fun validatePersonStatus(value: String): Boolean {
        return getPersonStatus() == value
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
        return fdato?.foedselsdato?.simpleFormat()
    }

    //doddato i rina P2100?
    private fun dodDatoFormat(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String? {
        logger.debug("4.2       Date of death / dødsdato P2100")
        val doddato = person.doedsdato
        return doddato?.doedsdato?.simpleFormat()
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
                                        //land = eessiInfo.institutionLand,

                                        sektor = "pensjon",
                                        identifikator = hentNorIdent(persontps),
                                        land = hentLandkodeRelasjoner(persontps)
                                )
                        ),
                        fornavn = navntps.fornavn,
                        etternavnvedfoedsel = navntps.etternavn
                        //doedsdato = dodDatoFormat(persontps)
                )
                if (RelasjonEnum.MOR.erSamme(tpsvalue)) {
                    relasjonperson.etternavnvedfoedsel = null
                }
                return Foreldre(person = relasjonperson)
                //return relasjonperson
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

                        //land = eessiInfo.institutionLand,

                        //harkode sektor penjson? er dette korrekt?
                        sektor = "pensjon",

                        //personnr
                        identifikator = hentNorIdent(brukerTps),

                        // norsk personnr alltid NO
                        //land = "NO"
                        land = hentLandkodeRelasjoner(brukerTps)
                )
        )
    }

    //hjelpe funkson for personstatus.
    private fun hentPersonStatus(brukerTps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): String {
        val personstatus = brukerTps.personstatus as Personstatus
        return personstatus.personstatus.value
    }

    //Sivilstand ENKE, PENS, SINGLE Familiestatus
    private fun hentSivilstand(brukerTps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): List<SivilstandItem> {
        logger.debug("2.2.2           Sivilstand / Familiestatus (01 Enslig, 02 Gift, 03 Samboer, 04 Partnerskal, 05 Skilt, 06 Skilt partner, 07 Separert, 08 Enke)")
        val sivilstand = brukerTps.sivilstand as Sivilstand

        //val status = mapOf<String, String>("01" to "UGIF", "02" to "GIFT", "03" to "SAMB", "04" to "REPA", "05" to "SKIL", "06" to "SKPA", "07" to "SEPA", "08" to "ENKE")
        val status = mapOf<String, String>("GIFT" to "02", "REPA" to "04", "ENKE" to "08", "SAMB" to "03", "SEPA" to "07", "UGIF" to "01", "SKIL" to "05", "SKPA" to "06")

        return listOf(SivilstandItem(
                //fradato = standardDatoformat(sivilstand.fomGyldighetsperiode),
                fradato = sivilstand.fomGyldighetsperiode.simpleFormat(),
                status = status.get(sivilstand.sivilstand.value)
        ))
    }


    //2.2.2 adresse informasjon
    fun hentPersonAdresse(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): Adresse? {
        logger.debug("2.2.2         Adresse")

        //ikke adresse for død
        if (validatePersonStatus(dod)) {
            logger.debug("           Person er avdod (ingen adresse å hente).")
            return null
        }

        //Gateadresse eller UstrukturertAdresse
        val bostedsadresse: Bostedsadresse = person.bostedsadresse ?: return hentPersonAdresseUstrukturert(person.postadresse)

        val gateAdresse = bostedsadresse.strukturertAdresse as Gateadresse
        val gate = gateAdresse.gatenavn
        val husnr = gateAdresse.husnummer
        return Adresse(
                postnummer = gateAdresse.poststed.value,
                gate = "$gate $husnr",
                land = hentLandkode(gateAdresse.landkode),
                by = postnummerService.finnPoststed(gateAdresse.poststed.value)
        )
    }

    //2.2.2 ustrukturert
    private fun hentPersonAdresseUstrukturert(postadr: no.nav.tjeneste.virksomhet.person.v3.informasjon.Postadresse): Adresse {
        logger.debug("             UstrukturertAdresse (utland)")
        val gateAdresse = postadr.ustrukturertAdresse as UstrukturertAdresse

        //returnerer nå en bank adresse dersom det finnes en ustrukturertAdresse hos borger.
        //dette må så endres/rettes av saksbehendlaer i rina?
        return Adresse(
                gate = "",
                bygning = "",
                by = "",
                postnummer = "",
                land = ""
        )

//        try {
//
//                    gate = gateAdresse.adresselinje1 ?: null,
//                    bygning = gateAdresse?.adresselinje2 ?: null,
//                    by = gateAdresse?.adresselinje3 ?: "",
//                    postnummer = gateAdresse?.adresselinje4 ?: null,
//                    land = hentLandkode(gateAdresse.landkode)
//            )
//        } catch (ex: Exception) {
//            logger.error("Feiler ved mapping av Ustrukturert Adresse.")
//            return Adresse()
//        }

    }

    //knytes til nasjonalitet for utfylling P2x00
    private fun hentStatsborgerskapTps(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): StatsborgerskapItem {
        logger.debug("2.2.1.1         Land / Statsborgerskap")

        val statsborgerskap = person.statsborgerskap as Statsborgerskap
        val land = statsborgerskap.land as no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder

        return StatsborgerskapItem(
                land = hentLandkode(land)
        )
    }

    private fun hentLandkodeRelasjoner(persontps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String? {
        if (persontps.statsborgerskap != null && persontps.statsborgerskap.land != null) {
            return hentLandkode(persontps.statsborgerskap.land)
        }
        return null
    }

    //TODO: Mapping av landkoder skal gjøres i codemapping i EUX
    private fun hentLandkode(landkodertps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder): String? {
        return landkodeService.finnLandkode2(landkodertps.value)
    }

    //TODO: Mapping av kjønn skal defineres i codemapping i EUX
    private fun mapKjonn(kjonn: Kjoenn): String {
        logger.debug("2.1.4         Kjønn")
        val ktyper = kjonn.kjoenn
//        val map: Map<String, String> = hashMapOf("M" to "m", "K" to "f")
//        val value = map[ktyper.value]
//        return value ?: "u"
        return ktyper.value
    }


}