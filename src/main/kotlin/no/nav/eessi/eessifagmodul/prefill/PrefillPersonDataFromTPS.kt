package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.clients.personv3.PersonV3Client
import no.nav.eessi.eessifagmodul.services.LandkodeService
import no.nav.eessi.eessifagmodul.services.PostnummerService
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.models.Bruker
import no.nav.eessi.eessifagmodul.models.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.math.log

@Service
class PrefillPersonDataFromTPS(private val personV3Client: PersonV3Client, private val postnummerService: PostnummerService, private val landkoder: LandkodeService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPersonDataFromTPS::class.java) }
    private val dateformat = "YYYY-MM-dd"

    private enum class RelasjonEnum(val relasjon: String) {
        FAR("FARA"),
        MOR("MORA"),
        BARN("BARN");
        fun erSamme(relasjonTPS: String): Boolean {
            return relasjon.equals(relasjonTPS)
        }
    }

    fun hentBarnaPinIdFraBruker(ident: String): List<String> {
        val brukerTPS = hentBrukerTPS(ident)
        val person = brukerTPS
        val resultat = mutableListOf<String>()
        person.harFraRolleI.forEach {
            val tpsvalue = it.tilRolle.value
            if (RelasjonEnum.BARN.erSamme(tpsvalue)) {
                val persontps = it.tilPerson as no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
                //val statsborgerskap = persontps.statsborgerskap as Statsborgerskap
                //val navntps = persontps.personnavn as Personnavn
                resultat.add(hentNorIdent(persontps))
                logger.debug("Preutfylling barn")
            }
        }
        return resultat.toList()
    }

    fun prefillBruker(ident: String): Bruker {

        val brukerTPS = hentBrukerTPS(ident)

        val bruker = Bruker(
            far = Foreldre( person =  hentRelasjon(RelasjonEnum.FAR, brukerTPS)),
            mor = Foreldre( person =  hentRelasjon(RelasjonEnum.MOR, brukerTPS)),
            person = personData(brukerTPS),
            adresse = personAdresse(brukerTPS)

        )
        logger.debug("Preutfylling Bruker")
        return bruker
    }

    //bruker fra TPS
    private fun hentBrukerTPS(ident: String): no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker {
        val response =  personV3Client.hentPerson(ident)
        logger.debug("henter persondata fra TPS")
        return response.person as no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
    }

    //personnr
    private fun hentNorIdent(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String {
        val persident = person.aktoer as PersonIdent
        val pinid: NorskIdent = persident.ident
        return pinid.ident
    }

    //fdato i rinaformat
    private fun standardDatoformat(xmldato: XMLGregorianCalendar): String {
        val fodCalendar = xmldato.toGregorianCalendar()
        val datoformatert = SimpleDateFormat(dateformat).format(fodCalendar.time)
        return datoformatert
    }
    //fdato i rinaformat
    private fun datoFormat(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String {
        val fdato = person.foedselsdato
        return standardDatoformat(fdato.foedselsdato)
    }
    //doddato i rina
    private fun dodDatoFormat(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): String? {
        val doddato = person.doedsdato
        if (doddato == null) {
            return null
        }
        return standardDatoformat(doddato.doedsdato)
    }

    fun hentFodested(bruker: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): Foedested {
        val fstedTPS = bruker.foedested
        val fsted = Foedested(
                land = fstedTPS ?: "Unknown",
                by = "Unkown",
                region = "Unknown"
        )
        if (fsted.land == "Unknown") {
            return Foedested()
        }

        return fsted
    }

    //mor / far
    private fun hentRelasjon(relasjon: RelasjonEnum, person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): Person? {
        person.harFraRolleI.forEach {
            val tpsvalue = it.tilRolle.value
            if (relasjon.erSamme(tpsvalue)) {
                logger.debug("Finner relasjon til : $tpsvalue")
                val persontps = it.tilPerson as no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
                val land : String? = try {
                    if ( persontps.statsborgerskap != null ) {
                        val statsborgerskap = persontps.statsborgerskap as Statsborgerskap
                        hentLandkode(statsborgerskap.land)
                    } else {
                        null
                    }
                } catch (ex: NullPointerException) {
                    logger.debug(ex.message)
                    null
                }
                logger.debug("har vi hentet ut land rett: $land")
                val navntps = persontps.personnavn as Personnavn
                val relasjonperson = Person(
                    pin = listOf(
                        PinItem(
                            sektor = "alle",
                            identifikator = hentNorIdent(persontps),
                            land = land
                            )
                    ),
                    fornavn = navntps.fornavn,
                    etternavnvedfoedsel = navntps.etternavn,
                    doedsdato = dodDatoFormat(persontps)
                )
                if (RelasjonEnum.MOR.erSamme(tpsvalue)) {
                    relasjonperson.etternavnvedfoedsel = null
                }
                logger.debug("Preutfylling Foreldre")
                return relasjonperson
            }
        }
        return null
    }

    //persondata - rina format
    private fun personData(brukerTps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): Person {
        val navn = brukerTps.personnavn as Personnavn
        val statsborgerskap = brukerTps.statsborgerskap as Statsborgerskap
        val kjonn = brukerTps.kjoenn

        val person = Person(
                pin = listOf(
                        PinItem(
                        sektor = "alle",
                        identifikator = hentNorIdent(brukerTps),
                        //er det rikitg å sette uk her dersomn person er uk stats.. men pinid er norsk 11personnr?
                        land = hentLandkode(statsborgerskap.land)
                        )
                    ),
                forrnavnvedfoedsel = navn.fornavn,
                fornavn = navn.fornavn,
                etternavn = navn.etternavn,
                foedselsdato = datoFormat(brukerTps),
                statsborgerskap = listOf(statsBorgerskap(brukerTps)),
                kjoenn = mapKjonn(kjonn),
                foedested = hentFodested(brukerTps),
                sivilstand = hentSivilstand(brukerTps)
        )
        logger.debug("Preutfylling Person")
        return person
    }

    private fun hentSivilstand(brukerTps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker): List<SivilstandItem> {
        val sivilstand = brukerTps.sivilstand as Sivilstand
       val sivil = SivilstandItem(
                fradato = standardDatoformat( sivilstand.fomGyldighetsperiode ),
                status = sivilstand.sivilstand.value
        )
        return listOf(sivil)
    }


    fun personAdresse(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): Adresse{
        val gateAdresse = person.bostedsadresse.strukturertAdresse as Gateadresse
        val postnr = gateAdresse.poststed.value
        val adr = Adresse(
            postnummer = postnr,
            region = gateAdresse.kommunenummer,
            gate = gateAdresse.gatenavn,
            bygning = gateAdresse.husnummer.toString(),
            land = hentLandkode(gateAdresse.landkode),
            by = postnummerService.finnPoststed(postnr)
        )
        logger.debug("Preutfylling by(sted) benytter finnPoststed")
        logger.debug("Preutfylling Gateadresse")
        return adr
    }

    private fun statsBorgerskap(person: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): StatsborgerskapItem {
        val statsborgerskap = person.statsborgerskap as Statsborgerskap
        val land = statsborgerskap.land as no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder
        val statitem = StatsborgerskapItem(
            land = hentLandkode(land)
        )
        logger.debug("Preutfylling Statsborgerskap")
        return statitem
    }

    //Denne blir vel flyttet til Basis når mapping blir rettet opp fra NO=NO til NOR=NO (TPS/EU-RINA)??
    private fun hentLandkode(landkodertps: no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder): String? {
        val result = landkoder.finnLandkode2(landkodertps.value)
        logger.debug("Preutfylling mapping Landkode (alpha3-alpha2)  ${landkodertps.value} til $result")
        return result
    }

    //Midlertidige - mapping i Basis vil bli rettet slik at det sammkjører mot tps mapping.
    //Midlertidig funksjon for map TPS til EUX/Rina
    private fun mapKjonn(kjonn: Kjoenn): String {
        val ktyper = kjonn.kjoenn
        val map: Map<String, String> = hashMapOf("M" to "m", "K" to "f")
        val value = map[ktyper.value]
        return  value ?: "u"
    }



}