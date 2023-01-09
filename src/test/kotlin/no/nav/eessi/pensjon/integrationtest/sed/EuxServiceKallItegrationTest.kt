package no.nav.eessi.pensjon.integrationtest.sed

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class EuxServiceKallItegrationTest {

    @MockkBean(name = "pdlRestTemplate")
    private lateinit var pdlRestTemplate: RestTemplate

    @MockkBean(name = "prefillOAuthTemplate")
    private lateinit var prefillOAuthTemplate: RestTemplate

    @MockkBean(name = "euxNavIdentRestTemplate")
    private lateinit var restTemplate: RestTemplate

    @MockkBean(name = "euxSystemRestTemplate")
    private lateinit var euxUserNameRestTemplate: RestTemplate

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var restSafTemplate: RestTemplate

    @MockkBean(name = "safRestOidcRestTemplate")
    private lateinit var safRestOidcRestTemplate: RestTemplate

    @MockkBean(name = "pensjoninformasjonRestTemplate")
    private lateinit var pensjoninformasjonRestTemplate: RestTemplate

    @MockkBean(name = "kodeverkRestTemplate")
    private lateinit var kodeverkRestTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @Autowired
    private lateinit var mockMvc: MockMvc

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