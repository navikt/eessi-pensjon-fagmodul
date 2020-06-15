package no.nav.eessi.pensjon.api.person

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
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


@WebMvcTest(PersonController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.person"])
@ActiveProfiles("unsecured-webmvctest")
class PersonControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockBean
    lateinit var mockAktoerregisterService: AktoerregisterService

    @MockBean
    lateinit var mockPersonV3Service: PersonV3Service

    @MockBean
    lateinit var auditLogger: AuditLogger

    @Test
    fun `getPerson should return Person as json`() {

        doNothing().whenever(auditLogger).log(any(), any())
        whenever(mockAktoerregisterService.hentGjeldendeNorskIdentForAktorId(anAktorId)).thenReturn(anFnr)
        whenever(mockPersonV3Service.hentPerson(anFnr)).thenReturn(hentPersonResponse)

        val response = mvc.perform(
            get("/person/$anAktorId")
                .accept(MediaType.APPLICATION_JSON))
            .andReturn().response

        JSONAssert.assertEquals(personAsJson, response.contentAsString, false)
    }

    @Test
    fun `getNameOnly should return names as json`() {
        doNothing().whenever(auditLogger).log(any(), any())
        whenever(mockAktoerregisterService.hentGjeldendeNorskIdentForAktorId(anAktorId)).thenReturn(anFnr)
        whenever(mockPersonV3Service.hentPerson(anFnr)).thenReturn(hentPersonResponse)

        val response = mvc.perform(
            get("/personinfo/$anAktorId")
                .accept(MediaType.APPLICATION_JSON))
            .andReturn().response

        JSONAssert.assertEquals(namesAsJson, response.contentAsString, false)
    }

    @Test
    fun `should return NOT_FOUND hvis personen ikke finnes i TPS`() {
        doThrow(PersonV3IkkeFunnetException("Error is Expected")).whenever(mockPersonV3Service).hentPerson(anFnr)
        doNothing().whenever(auditLogger).log(any(), any())
        doReturn(anFnr).whenever(mockAktoerregisterService).hentGjeldendeNorskIdentForAktorId(anAktorId)

        mvc.perform(
            get("/personinfo/$anAktorId")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound)
    }

    private val anAktorId = "012345"
    private val anFnr = "01010123456"

    private val hentPersonResponse =
        HentPersonResponse()
            .withPerson(Person()
                .withPersonnavn(Personnavn()
                    .withFornavn("OLA")
                    .withEtternavn("NORDMANN")
                    .withSammensattNavn("NORDMANN OLA")))

    private val personAsJson = """{
                  "person": {
                    "diskresjonskode": null,
                    "bostedsadresse": null,
                    "sivilstand": null,
                    "statsborgerskap": null,
                    "harFraRolleI": [],
                    "aktoer": null,
                    "kjoenn": null,
                    "personnavn": { fornavn: "OLA", etternavn: "NORDMANN", mellomnavn: null, sammensattNavn: "NORDMANN OLA"},
                    "personstatus": null,
                    "postadresse": null,
                    "doedsdato": null,
                    "foedselsdato": null
                  }
                }"""

    private val namesAsJson = """{ fornavn: "OLA", etternavn: "NORDMANN", mellomnavn: null, fulltNavn: "NORDMANN OLA"}"""
}
