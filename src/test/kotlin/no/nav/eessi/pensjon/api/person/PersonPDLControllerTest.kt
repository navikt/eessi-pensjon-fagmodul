package no.nav.eessi.pensjon.api.person

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierlasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstand
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Statsborgerskap
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

@WebMvcTest(PersonPDLController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.person"])
@ActiveProfiles("unsecured-webmvctest")
class PersonPDLControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockBean
    lateinit var auditLogger: AuditLogger

    @MockBean
    lateinit var mockAktoerregisterService: AktoerregisterService

    @MockBean
    lateinit var mockPersonV3Service: PersonV3Service

    @MockBean
    lateinit var mockPensjonClient: PensjonsinformasjonClient

    @MockBean
    lateinit var pdlService: PersonService

    @Test
    fun `getPerson should return Person as json`() {

        doNothing().whenever(auditLogger).log(any(), any())
        doReturn(lagPerson(etternavn = "NORDMANN", fornavn = "OLA")).whenever(pdlService).hentPerson(any<Ident<*>>())


        val response = mvc.perform(
            get("/person/pdl/${Companion.AKTOERID}")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        JSONAssert.assertEquals(personResponsAsJson, response.contentAsString, true)
    }

    @Test
    fun `getNameOnly should return names as json`() {
        doNothing().whenever(auditLogger).log(any(), any())
        doReturn(lagPerson(etternavn = "NORDMANN", fornavn = "OLA")).whenever(pdlService).hentPerson(any<Ident<*>>())

        val response = mvc.perform(
            get("/person/pdl/info/${Companion.AKTOERID}")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        JSONAssert.assertEquals(namesAsJson, response.contentAsString, false)
    }

    @Test
    fun `should return NOT_FOUND hvis personen ikke finnes i TPS`() {
//        doThrow(PersonV3IkkeFunnetException("Error is Expected")).whenever(mockPersonV3Service).hentPersonResponse(anFnr)
        doNothing().whenever(auditLogger).log(any(), any())
//        whenever(mockAktoerregisterService.hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId(anAktorId))).thenReturn(NorskIdent(anFnr))

        doThrow(PersonoppslagException(HttpStatus.NOT_FOUND.name)).whenever(pdlService).hentPerson(any<Ident<*>>())

        mvc.perform(
            get("/person/pdl/info/${Companion.AKTOERID}")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PDL getDeceased should return a list of deceased parents given a remaining, living child`() {
        val aktoerId = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodMorfnr = "310233213123"
        val avdodFarfnr = "101020223123"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = avdodMorfnr
        mockPensjoninfo.avdod.avdodFar = avdodFarfnr
        mockPensjoninfo.person.aktorId = aktoerId

        val avdodMor = lagPerson(
            avdodMorfnr, "Fru", "Blyant",
            listOf(Familierlasjon(fnrGjenlevende, Familierelasjonsrolle.BARN, Familierelasjonsrolle.MOR)),
            listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2000, 10, 2), avdodFarfnr))
        )
        val avdodFar = lagPerson(
            avdodFarfnr, "Hr", "Blyant",
            listOf(Familierlasjon(fnrGjenlevende, Familierelasjonsrolle.BARN, Familierelasjonsrolle.FAR)),
            listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2000, 10, 2), avdodMorfnr))
        )

        val barn = lagPerson(
            fnrGjenlevende, "Liten", "Blyant",
            listOf(
                Familierlasjon(avdodFarfnr, Familierelasjonsrolle.FAR, Familierelasjonsrolle.BARN),
                Familierlasjon(avdodMorfnr, Familierelasjonsrolle.MOR, Familierelasjonsrolle.BARN)
            )
        )

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)

        doReturn(avdodMor).whenever(pdlService).hentPerson(NorskIdent(avdodMorfnr))
        doReturn(avdodFar).whenever(pdlService).hentPerson(NorskIdent(avdodFarfnr))
        doReturn(barn).whenever(pdlService).hentPerson(AktoerId(aktoerId))

        val response = mvc.perform(
            get("/person/pdl/$fnrGjenlevende/avdode/vedtak/$vedtaksId")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        println(response.contentAsString)
//        val actual = mapJsonToAny(response.contentAsString, typeRefs<List<PersonPDLController.PersoninformasjonAvdode>>())
//        val avdodFarResponse = actual.first()
//        val avdodMorResponse = actual.last()

//        assertEquals(avdodMorfnr, avdodMorResponse.fnr)
//        assertEquals(MOR.name, avdodMorResponse.relasjon)
//        assertEquals(avdodFarfnr, avdodFarResponse.fnr)
//        assertEquals(FAR.name, avdodFarResponse.relasjon)
    }

    @Test
    fun `getDeceased should return a list of one parent given a remaining, living child`() {
        val aktoerId = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodMorfnr = "310233213123"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = avdodMorfnr
        mockPensjoninfo.person.aktorId = aktoerId

        val relasjonMor = "MORA"
//        val avdodMorTPSBruker = lagTPSBruker(avdodMorfnr, "Stor", "Blyant")
//        val gjenlevendeBarnTSPBruker =
//            lagTPSBruker(fnrGjenlevende, "Liten", "Blyant").medVoksen(avdodMorfnr, relasjonMor)

        val avdodmor = lagPerson(avdodMorfnr, "Stor", "Blyant",
            listOf(Familierlasjon(fnrGjenlevende, Familierelasjonsrolle.BARN, Familierelasjonsrolle.MOR)))
        val barn = lagPerson(fnrGjenlevende, "Liten", "Blyant",
            listOf(Familierlasjon(avdodMorfnr, Familierelasjonsrolle.MOR, Familierelasjonsrolle.BARN)))

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)
//        doReturn(avdodMorTPSBruker).whenever(mockPersonV3Service).hentBruker(avdodMorfnr)
//        doReturn(NorskIdent(fnrGjenlevende)).whenever(mockAktoerregisterService).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())
//        doReturn(HentPersonResponse().withPerson(gjenlevendeBarnTSPBruker)).whenever(mockPersonV3Service).hentPersonResponse(fnrGjenlevende)

        doReturn(avdodmor).whenever(pdlService).hentPerson(NorskIdent(avdodMorfnr))
        doReturn(barn).whenever(pdlService).hentPerson(AktoerId(aktoerId))


        val response = mvc.perform(
            get("/person/pdl/$fnrGjenlevende/avdode/vedtak/$vedtaksId")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        println(response.contentAsString)
//        val actual =
//            mapJsonToAny(response.contentAsString, typeRefs<List<PersonPDLController.PersoninformasjonAvdode>>()).first()
//        assertTrue(actual.fnr == avdodMorfnr)
//        assertTrue(actual.relasjon == MOR.name)
    }


    @Test
    fun `getDeceased should return an empty list when both partents are alive`() {
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodMorfnr = "310233213123"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()

//        val gjenlevendeBarnTSPBruker = lagTPSBruker(fnrGjenlevende, "Liten", "Blyant").medVoksen(avdodMorfnr, "MOR")
        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)
//        doReturn(NorskIdent(fnrGjenlevende)).whenever(mockAktoerregisterService).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())
//        doReturn(HentPersonResponse().withPerson(gjenlevendeBarnTSPBruker)).whenever(mockPersonV3Service).hentPersonResponse(fnrGjenlevende)

        val response = mvc.perform(
            get("/person/$fnrGjenlevende/avdode/vedtak/$vedtaksId")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response
        val list: List<String> = mapJsonToAny(response.contentAsString, typeRefs())
        assert(list.isEmpty())
    }

    private val personResponsAsJson = """
        {
          "identer": [
            {
              "ident": "123456",
              "gruppe": "FOLKEREGISTERIDENT"
            }
          ],
          "navn": {
            "fornavn": "OLA",
            "mellomnavn": null,
            "etternavn": "NORDMANN",
            "sammensattNavn": "OLA NORDMANN"
          },
          "adressebeskyttelse": [],
          "bostedsadresse": null,
          "oppholdsadresse": null,
          "statsborgerskap": [
            {
              "land": "NOR",
              "gyldigFraOgMed": "2010-10-11",
              "gyldigTilOgMed": "2020-10-02"
            }
          ],
          "foedsel": null,
          "geografiskTilknytning": null,
          "kjoenn": {
            "kjoenn": "MANN",
            "folkeregistermetadata": {
              "gyldighetstidspunkt": "2000-10-01T12:10:31"
            }
          },
          "doedsfall": null,
          "familierelasjoner": [],
          "sivilstand": []
        }
    """.trimIndent()

    private val personAsJson = """{
                  "person": {
                    "diskresjonskode": null,
                    "bostedsadresse": null,
                    "sivilstand": null,
                    "statsborgerskap": null,
                    "harFraRolleI": [],
                    "aktoer": null,
                    "kjoenn": null,
                    "personnavn": { "fornavn": "OLA", "etternavn": "NORDMANN", "mellomnavn": null, "sammensattNavn": "NORDMANN OLA", "endringstidspunkt": null, "endretAv": null, "endringstype": null},
                    "personstatus": null,
                    "postadresse": null,
                    "doedsdato": null,
                    "foedselsdato": null
                  }
                }"""

    private val namesAsJson =
        """{ fornavn: "OLA", etternavn: "NORDMANN", mellomnavn: null, fulltNavn: "NORDMANN OLA"}"""


    private fun lagPerson(
        fnr: String = FNR,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        familierlasjon: List<Familierlasjon> = emptyList(),
        sivilstand: List<Sivilstand> = emptyList()
    ): Person {

        return Person(
            listOf(IdentInformasjon(fnr, IdentGruppe.FOLKEREGISTERIDENT)),
            Navn(fornavn = fornavn, etternavn = etternavn, mellomnavn = null),
            emptyList(),
            null,
            null,
            listOf(
                Statsborgerskap(
                    "NOR",
                    LocalDate.of(2010, 10, 11),
                    LocalDate.of(2020, 10, 2)
                )
            ),
            null,
            null,
            Kjoenn(
                KjoennType.MANN,
                Folkeregistermetadata(LocalDateTime.of(2000, 10, 1, 12, 10, 31))
            ),
            null,
            familierlasjon,
            sivilstand
        )
    }

    companion object {
        private const val AKTOERID = "012345"
        private const val FNR = "01010123456"

    }
}
