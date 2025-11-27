package no.nav.eessi.pensjon.api.geo

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    classes = [IntegrasjonsTestConfig::class, UnsecuredWebMvcTestLauncher::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureRestTestClient
@EmbeddedKafka
@MockkBeans(
    value = [
//        MockkBean(name = "restTemplate", classes = [TestRestTemplate::class], relaxed = true),
        MockkBean(name = "personService", classes = [PersonService::class]),
        MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "restEuxTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "kodeverkRestTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "prefillOAuthTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "euxSystemRestTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class]),
        MockkBean(name = "euxNavIdentRestTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "euxNavIdentRestTemplateV2", classes = [RestTemplate::class]),
        MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
        MockkBean(name = "pensjonsinformasjonClient", classes = [PensjonsinformasjonClient::class])
    ]
)
class LandkoderControllerIntegrationTest {

    @Autowired
    lateinit var restTemplate: RestTestClient

    companion object {
        private var port = 1080

        private var clientAndServer = ClientAndServer.startClientAndServer(port)

        @JvmStatic
        @BeforeAll
        fun setUp() {
            MockServerClient("localhost", port).apply {
                `when`(
                    HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/landkoder/rina")
                        .withQueryStringParameter("format", "iso2")
                ).respond(
                    HttpResponse.response()
                        .withStatusCode(200)
                        .withBody("""{"result": "iso2 format response"}""")
                )

                `when`(
                    HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/landkoder/rina")
                ).respond(
                    HttpResponse.response()
                        .withStatusCode(200)
                        .withBody("""{"result": "default format response"}""")
                )
            }
        }

        @JvmStatic
        @AfterAll
        fun close() {
            clientAndServer.close()
        }
    }


    @Test
    fun `landkoderAkseptertAvRina med format`() {
        val format = "iso2"
        val response= restTemplate
            .get().uri("http://localhost:$port/landkoder/rina?format=$format").exchange().returnResult()

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("""{"result": "iso2 format response"}""", response.responseBodyContent.toString(Charsets.UTF_8))
    }

    @Test
    fun `landkoderAkseptertAvRina uten format`() {
//        val response: ResponseEntity<String> = restTemplate.getForEntity(
//            "http://localhost:$port/landkoder/rina",
//            String::class.java
//        )
//
//        assertEquals(HttpStatus.OK, response.statusCode)
//        assertEquals("""{"result": "default format response"}""", response.body)
    }
}
