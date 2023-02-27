package no.nav.eessi.pensjon.integrationtest.sed

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
@EmbeddedKafka
@MockkBeans(
    MockkBean(name = "personService", classes = [PersonService::class]),
    MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "restEuxTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "kodeverkRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "prefillOAuthTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxSystemRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxNavIdentRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pensjonsinformasjonClient", classes = [PensjonsinformasjonClient::class])
)
class EuxServiceKallItegrationTest {

    @Autowired
    private lateinit var euxNavIdentRestTemplate: RestTemplate

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun sjekkRinaUrl() {
        val fakeid = "-1-11-111"
        val mockRina2020url = "https://rina-q.adeo.no/portal_new/case-management/"

        every { euxNavIdentRestTemplate.exchange(
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