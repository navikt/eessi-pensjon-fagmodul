package no.nav.eessi.pensjon.api.pensjon

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonRequestBuilder
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.Pensjontype
import no.nav.eessi.pensjon.utils.toJson
import no.nav.pensjon.v1.brukerssakerliste.V1BrukersSakerListe
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate

class PensjonControllerTest {

    private var pensjonsinformasjonClient: PensjonsinformasjonClient = mockk()

    @SpyK
    private var auditLogger: AuditLogger = AuditLogger()

    @InjectMockKs
    private val controller = PensjonController(pensjonsinformasjonClient, auditLogger)

    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @BeforeEach
    fun setup() {
        controller.initMetrics()
    }

    @Test
    fun `hentPensjonSakType gitt en aktoerId saa slaa opp fnr og hent deretter sakstype`() {
        val aktoerId = "1234567890123" // 13 sifre
        val sakId = "Some sakId"

        every { pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId) } returns Pensjontype(sakId, "Type")

        controller.hentPensjonSakType(sakId, aktoerId)


        verify { pensjonsinformasjonClient.hentKunSakType(eq(sakId), eq(aktoerId)) }
    }


    @Test
    fun `hentPensjonSakType gitt at det svar fra PESYS er tom`() {
        val aktoerId = "1234567890123" // 13 sifre
        val sakId = "Some sakId"

        every { pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId) } returns Pensjontype(sakId, "")
        val response = controller.hentPensjonSakType(sakId, aktoerId)


        verify { pensjonsinformasjonClient.hentKunSakType(eq(sakId), eq(aktoerId)) }

        val expected = """
            {
              "sakId" : "Some sakId",
              "sakType" : ""
            }
        """.trimIndent()

        assertEquals(expected, response?.body)

    }

    @Test
    fun `hentPensjonSakType gitt at det svar feiler fra PESYS`() {
        val aktoerId = "1234567890123" // 13 sifre
        val sakId = "Some sakId"

        every { pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId) } returns Pensjontype(sakId, "")

        val response = controller.hentPensjonSakType(sakId, aktoerId)

        verify { pensjonsinformasjonClient.hentKunSakType(eq(sakId), eq(aktoerId)) }

        val expected = """
            {
              "sakId" : "Some sakId",
              "sakType" : ""
            }
        """.trimIndent()

        assertEquals(expected, response?.body)

    }

    @Test
    fun `Gitt det finnes pensjonsak på aktoer så skal det returneres en liste over alle saker til aktierid`() {
        val aktoerId = "1234567890123" // 13 sifre

        val mockpen = Pensjonsinformasjon()
        val mocksak1 = V1Sak()
        mocksak1.sakId = 1010
        mocksak1.status = "INNV"
        mocksak1.sakType = "ALDER"
        mockpen.brukersSakerListe = V1BrukersSakerListe()
        mockpen.brukersSakerListe.brukersSakerListe.add(mocksak1)
        val mocksak2 = V1Sak()
        mocksak2.sakId = 2020
        mocksak2.status = "AVSL"
        mocksak2.sakType = "UFOREP"
        mockpen.brukersSakerListe.brukersSakerListe.add(mocksak2)

        every { pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId) } returns mockpen

        val result = controller.hentPensjonSakIder(aktoerId)

        verify { pensjonsinformasjonClient.hentAltPaaAktoerId(eq(aktoerId)) }

        assertEquals(2, result.size)
        val expected1 = PensjonSak("1010", "ALDER", PensjonSakStatus.LOPENDE)
        assertEquals(expected1.toJson(), result.first().toJson())
        val expected2 = PensjonSak("2020", "UFOREP", PensjonSakStatus.AVSLUTTET)
        assertEquals(expected2.toJson(), result.last().toJson())

        assertEquals(PensjonSakStatus.AVSLUTTET, expected2.sakStatus)
    }

    @Test
    fun `Gitt det ikke finnes pensjonsak på aktoer så skal det returneres et tomt svar tom liste`() {
        val aktoerId = "1234567890123" // 13 sifre

        val mockpen = Pensjonsinformasjon()
        mockpen.brukersSakerListe = V1BrukersSakerListe()

        every { (pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId)) } returns mockpen

        val result = controller.hentPensjonSakIder(aktoerId)
        verify(exactly = 1) {
            pensjonsinformasjonClient.hentAltPaaAktoerId(any())

            assertEquals(0, result.size)
        }
    }

    @Test
    fun `sjekk på forskjellige verdier av sakstatus fra pensjoninformasjon konvertere de til enum`() {
        val tilbeh = "TIL_BEHANDLING"
        val avsl = "AVSL"
        val lop = "INNV"
        val opph = "OPPHOR"
        val ukjent = "CrazyIkkeIbrukTull"


        assertEquals(PensjonSakStatus.TIL_BEHANDLING, PensjonSakStatus.from(tilbeh))
        assertEquals(PensjonSakStatus.AVSLUTTET, PensjonSakStatus.from(avsl))
        assertEquals(PensjonSakStatus.LOPENDE, PensjonSakStatus.from(lop))
        assertEquals(PensjonSakStatus.OPPHOR, PensjonSakStatus.from(opph))
        assertEquals(PensjonSakStatus.UKJENT, PensjonSakStatus.from(ukjent))

    }

    @Test
    fun `hentKravDato skal gi en data hentet fra aktorid og vedtaksid `() {
        val kravDato = "2020-01-01"
        val aktoerId = "123"
        val saksId = "10000"
        val kravId = "12456"

        every { pensjonsinformasjonClient.hentKravDatoFraAktor(aktoerId, saksId, kravId) } returns kravDato

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/pensjon/kravdato/saker/$saksId/krav/$kravId/aktor/$aktoerId")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        print(response)

        assertEquals("""{ "kravDato": "$kravDato" }""", response)
    }

    @Test
    fun `hentKravDato skal gi 400 og feilmelding ved manglende parameter`() {
        val aktoerId = "123"
        val saksId = "10000"
        val kravId = ""

        mockMvc.perform(
            MockMvcRequestBuilders.get("/pensjon/kravdato/saker/$saksId/krav/$kravId/aktor/$aktoerId")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun uthentingAvUforeTidspunkt() {
        val mockVedtakid = "213123333"
        val mockClient = fraFil("VEDTAK-UT-MUTP.xml")
        val mockController = PensjonController(mockClient, auditLogger)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$mockVedtakid/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
            )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("2020-02-29", response)

    }

    @Test
    fun uthentingAvUforeTidspunktMedGMTZ() {
        val mockVedtakid = "213123333"
        val mockClient = fraFil("VEDTAK-UT-MUTP-GMTZ.xml")
        val mockController = PensjonController(mockClient, auditLogger)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$mockVedtakid/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("2020-03-01", response)
    }

    @Test
    fun uthentingAvUforeTidspunktSomErTom() {
        val mockVedtakid = "213123333"
        val mockClient = fraFil("VEDTAK-UT.xml")
        val mockController = PensjonController(mockClient, auditLogger)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$mockVedtakid/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals("", response)
    }



    @Test
    fun `Sjekke for hentKravDatoFraAktor ikke kaster en unormal feil`() {
        val aktoerId = "123"
        val saksId = "10000"
        val kravId = "345345"

        MDC.put("x_request_id","AAA-BBB")
        every { pensjonsinformasjonClient.hentKravDatoFraAktor(any(), any(), any()) } returns null

        val result = controller.hentKravDatoFraAktor(saksId, kravId, aktoerId)
        assertEquals("{\"success\": false, \n" + " \"error\": \"Feiler å hente kravDato\", \"uuid\": \"AAA-BBB\"}", result?.body)

    }


    fun fraFil(responseXMLfilename: String): PensjonsinformasjonClient {
        val resource = ResourceUtils.getFile("classpath:pensjonsinformasjon/$responseXMLfilename").readText()
        val readXMLresponse = ResponseEntity(resource, HttpStatus.OK)

        val mockRestTemplate: RestTemplate = mockk()

        every { mockRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } returns readXMLresponse
        val pensjonsinformasjonClient = PensjonsinformasjonClient(mockRestTemplate, PensjonRequestBuilder())
        pensjonsinformasjonClient.initMetrics()
        return pensjonsinformasjonClient
    }
}

