package no.nav.eessi.pensjon.integrationtest.sed

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.fagmodul.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.security.sts.STSService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
@EmbeddedKafka
class EuxServiceKallItegrationTest {

    @MockkBean
    lateinit var stsService: STSService

    @MockkBean(name = "euxOidcRestTemplate")
    lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val responsejson = """
            [{"kode":"AT","term":"Østerrike"},{"kode":"BE","term":"Belgium"},{"kode":"BG","term":"Bulgaria"},{"kode":"HR","term":"Kroatia"},{"kode":"CY","term":"Kypros"},{"kode":"CZ","term":"Tsjekkia"},{"kode":"DK","term":"Danmark"},{"kode":"EE","term":"Estland"},{"kode":"FI","term":"Finland"},{"kode":"FR","term":"Frankrike"},{"kode":"GR","term":"Hellas"},{"kode":"IE","term":"Irland"},{"kode":"IS","term":"Island"},{"kode":"IT","term":"Italia"},{"kode":"LV","term":"Latvia"},{"kode":"NO","term":"Norge"},{"kode":"SE","term":"Sverige"}]
        """.trimIndent()

    @Nested
    @DisplayName("Swagger and v3-api-docs")
    inner class swaggerapi {

        @Test
        fun `sjekk v3 api-docs saver korrekt`() {

            val result = mockMvc.perform(
                MockMvcRequestBuilders.get("/v3/api-docs/").contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk).andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)).andReturn()

            val response = result.response.getContentAsString(charset("UTF-8"))
            println(response)
        }

        @Test
        fun `sjekk swagger-ui v3 svarer korrekt`() {

            val result = mockMvc.perform(
                MockMvcRequestBuilders.get("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config").contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk).andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_HTML)).andReturn()

            val response = result.response.getContentAsString(charset("UTF-8"))
            println(response)
        }

    }

    @Test
    fun `sjekk for landkoder med bruk av kodever for eux`() {

        every { restTemplate.exchange(
            eq("/kodeverk?Kodeverk=landkoder"),
            eq(HttpMethod.GET),
            any(),
            eq(String::class.java)) } returns ResponseEntity.ok().body( responsejson )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/eux/landkoder")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            ["AT","BE","BG","HR","CY","CZ","DK","EE","FI","FR","GR","IE","IS","IT","LV","NO","SE"]
        """.trimIndent()

        assertEquals(expected, response)

        val result2 = mockMvc.perform(
            MockMvcRequestBuilders.get("/eux/landkoder")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response2 = result2.response.getContentAsString(charset("UTF-8"))
        assertEquals(expected, response2)


    }

}