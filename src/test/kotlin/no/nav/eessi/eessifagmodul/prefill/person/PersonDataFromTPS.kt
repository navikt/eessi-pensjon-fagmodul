package no.nav.eessi.eessifagmodul.prefill.person

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.EessiInformasjon
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillNav
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPersonDataFromTPS
import no.nav.eessi.eessifagmodul.services.LandkodeService
import no.nav.eessi.eessifagmodul.services.PostnummerService
import no.nav.eessi.eessifagmodul.services.personv3.PersonV3Service
import no.nav.eessi.eessifagmodul.utils.NavFodselsnummer
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.junit.Before
import org.mockito.Mock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ResourceUtils
import java.time.LocalDate

private val logger: Logger by lazy { LoggerFactory.getLogger(PersonDataFromTPS::class.java) }

abstract class PersonDataFromTPS(private val mocktps: Set<MockTPS>, private val eessiInformasjon: EessiInformasjon) {

    @Mock
    lateinit var mockPersonV3Service: PersonV3Service

    protected lateinit var preutfyllingTPS: PrefillPersonDataFromTPS

    @Mock
    protected lateinit var prefillNav: PrefillNav

    @Before
    fun setup() {
        preutfyllingTPS = mockPrefillPersonDataFromTPS()
        prefillNav = PrefillNav(preutfyllingTPS)

        prefillNav.institutionid = "NO:noinst002"
        prefillNav.institutionnavn = "NOINST002, NO INST002, NO"
    }


    private fun initMockHentPersonResponse(mockTPS: MockTPS, mockTPSset: Set<MockTPS>): HentPersonResponse {
        val resource = ResourceUtils.getFile("classpath:personv3/${mockTPS.mockFile}").readText()

        logger.info("Parsing TPS mockfile: ${mockTPS.mockFile}   Generated fnr:  ${mockTPS.replaceMockfnr}   type:  ${mockTPS.mockType} ")

        val mockBarnList = mutableListOf<MockTPS>()
        val mockEkteItem = mutableListOf<MockTPS>()
        mockTPSset.forEach {
            if (it.mockType == MockTPS.TPSType.BARN) {
                mockBarnList.add(it)
            }
            if (it.mockType == MockTPS.TPSType.EKTE) {
                mockEkteItem.add(it)
            }
        }
        logger.info("---------------------------------------------------------------------------------")

        val mapper = jacksonObjectMapper()
        val rootNode = mapper.readValue(resource, JsonNode::class.java)

        val bostedsadresse = rootNode["bostedsadresse"]
        val sivilstand = rootNode["sivilstand"]
        val personnavn = rootNode["personnavn"]
        val personstatus = rootNode["personstatus"]
        val statsborgerskap = rootNode["statsborgerskap"]
        val kjoenn = rootNode["kjoenn"]
        val aktoer = rootNode["aktoer"]
        val strukturertAdresse = bostedsadresse["strukturertAdresse"]
        val poststed = strukturertAdresse["poststed"]
        val landkode = strukturertAdresse["landkode"]
        val harFraRolleI = rootNode["harFraRolleI"]
        val foedselsdato = rootNode["foedselsdato"]


        val v3struktAdr = mapJsonToAny(bostedsadresse.toString(), typeRefs<Gateadresse>())
        val v3personstatus = mapJsonToAny(personstatus.toString(), typeRefs<Personstatus>())
        val v3statsborgerskap = mapJsonToAny(statsborgerskap.toString(), typeRefs<Statsborgerskap>())
        val v3pernavn = mapJsonToAny(personnavn.toString(), typeRefs<Personnavn>())
        val v3sivilstand = mapJsonToAny(sivilstand.toString(), typeRefs<Sivilstand>())
        val v3kjoenn = mapJsonToAny(kjoenn.toString(), typeRefs<Kjoenn>())
        val v3aktoer = mapJsonToAny(aktoer.toString(), typeRefs<PersonIdent>())
        val v3poststed = mapJsonToAny(poststed.toString(), typeRefs<Postnummer>())
        val v3landkode = mapJsonToAny(landkode.toString(), typeRefs<Landkoder>())
        val v3foedselsdato = mapJsonToAny(foedselsdato.toString(), typeRefs<Foedselsdato>())

        val v3person = Bruker()

        harFraRolleI.forEach {
            val tilRolle = it["tilRolle"]
            val tilPerson = it["tilPerson"]
            val kjoennitem = tilPerson["kjoenn"]
            val aktoeritem = tilPerson["aktoer"]
            val personnavnitem = tilPerson["personnavn"]

            val v3familieRelasjon = Familierelasjon()
            v3familieRelasjon.tilRolle = mapJsonToAny(tilRolle.toString(), typeRefs<Familierelasjoner>())

            v3familieRelasjon.tilPerson = Bruker()
            if (!kjoennitem.isNull) {
                v3familieRelasjon.tilPerson.kjoenn = mapJsonToAny(kjoennitem.toString(), typeRefs<Kjoenn>())
            }
            val familieIdent = mapJsonToAny(aktoeritem.toString(), typeRefs<PersonIdent>())

            if (v3familieRelasjon.tilRolle.value == "BARN") {
                for (i in mockBarnList) {
                    if (i.mockType == MockTPS.TPSType.BARN && i.used == false) {
                        familieIdent.ident.ident = i.replaceMockfnr
                        i.used = true
                        break
                    }
                }
            }
            if (v3familieRelasjon.tilRolle.value == "EKTE") {
                for (i in mockEkteItem) {
                    if (i.mockType == MockTPS.TPSType.EKTE && i.used == false)
                        familieIdent.ident.ident = i.replaceMockfnr
                    i.used = true
                    break
                }
            }
            v3familieRelasjon.tilPerson.aktoer = familieIdent

            v3familieRelasjon.tilPerson.personnavn = mapJsonToAny(personnavnitem.toString(), typeRefs<Personnavn>())
            v3person.harFraRolleI.add(v3familieRelasjon)
        }

        v3person.bostedsadresse = Bostedsadresse()
        v3person.bostedsadresse.strukturertAdresse = v3struktAdr // Gateadresse
        v3struktAdr.poststed = v3poststed
        v3struktAdr.landkode = v3landkode
        v3person.sivilstand = v3sivilstand // v3sivilstand
        v3person.personnavn = v3pernavn // v3pernavn
        v3person.personstatus = v3personstatus
        v3person.foedselsdato = v3foedselsdato
        v3person.statsborgerskap = v3statsborgerskap
        v3person.kjoenn = v3kjoenn
        v3person.aktoer = v3aktoer

        val ident = v3person.aktoer as PersonIdent
        ident.ident.ident = mockTPS.replaceMockfnr

        val navfnr = NavFodselsnummer(ident.ident.ident)
        logger.info("Person-Aldrer: ${navfnr.getAge()}")

        val v3PersonResponse = HentPersonResponse()
        v3PersonResponse.person = v3person

        return v3PersonResponse
    }

    fun mockPrefillPersonDataFromTPS(): PrefillPersonDataFromTPS {
        mocktps.forEach {
            val result = initMockHentPersonResponse(it, mocktps)
            whenever(mockPersonV3Service.hentPerson(it.replaceMockfnr)).thenReturn(result)
        }
        return PrefillPersonDataFromTPS(mockPersonV3Service, PostnummerService(), LandkodeService(), eessiInformasjon)
    }

    fun getRandomNavFodselsnummer(value: MockTPS.TPSType): String? {
        mocktps.forEach {
            if (it.mockType == MockTPS.TPSType.PERSON) {
                return it.replaceMockfnr
            }
        }
        return null
    }

    fun generatePrefillData(sedName: String = "P2000", pinId: String = getRandomNavFodselsnummer(MockTPS.TPSType.PERSON)!!): PrefillDataModel {
        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        return PrefillDataModel().apply {
            rinaSubject = "Pensjon"
            sed = SED.create(sedName)
            penSaksnummer = "12345"
            //vedtakId = "12312312"
            vedtakId = ""
            buc = "P_BUC_99"
            aktoerID = "123456789"
            personNr = pinId
            institution = items
        }
    }

    companion object {
        fun generateRandomFnr(fixedAlder: Int = 67): String {

            val fnrdate = LocalDate.now().minusYears(fixedAlder.toLong())

            val y = fnrdate.year.toString()
            val day = "01"
            val month = fixDigits(fnrdate.month.value.toString())
            val fixedyear = y.substring(2, y.length)

            val indivdnr = generateIndvididnr(fnrdate.year)

            val fnr = day + month + fixedyear + indivdnr + "52"

            val navfnr = NavFodselsnummer(fnr)
            logger.info("Generert fnr: ${navfnr.fnr()}  age: ${navfnr.getAge()}  birthday:  ${navfnr.get4DigitBirthYear()}  isunder18: ${navfnr.isUnder18Year()}")
            return fnr
        }

        private fun mockDnr(strFnr: String): String {
            val nvf = NavFodselsnummer(strFnr)
            val fdig = nvf.getFirstDigit()
            return when (fdig) {
                0 -> "4" + strFnr.substring(1, strFnr.length)
                1 -> "5" + strFnr.substring(1, strFnr.length)
                2 -> "6" + strFnr.substring(1, strFnr.length)
                3 -> "7" + strFnr.substring(1, strFnr.length)
                else -> strFnr
            }
        }

        private fun fixDigits(str: String): String {
            if (str.length == 1) {
                return "0$str"
            }
            return str
        }

        private fun generateIndvididnr(year: Int): String {
            return when (year) {
                in 1900..1999 -> "433"
                in 1940..1999 -> "954"
                in 2000..2039 -> "543"
                else -> "739"
            }
        }

    }


    data class MockTPS(
            val mockFile: String,
            val replaceMockfnr: String,
            val mockType: TPSType,
            var used: Boolean = false
    ) {
        enum class TPSType {
            PERSON,
            EKTE,
            BARN;
        }
    }

}