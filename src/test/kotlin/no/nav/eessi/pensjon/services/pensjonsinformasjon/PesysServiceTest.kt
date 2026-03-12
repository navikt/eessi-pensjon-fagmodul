package no.nav.eessi.pensjon.services.pensjonsinformasjon

import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiFellesDto.EessiAvdodDto
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiFellesDto.EessiSakStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class PesysServiceTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer
    private lateinit var pesysService: PesysService

    @BeforeEach
    fun setup() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.bindTo(restTemplate).build()
        pesysService = PesysService(restTemplate)
    }

    @Test
    fun `hentP2000data mapper alle verdier fra p2000-alder json til P2xxxMeldingOmPensjonDto`() {
        val p2000Json = """
            [ {
              "sakId" : "26399073",
              "sakType" : "ALDER",
              "sakStatus" : "AVSL"
            } ]
            """.trimIndent()
        val fnr = "111111111111"
        server.expect(requestTo("/bruker/sakliste"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("fnr", fnr))
            .andRespond(withSuccess(p2000Json, MediaType.APPLICATION_JSON))

        val result = pesysService.hentSakListe(fnr)
        with(result) {
            assert(size == 1)
            assert(first().sakId == "26399073")
            assert(first().sakType == EessiFellesDto.EessiSakType.ALDER)
            assert(first().sakStatus == EessiSakStatus.AVSL)
        }
        server.verify()
    }


    @Test
    fun `hentAvdod skal sortere listen fra prioritert liste og gi riktig EessiAvdodDto tilbake`() {
        val vedtakId = "11111111111"

        server.expect(requestTo("/vedtak/$vedtakId/avdoed"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """{
                      "avdod": null,
                      "avdodMor": "2131232321",
                      "avdodFar": "3432434234"
                    }""".trimIndent(), MediaType.APPLICATION_JSON))

        with(pesysService.hentAvdod(vedtakId)) {
            assert(this == EessiAvdodDto(avdod = null, avdodMor = "2131232321", avdodFar = "3432434234"))
        }
        server.verify()
    }
}