package no.nav.eessi.pensjon.services.pensjonsinformasjon

import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiFellesDto.EessiAvdodDto
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiFellesDto.EessiSakStatus
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.web.client.HttpServerErrorException
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
    fun `hentP12000data returnerer ytelsesperioder fra alle vedtak uavhengig av vedtaksdato`() {
        val nyeste = p6000Data(LocalDate.of(2025, 10, 10))
        val vedtak = listOf(
            p6000Data(null),
            p6000Data(LocalDate.of(2025, 2, 10)),
            nyeste,
            p6000Data(LocalDate.of(2025, 5, 10))
        )
        server.expect(requestTo("/sed/p6000"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("sakId", "789"))
            .andRespond(withSuccess(vedtak.toJson(), MediaType.APPLICATION_JSON))

        assertEquals(vedtak.flatMap { it.ytelsePerMaaned }, pesysService.hentP12000data("789"))
        server.verify()
    }

    @ParameterizedTest
    @CsvSource(
        "2025-02-01, 2025-02-28, 0",
        "2025-03-01, 2025-03-01, 0;1",
        "2025-05-31, 2025-05-31, 1;2",
        "2025-04-01, 2025-04-30, 1",
        "2024-01-01, 2026-12-31, 0;1;2",
        "NULL, 2025-02-28, 0",
        "2025-06-01, NULL, 2",
        "2026-01-01, 2026-12-31, 2",
        "NULL, 2024-12-31, NULL",
        "NULL, NULL, 0;1;2",
        nullValues = ["NULL"]
    )
    fun `hentP12000data filtrerer overlappende fom tom med inklusive valgfrie grenser`(
        from: String?, to: String?, expected: String?
    ) {
        val base = p6000Data(null).ytelsePerMaaned.single()
        val perioder = listOf(
            base.copy(fom = LocalDate.of(2025, 1, 1), tom = LocalDate.of(2025, 3, 1)),
            base.copy(fom = LocalDate.of(2025, 3, 1), tom = LocalDate.of(2025, 5, 31)),
            base.copy(fom = LocalDate.of(2025, 5, 31), tom = null)
        )
        val vedtak = listOf(
            p6000Data(null).copy(ytelsePerMaaned = perioder.take(2)),
            p6000Data(LocalDate.of(2020, 1, 1)).copy(ytelsePerMaaned = perioder.drop(2))
        )
        server.expect(requestTo("/sed/p6000"))
            .andExpect(header("sakId", "789"))
            .andRespond(withSuccess(vedtak.toJson(), MediaType.APPLICATION_JSON))

        val result = pesysService.hentP12000data("789", from?.let(LocalDate::parse), to?.let(LocalDate::parse))

        assertEquals(expected?.split(";")?.map { perioder[it.toInt()] }, result)
        server.verify()
    }

    @Test
    fun `hentP12000data beholder perioder og duplikater fra alle vedtak`() {
        val forste = p6000Data(LocalDate.of(2025, 10, 10))
        val andre = forste.copy(sakType = EessiFellesDto.EessiSakType.UFOREP)
        server.expect(requestTo("/sed/p6000"))
            .andRespond(withSuccess(listOf(forste, andre).toJson(), MediaType.APPLICATION_JSON))

        assertEquals(forste.ytelsePerMaaned + andre.ytelsePerMaaned, pesysService.hentP12000data("789"))
        server.verify()
    }

    @Test
    fun `hentP12000data inkluderer perioder naar alle vedtaksdatoer mangler`() {
        val forste = p6000Data(null)
        val andre = forste.copy(sakType = EessiFellesDto.EessiSakType.UFOREP)
        server.expect(requestTo("/sed/p6000"))
            .andRespond(withSuccess(listOf(forste, andre).toJson(), MediaType.APPLICATION_JSON))

        assertEquals(forste.ytelsePerMaaned + andre.ytelsePerMaaned, pesysService.hentP12000data("789"))
        server.verify()
    }

    @Test
    fun `hentP12000data returnerer null naar vedtak ikke har ytelsesperioder`() {
        server.expect(requestTo("/sed/p6000"))
            .andRespond(withSuccess(
                listOf(p6000Data(null).copy(ytelsePerMaaned = emptyList())).toJson(),
                MediaType.APPLICATION_JSON
            ))

        assertNull(pesysService.hentP12000data("789"))
        server.verify()
    }

    @Test
    fun `hentP12000data returnerer null for tom liste`() {
        server.expect(requestTo("/sed/p6000"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        assertNull(pesysService.hentP12000data("789"))
        server.verify()
    }

    @Test
    fun `hentP12000data returnerer null for manglende responsbody`() {
        server.expect(requestTo("/sed/p6000"))
            .andRespond(withNoContent())

        assertNull(pesysService.hentP12000data("789"))
        server.verify()
    }

    private fun p6000Data(dato: LocalDate?) = P6000MeldingOmVedtakDto(
        avdod = P6000MeldingOmVedtakDto.Avdod(null, false, false, false),
        sakType = EessiFellesDto.EessiSakType.ALDER,
        trygdeavtale = P6000MeldingOmVedtakDto.Trygdeavtale(false, true),
        trygdetid = listOf(P6000MeldingOmVedtakDto.Trygdetid(LocalDate.of(1987, 3, 15), LocalDate.of(2020, 11, 15))),
        vedtak = P6000MeldingOmVedtakDto.Vedtak(LocalDate.of(2025, 1, 1), "REVURD", false, true, dato),
        vilkarsvurdering = listOf(
            P6000MeldingOmVedtakDto.Vilkarsvurdering(
                LocalDate.of(2025, 1, 1),
                P6000MeldingOmVedtakDto.VilkarsvurderingUforetrygd("OPPFYLT", null, null, null, null),
                "INNV", false, null
            )
        ),
        ytelsePerMaaned = listOf(
                    P6000MeldingOmVedtakDto.YtelsePerMaaned(
                LocalDate.of(2025, 1, 1), null, false, null, 5057,
                listOf(P6000MeldingOmVedtakDto.Ytelseskomponent("IP", 5057))
            )
        )
    )

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

    @Test
    @Disabled
    fun `uthentingAvUforeTidspunkt returnerer et enkelt ufoeretidspunkt fra en liste`() {
        val enDato = LocalDate.now()
        val dto = listOf(
            EessiFellesDto.EessiUfoeretidspunktDto(enDato, enDato.plusDays(10).plusDays(1)) ,
            EessiFellesDto.EessiUfoeretidspunktDto(enDato, enDato.plusDays(10)) ,
            EessiFellesDto.EessiUfoeretidspunktDto(enDato, enDato.plusDays(10).plusDays(4))
        )

        server.expect(requestTo("/vedtak/111/ufoeretidspunkt"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(dto.toJson(), MediaType.APPLICATION_JSON))
        val result = pesysService.hentUfoeretidspunktOnVedtak("111")
        assertEquals(dto[1], result)
    }

}