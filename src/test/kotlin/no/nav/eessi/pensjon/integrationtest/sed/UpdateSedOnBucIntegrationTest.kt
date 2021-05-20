package no.nav.eessi.pensjon.integrationtest.sed

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.security.sts.STSService
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.nio.charset.Charset
import kotlin.test.assertEquals

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class UpdateSedOnBucIntegrationTest {

    @MockkBean
    lateinit var stsService: STSService

    @MockkBean(name = "euxOidcRestTemplate")
    lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var mockMvc: MockMvc

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
            eq(String::class.java)) } returns ResponseEntity(null ,HttpStatus.OK)

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

        mockMvc.perform(
            put("/sed/put/$euxCaseId/$documentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonsed))
            .andExpect(status().is4xxClientError)
            .andExpect(status().reason(Matchers.containsString(expectedError)))

    }

    @Test
    fun `oppdate sed P5000 empty medlemskap and gydligperiode 0 true when all OK`() {
        val jsonsed = javaClass.getResource("/json/nav/P5000-tomperioder-NAV.json").readText()

        /////cpi/buc/{RinaSakId}/sed/{DokumentId}
        every { restTemplate.exchange(
            eq("/buc/$euxCaseId/sed/$documentId?ventePaAksjon=false"),
            eq(HttpMethod.PUT),
            any(),
            eq(String::class.java)) } returns ResponseEntity(null ,HttpStatus.OK)

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

