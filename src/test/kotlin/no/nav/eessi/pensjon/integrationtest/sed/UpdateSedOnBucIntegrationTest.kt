package no.nav.eessi.pensjon.integrationtest.sed

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.every
import jakarta.servlet.ServletException
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.*
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.nio.charset.Charset

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
@EmbeddedKafka
@MockkBeans(
    MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "kodeverkRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "prefillOAuthTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxSystemRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class]),
    MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pensjoninformasjonRestTemplate", classes = [RestTemplate::class])
)
class UpdateSedOnBucIntegrationTest {

    @MockkBean(name = "euxNavIdentRestTemplate")
    private lateinit var restTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @Autowired
    private lateinit var mockMvc: MockMvc
    private lateinit var server: MockRestServiceServer

    private companion object {
        const val euxCaseId = "131231234"
        const val documentId = "12312j3g12jh3g12kj3g12kj3g12k3gh123k1g23"
    }

    @Test
    fun `oppdate sed P5000 on buc result in true when all OK`() {
        val jsonsed = javaClass.getResource("/json/nav/P5000-NAV.json")?.readText()!!

        /////cpi/buc/{RinaSakId}/sed/{DokumentId}
        every { restTemplate.exchange(
            eq("/buc/$euxCaseId/sed/$documentId?ventePaAksjon=false"),
            eq(HttpMethod.PUT),
            any(),
            eq(String::class.java)) } returns ResponseEntity("", HttpStatus.OK)

        val result = mockMvc.perform(put("/sed/put/$euxCaseId/$documentId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonsed))
            .andExpect(status().isOk)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals(true, response.toBoolean())

   }

    @Test
    fun `oppdate sed P5000 on buc results in false when eux throws an UNAUTHORIZED Exception`() {

        val jsonsed = javaClass.getResource("/json/nav/P5000-NAV.json")?.readText()!!
        //cpi/buc/{RinaSakId}/sed/{DokumentId}

        every { restTemplate.exchange(
            eq("/buc/$euxCaseId/sed/$documentId?ventePaAksjon=false"),
            eq(HttpMethod.PUT),
            any(),
            eq(String::class.java)) } throws createDummyClientRestExecption(HttpStatus.UNAUTHORIZED, "Unauthorized")

        val expectedError = """Authorization token required for Rina.""".trimIndent()
        assertThrows<ServletException> {
            mockMvc.perform(
                put("/sed/put/$euxCaseId/$documentId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonsed))
                .andExpect(status().is4xxClientError)
                .andExpect(status().reason(Matchers.containsString(expectedError)))
        }
    }

    @Test
    fun `oppdate sed P5000 empty medlemskap and gydligperiode 0 true when all OK`() {
        val jsonsed = javaClass.getResource("/json/nav/P5000-tomperioder-NAV.json").readText()

        /////cpi/buc/{RinaSakId}/sed/{DokumentId}
        every { restTemplate.exchange(
            eq("/buc/$euxCaseId/sed/$documentId?ventePaAksjon=false"),
            eq(HttpMethod.PUT),
            any(),
            eq(String::class.java)) } returns ResponseEntity("", HttpStatus.OK)

        val result = mockMvc.perform(put("/sed/put/$euxCaseId/$documentId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonsed))
            .andExpect(status().isOk)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals(true, response.toBoolean())
    }

    @Test
    fun `oppdate sed P5000 empty medlemskap test2 and gydligperiode 0 true when all OK`() {
        val jsonsed = javaClass.getResource("/json/nav/P5000-tomperioder2-NAV.json").readText()

        /////cpi/buc/{RinaSakId}/sed/{DokumentId}
        every { restTemplate.exchange(
            eq("/buc/$euxCaseId/sed/$documentId?ventePaAksjon=false"),
            eq(HttpMethod.PUT),
            any(),
            eq(String::class.java)) } returns ResponseEntity("", HttpStatus.OK)

        val result = mockMvc.perform(put("/sed/put/$euxCaseId/$documentId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonsed))
            .andExpect(status().isOk)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals(true, response.toBoolean())
    }

    @Test
    fun `oppdate sed P5000 on buc results in false when json is not a valid SED Exception`() {

        val jsonsed = """
            {
            "value" : 12321,
            "ikkenoe" : 21312,
            "noemer" : null
            }
        """.trimIndent()

        val expectedError = """Data er ikke gyldig SEDformat""".trimIndent()
        mockMvc.perform(
            put("/sed/put/$euxCaseId/$documentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonsed))
            .andExpect(status().isBadRequest)
            .andExpect(status().reason(Matchers.containsString(expectedError)))
    }

    private fun createDummyClientRestExecption(httpstatus: HttpStatus, dummyBody: String)
            = HttpClientErrorException.create (httpstatus, httpstatus.name, HttpHeaders(), dummyBody.toByteArray(), Charset.defaultCharset())

}

