package no.nav.eessi.pensjon.integrationtest.sed

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class EuxServiceKallItegrationTest {

    @MockkBean(name = "prefillOAuthTemplate")
    private lateinit var prefillOAuthTemplate: RestTemplate

    @MockkBean(name = "euxOidcRestTemplate")
    private lateinit var restTemplate: RestTemplate

    @MockkBean(name = "euxUsernameOidcRestTemplate")
    private lateinit var euxUserNameRestTemplate: RestTemplate

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var restSafTemplate: RestTemplate

    @MockkBean(name = "safRestOidcRestTemplate")
    private lateinit var safRestOidcRestTemplate: RestTemplate

    @MockkBean(name = "pensjonsinformasjonOidcRestTemplate")
    private lateinit var pensjonsinformasjonOidcRestTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val responsejson = """
            [{"kode":"AT","term":"Østerrike"},{"kode":"BE","term":"Belgium"},{"kode":"BG","term":"Bulgaria"},{"kode":"HR","term":"Kroatia"},{"kode":"CY","term":"Kypros"},{"kode":"CZ","term":"Tsjekkia"},{"kode":"DK","term":"Danmark"},{"kode":"EE","term":"Estland"},{"kode":"FI","term":"Finland"},{"kode":"FR","term":"Frankrike"},{"kode":"GR","term":"Hellas"},{"kode":"IE","term":"Irland"},{"kode":"IS","term":"Island"},{"kode":"IT","term":"Italia"},{"kode":"LV","term":"Latvia"},{"kode":"NO","term":"Norge"},{"kode":"SE","term":"Sverige"}]
        """.trimIndent()

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

//    @Test
//    fun `test på gyldig rina2020 url fra eux`() {
//        val fakeid = "-1-11-111"
//        val mockRina2020url = "https://rina-q.adeo.no/portal_new/case-management/"
//
//        every { restTemplate.exchange(
//            eq("/url/buc/$fakeid"),
//            eq(HttpMethod.GET),
//            any(),
//            eq(String::class.java)) } returns ResponseEntity.ok().body( mockRina2020url+fakeid )

//        val response = mockMvc.perform(
//            MockMvcRequestBuilders.get("/eux/rinaurl"))
//            .andExpect(MockMvcResultMatchers.status().isOk)
//            .andReturn()
//
//        val result = response.response.contentAsString
//        val expected = """{"rinaUrl":"https://rina-q.adeo.no/portal_new/case-management/"}""".trimIndent()
//        assertEquals(expected, result)

//        val response2 = mockMvc.perform(
//            MockMvcRequestBuilders.get("/eux/rinaurl"))
//            .andExpect(MockMvcResultMatchers.status().isOk)
//            .andReturn()

//        val result2 = response2.response.contentAsString
//        assertEquals(expected, result2)

//        val response3 = mockMvc.perform(
//            MockMvcRequestBuilders.get("/eux/rinaurl"))
//            .andExpect(MockMvcResultMatchers.status().isOk)
//            .andReturn()

//        val result3 = response3.response.contentAsString
//        assertEquals(expected, result3)

//    }

    @Test
    fun sjekkRinaUrl() {
        val fakeid = "-1-11-111"
        val mockRina2020url = "https://rina-q.adeo.no/portal_new/case-management/"

        every { restTemplate.exchange(
            eq("/url/buc/$fakeid"),
            eq(HttpMethod.GET),
            any(),
            eq(String::class.java)) } returns ResponseEntity.ok().body( mockRina2020url+fakeid )

        val response = mockMvc.perform(get("/eux/rinaurl")
            .header("Authorization", "blatoken"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()

        val result = response.response.contentAsString
        val expected = """{"rinaUrl":"https://rina-q.adeo.no/portal_new/case-management/"}""".trimIndent()
        assertEquals(expected, result)

    }

}