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
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
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
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@WebMvcTest(PersonPDLController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.person"])
@ActiveProfiles("unsecured-webmvctest")
class PersonPDLControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockBean
    lateinit var auditLogger: AuditLogger

    @MockBean
    private lateinit var personV3Service: PersonV3Service

    @MockBean
    private lateinit var aktoerregisterService: AktoerregisterService

    @MockBean
    lateinit var mockPensjonClient: PensjonsinformasjonClient

    @MockBean
    lateinit var pdlService: PersonService

    companion object {
        const val AKTOERID = "012345"

        const val FNR = "01010123456"

    }

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
    fun `should return NOT_FOUND hvis personen ikke finnes`() {
        doNothing().whenever(auditLogger).log(any(), any())
        doThrow(PersonoppslagException("not_found: Fant ikke person")).whenever(pdlService).hentPerson(any<Ident<*>>())

        mvc.perform(
            get("/person/pdl/info/${AKTOERID}")
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
            listOf(Familierelasjon(fnrGjenlevende, Familierelasjonsrolle.BARN, Familierelasjonsrolle.MOR)),
            listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2000, 10, 2), avdodFarfnr))
        )
        val avdodFar = lagPerson(
            avdodFarfnr, "Hr", "Blyant",
            listOf(Familierelasjon(fnrGjenlevende, Familierelasjonsrolle.BARN, Familierelasjonsrolle.FAR)),
            listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2000, 10, 2), avdodMorfnr))
        )

        val barn = lagPerson(
            fnrGjenlevende, "Liten", "Blyant",
            listOf(
                Familierelasjon(avdodFarfnr, Familierelasjonsrolle.FAR, Familierelasjonsrolle.BARN),
                Familierelasjon(avdodMorfnr, Familierelasjonsrolle.MOR, Familierelasjonsrolle.BARN)
            )
        )
        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)
        doReturn(avdodMor).whenever(pdlService).hentPerson(NorskIdent(avdodMorfnr))
        doReturn(avdodFar).whenever(pdlService).hentPerson(NorskIdent(avdodFarfnr))
        doReturn(barn).whenever(pdlService).hentPerson(AktoerId(aktoerId))

        val response = mvc.perform(
            get("/person/pdl/$aktoerId/avdode/vedtak/$vedtaksId")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        val actual = mapJsonToAny(response.contentAsString, typeRefs<List<PersonPDLController.PersoninformasjonAvdode>>())
        val avdodFarResponse = actual.first()
        val avdodMorResponse = actual.last()

        assertEquals(avdodMorfnr, avdodMorResponse.fnr)
        assertEquals(Familierelasjonsrolle.MOR.name, avdodMorResponse.relasjon)
        assertEquals(avdodFarfnr, avdodFarResponse.fnr)
        assertEquals(Familierelasjonsrolle.FAR.name, avdodFarResponse.relasjon)
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

        val avdodmor = lagPerson(avdodMorfnr, "Stor", "Blyant",
            listOf(Familierelasjon(fnrGjenlevende, Familierelasjonsrolle.BARN, Familierelasjonsrolle.MOR)))
        val barn = lagPerson(fnrGjenlevende, "Liten", "Blyant",
            listOf(Familierelasjon(avdodMorfnr, Familierelasjonsrolle.MOR, Familierelasjonsrolle.BARN)))

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)
        doReturn(avdodmor).whenever(pdlService).hentPerson(NorskIdent(avdodMorfnr))
        doReturn(barn).whenever(pdlService).hentPerson(AktoerId(aktoerId))

        val response = mvc.perform(
            get("/person/pdl/$aktoerId/avdode/vedtak/$vedtaksId")
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response


        print(response.contentAsString)
        val result = mapJsonToAny(response.contentAsString, typeRefs<List<PersonPDLController.PersoninformasjonAvdode?>>())

        assertEquals(1, result.size)
        val element = result.firstOrNull()
        assertEquals  (avdodMorfnr, element?.fnr)
        assertEquals (Familierelasjonsrolle.MOR.name, element?.relasjon)

    }


    @Test
    fun `getDeceased should return an empty list when both partents are alive`() {
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val aktoerId = "212342321312"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.person.aktorId = aktoerId

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)

        val barn = lagPerson(fnrGjenlevende, "Liten", "Blyant",
            listOf(Familierelasjon("231231231231", Familierelasjonsrolle.MOR, Familierelasjonsrolle.BARN)))
        doReturn(barn).whenever(pdlService).hentPerson(any<Ident<*>>())

        val response = mvc.perform(
            get("/person/pdl/$aktoerId/avdode/vedtak/$vedtaksId")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        val list: List<PersonPDLController.PersoninformasjonAvdode?> = mapJsonToAny(response.contentAsString, typeRefs())
        assertEquals(emptyList(), list)
    }

    private val personResponsAsJson = """
        {
          "identer": [
            {
              "ident": "01010123456",
              "gruppe": "FOLKEREGISTERIDENT"
            }
          ],
          "navn": {
            "fornavn": "OLA",
            "mellomnavn": null,
            "etternavn": "NORDMANN",
            "sammensattNavn": "OLA NORDMANN",
            "sammensattEtterNavn": "NORDMANN OLA"
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

    private val namesAsJson =  """{ fornavn: "OLA", etternavn: "NORDMANN", mellomnavn: null, fulltNavn: "NORDMANN OLA"}""".trimIndent()

    private fun lagPerson(
        fnr: String = FNR ,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        familierlasjon: List<Familierelasjon> = emptyList(),
        sivilstand: List<Sivilstand> = emptyList()
    ) = Person(
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
