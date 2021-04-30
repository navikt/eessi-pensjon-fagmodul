package no.nav.eessi.pensjon.integrationtest.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.security.sts.STSService
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
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

    @MockBean
    lateinit var stsService: STSService

    @MockBean(name = "euxOidcRestTemplate")
    lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var mockMvc: MockMvc

    private companion object {
        const val euxCaseId = "131231234"
        const val documentId = "12312j3g12jh3g12kj3g12kj3g12k3gh123k1g23"

    }

    @Test
    fun `oppdate sed P5000 on buc result in true when all OK`() {

        val jsonsed = javaClass.getResource("/json/nav/P5000-NAV.json").readText()

        /////cpi/buc/{RinaSakId}/sed/{DokumentId}
        doReturn(ResponseEntity(null ,HttpStatus.OK))
            .whenever(restTemplate).exchange(
                eq("/buc/$euxCaseId/sed/$documentId?ventePaAksjon=false"),
                eq(HttpMethod.PUT),
                any(),
                eq(String::class.java)
            )

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

        val jsonsed = javaClass.getResource("/json/nav/P5000-NAV.json").readText()

        //cpi/buc/{RinaSakId}/sed/{DokumentId}

        doThrow(createDummyClientRestExecption(HttpStatus.UNAUTHORIZED, "Unauthorized"))
            .whenever(restTemplate).exchange(
                eq("/buc/$euxCaseId/sed/$documentId?ventePaAksjon=false"),
                eq(HttpMethod.PUT),
                any(),
                eq(String::class.java)
            )
        val expectedError = """Authorization token required for Rina.""".trimIndent()

        mockMvc.perform(
            put("/sed/put/$euxCaseId/$documentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonsed))
            .andExpect(status().is4xxClientError)
            .andExpect(status().reason(Matchers.containsString(expectedError)))

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

