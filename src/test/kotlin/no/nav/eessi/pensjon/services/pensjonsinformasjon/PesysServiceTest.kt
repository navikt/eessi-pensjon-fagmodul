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
import java.time.LocalDate

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
    fun `hentSakListe returnerer en liste med saker`() {
        val sakListeJson = """
            [ {
              "sakId" : "26399073",
              "sakType" : "ALDER",
              "sakStatus" : "INNV"
            }, 
             {
              "sakId" : "26399073",
              "sakType" : "UFOREP",
              "sakStatus" : "AVSL"
            }]
            """.trimIndent()
        val fnr = "111111111111"
        server.expect(requestTo("/bruker/sakliste"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("fnr", fnr))
            .andRespond(withSuccess(sakListeJson, MediaType.APPLICATION_JSON))

        val result = pesysService.hentSakListe(fnr)
        with(result) {
            assert(size == 2)
            assert(first().sakId == "26399073")
            assert(first().sakType == EessiFellesDto.EessiSakType.ALDER)
            assert(first().sakStatus == EessiSakStatus.INNV)
            assert(last().sakStatus == EessiSakStatus.AVSL)
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
                  "avdod" : "66526810121",
                  "avdodMor" : null,
                  "avdodFar" : null
                }""".trimIndent(), MediaType.APPLICATION_JSON))

        with(pesysService.hentAvdod(vedtakId)) {
            assert(this == EessiAvdodDto(avdod="66526810121", avdodMor=null, avdodFar=null))
        }
        server.verify()
    }

    @Test
    fun `hentKravdato returnerer kravdato for gyldig kravId`() {
        val kravId = "12345678"
        server.expect(requestTo("/krav/$kravId/mottattDato"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("\"2022-03-01\"", MediaType.APPLICATION_JSON))

        val result = pesysService.hentKravdato(kravId)
        assert(result == LocalDate.of(2022, 3, 1))
        server.verify()
    }

    @Test
    fun `hentSakListe returnerer tom liste naar responsen er tom`() {
        val fnr = "111111111111"
        server.expect(requestTo("/bruker/sakliste"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("fnr", fnr))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val result = pesysService.hentSakListe(fnr)
        assert(result.isEmpty())
        server.verify()
    }

}