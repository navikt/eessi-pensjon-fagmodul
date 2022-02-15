package no.nav.eessi.pensjon.api.pensjon

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonRequestBuilder
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.Pensjontype
import no.nav.eessi.pensjon.utils.toJson
import no.nav.pensjon.v1.brukerssakerliste.V1BrukersSakerListe
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.MDC
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

class PensjonControllerTest {

    private var pensjonsinformasjonClient: PensjonsinformasjonClient = mockk()

    @SpyK
    private var auditLogger: AuditLogger = AuditLogger()

    @InjectMockKs
    private val controller = PensjonController(pensjonsinformasjonClient, auditLogger)

    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @BeforeEach
    fun setup() {
        controller.initMetrics()
    }

    @Test
    fun `hentPensjonSakType gitt en aktoerId saa slaa opp fnr og hent deretter sakstype`() {
        val aktoerId = "1234567890123" // 13 sifre
        val sakId = "Some sakId"

        every { pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId) } returns Pensjontype(sakId, "Type")

        controller.hentPensjonSakType(sakId, aktoerId)


        verify { pensjonsinformasjonClient.hentKunSakType(eq(sakId), eq(aktoerId)) }
    }


    @Test
    fun `hentPensjonSakType gitt at det svar fra PESYS er tom`() {
        val aktoerId = "1234567890123" // 13 sifre
        val sakId = "Some sakId"

        every { pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId) } returns Pensjontype(sakId, "")
        val response = controller.hentPensjonSakType(sakId, aktoerId)


        verify { pensjonsinformasjonClient.hentKunSakType(eq(sakId), eq(aktoerId)) }

        val expected = """
            {
              "sakId" : "Some sakId",
              "sakType" : ""
            }
        """.trimIndent()

        assertEquals(expected, response?.body)

    }

    @Test
    fun `hentPensjonSakType gitt at det svar feiler fra PESYS`() {
        val aktoerId = "1234567890123" // 13 sifre
        val sakId = "Some sakId"

        every { pensjonsinformasjonClient.hentKunSakType(sakId, aktoerId) } returns Pensjontype(sakId, "")

        val response = controller.hentPensjonSakType(sakId, aktoerId)

        verify { pensjonsinformasjonClient.hentKunSakType(eq(sakId), eq(aktoerId)) }

        val expected = """
            {
              "sakId" : "Some sakId",
              "sakType" : ""
            }
        """.trimIndent()

        assertEquals(expected, response?.body)

    }

    @Test
    fun `Gitt det finnes pensjonsak på aktoer så skal det returneres en liste over alle saker til aktierid`() {
        val aktoerId = "1234567890123" // 13 sifre

        val mockpen = Pensjonsinformasjon()
        val mocksak1 = V1Sak()
        mocksak1.sakId = 1010
        mocksak1.status = "INNV"
        mocksak1.sakType = "ALDER"
        mockpen.brukersSakerListe = V1BrukersSakerListe()
        mockpen.brukersSakerListe.brukersSakerListe.add(mocksak1)
        val mocksak2 = V1Sak()
        mocksak2.sakId = 2020
        mocksak2.status = "AVSL"
        mocksak2.sakType = "UFOREP"
        mockpen.brukersSakerListe.brukersSakerListe.add(mocksak2)

        every { pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId) } returns mockpen

        val result = controller.hentPensjonSakIder(aktoerId)

        verify { pensjonsinformasjonClient.hentAltPaaAktoerId(eq(aktoerId)) }

        assertEquals(2, result.size)
        val expected1 = PensjonSak("1010", "ALDER", PensjonSakStatus.LOPENDE)
        assertEquals(expected1.toJson(), result.first().toJson())
        val expected2 = PensjonSak("2020", "UFOREP", PensjonSakStatus.AVSLUTTET)
        assertEquals(expected2.toJson(), result.last().toJson())

        assertEquals(PensjonSakStatus.AVSLUTTET, expected2.sakStatus)
    }

    @Test
    fun `Gitt det ikke finnes pensjonsak på aktoer så skal det returneres et tomt svar tom liste`() {
        val aktoerId = "1234567890123" // 13 sifre

        val mockpen = Pensjonsinformasjon()
        mockpen.brukersSakerListe = V1BrukersSakerListe()

        every { (pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId)) } returns mockpen

        val result = controller.hentPensjonSakIder(aktoerId)
        verify(exactly = 1) {
            pensjonsinformasjonClient.hentAltPaaAktoerId(any())

            assertEquals(0, result.size)
        }
    }

    @Test
    fun `sjekk på forskjellige verdier av sakstatus fra pensjoninformasjon konvertere de til enum`() {
        val tilbeh = "TIL_BEHANDLING"
        val avsl = "AVSL"
        val lop = "INNV"
        val opph = "OPPHOR"
        val ukjent = "CrazyIkkeIbrukTull"


        assertEquals(PensjonSakStatus.TIL_BEHANDLING, PensjonSakStatus.from(tilbeh))
        assertEquals(PensjonSakStatus.AVSLUTTET, PensjonSakStatus.from(avsl))
        assertEquals(PensjonSakStatus.LOPENDE, PensjonSakStatus.from(lop))
        assertEquals(PensjonSakStatus.OPPHOR, PensjonSakStatus.from(opph))
        assertEquals(PensjonSakStatus.UKJENT, PensjonSakStatus.from(ukjent))

    }

    @Test
    fun `hentKravDato skal gi en data hentet fra aktorid og vedtaksid `() {
        val kravDato = "2020-01-01"
        val aktoerId = "123"
        val saksId = "10000"
        val kravId = "12456"

        every { pensjonsinformasjonClient.hentKravDatoFraAktor(aktoerId, saksId, kravId) } returns kravDato

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/pensjon/kravdato/saker/$saksId/krav/$kravId/aktor/$aktoerId")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        print(response)

        assertEquals("""{ "kravDato": "$kravDato" }""", response)
    }

    @Test
    fun `hentKravDato skal gi 400 og feilmelding ved manglende parameter`() {
        val aktoerId = "123"
        val saksId = "10000"
        val kravId = ""

        mockMvc.perform(
            MockMvcRequestBuilders.get("/pensjon/kravdato/saker/$saksId/krav/$kravId/aktor/$aktoerId")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun uthentingAvUforeTidspunkt() {
        val mockVedtakid = "213123333"
        val mockClient = fraFil("VEDTAK-UT-MUTP.xml")
        val mockController = PensjonController(mockClient, auditLogger,)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$mockVedtakid/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
            )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            {
              "uforetidspunkt" : "2020-02-29"
            }
        """.trimIndent()
        assertEquals(expected, response)

    }

    @Test
    fun uthentingAvUforeTidspunktMedGMTZ() {
        val mockVedtakid = "213123333"
        val mockClient = fraFil("VEDTAK-UT-MUTP-GMTZ.xml")
        val mockController = PensjonController(mockClient, auditLogger,)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$mockVedtakid/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            {
              "uforetidspunkt" : "2020-03-01"
            }
        """.trimIndent()

        assertEquals(expected, response)
    }

    @Test
    fun uthentingAvUforeTidspunktSomErTom() {
        val mockVedtakid = "213123333"
        val mockClient = fraFil("VEDTAK-UT.xml")
        val mockController = PensjonController(mockClient, auditLogger,)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$mockVedtakid/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            {
              "uforetidspunkt" : null
            }
        """.trimIndent()

        assertEquals(expected, response)
    }

    @ParameterizedTest
    @CsvSource(
        "2020, 02, 29, 23,  60, 2020-02-29, false",
        "2020, 02, 29, 23,  0, 2020-03-01, true",
        "2020, 06, 01, 00, 120, 2020-06-01, true",
        "2020, 05, 31, 23, 00, 2020-06-01, true",
        "2020, 05, 31, 23, 120, 2020-05-31, false",
        "2020, 06, 01, 00, 120, 2020-06-01, true")
    fun `Sjekk for konvertering fra XMLgregorianCalendar til String med stotte for GMT`(xmlYear: Int, xmlMonth: Int, xmlDay: Int, xmlHour: Int, xmlTz: Int, resultat: String, check: Boolean) {
        val calendar = GregorianCalendar()
        calendar.set(xmlYear, xmlMonth-1, xmlDay, xmlHour, 0, 0)
        val xmlDate: XMLGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar)
        xmlDate.timezone = xmlTz

        val verdi = controller.transformXMLGregorianCalendarToJson(xmlDate)
        assertEquals(resultat, verdi.toString())
        assert( verdi.dayOfMonth == 1 == check )
    }


    @Test
    fun `Sjekke for hentKravDatoFraAktor ikke kaster en unormal feil`() {
        val aktoerId = "123"
        val saksId = "10000"
        val kravId = "345345"

        MDC.put("x_request_id","AAA-BBB")
        every { pensjonsinformasjonClient.hentKravDatoFraAktor(any(), any(), any()) } returns null

        val result = controller.hentKravDatoFraAktor(saksId, kravId, aktoerId)
        assertEquals("{\"success\": false, \n" + " \"error\": \"Feiler å hente kravDato\", \"uuid\": \"AAA-BBB\"}", result?.body)

    }

    @Test
    fun `sjekk om sak resultat er gyldig pensjoninfo`() {
        val ident = "1234567890123"
        val mockSakid = "21841174"
        val mockClient = fraFil("ALDERP-INV-21841174.xml")

        val mockController = PensjonController(mockClient, auditLogger,)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/sak/aktoer/$ident/sakid/$mockSakid/pensjonsak")
                .contentType(MediaType.APPLICATION_JSON)
        )
       .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            {
              "sakType" : "ALDER",
              "sakId" : 21841174,
              "forsteVirkningstidspunkt" : "2016-03-01",
              "kravHistorikkListe" : {
                "kravHistorikkListe" : [ {
                  "kravId" : null,
                  "kravType" : "F_BH_MED_UTL",
                  "mottattDato" : "2015-11-25",
                  "virkningstidspunkt" : "2016-03-01",
                  "status" : "AVSL",
                  "kravArsak" : null
                }, {
                  "kravId" : null,
                  "kravType" : "MELLOMBH",
                  "mottattDato" : "2015-11-25",
                  "virkningstidspunkt" : "2016-03-01",
                  "status" : "INNV",
                  "kravArsak" : null
                }, {
                  "kravId" : null,
                  "kravType" : "SLUTT_BH_UTL",
                  "mottattDato" : "2016-06-07",
                  "virkningstidspunkt" : "2017-08-01",
                  "status" : "INNV",
                  "kravArsak" : null
                }, {
                  "kravId" : null,
                  "kravType" : "REVURD",
                  "mottattDato" : "2016-12-10",
                  "virkningstidspunkt" : "2017-01-01",
                  "status" : "INNV",
                  "kravArsak" : null
                }, {
                  "kravId" : null,
                  "kravType" : "REVURD",
                  "mottattDato" : "2017-12-16",
                  "virkningstidspunkt" : "2018-01-01",
                  "status" : "INNV",
                  "kravArsak" : null
                } ]
              },
              "ytelsePerMaanedListe" : {
                "ytelsePerMaanedListe" : [ {
                  "fom" : "2018-05-01",
                  "tom" : null,
                  "mottarMinstePensjonsniva" : false,
                  "gjenlevendetilleggAnvendtAlderspensjon" : false,
                  "yrkesskadeAnvendtAlderspensjon" : false,
                  "vinnendeBeregningsmetode" : "FOLKETRYGD",
                  "vinnendeBeregningsmetodeKap20" : "FOLKETRYGD",
                  "vurdertBeregningsmetodeFolketrygd" : false,
                  "vurdertBeregningsmetodeEOS" : false,
                  "vurdertBeregningsmetodeNordisk" : false,
                  "belop" : 16114,
                  "belopUtenAvkorting" : 16114,
                  "ytelseskomponentListe" : [ {
                    "ytelsesKomponentType" : "GP",
                    "belopTilUtbetaling" : 5328,
                    "belopUtenAvkorting" : 5328
                  }, {
                    "ytelsesKomponentType" : "TP",
                    "belopTilUtbetaling" : 9009,
                    "belopUtenAvkorting" : 9009
                  }, {
                    "ytelsesKomponentType" : "MIN_NIVA_TILL_INDV",
                    "belopTilUtbetaling" : 347,
                    "belopUtenAvkorting" : 347
                  }, {
                    "ytelsesKomponentType" : "IP",
                    "belopTilUtbetaling" : 1189,
                    "belopUtenAvkorting" : 1189
                  }, {
                    "ytelsesKomponentType" : "GAP",
                    "belopTilUtbetaling" : 241,
                    "belopUtenAvkorting" : 241
                  } ],
                  "benyttetSivilstand" : "ENSLIG",
                  "uforetidspunkt" : null
                }, {
                  "fom" : "2018-01-01",
                  "tom" : null,
                  "mottarMinstePensjonsniva" : false,
                  "gjenlevendetilleggAnvendtAlderspensjon" : false,
                  "yrkesskadeAnvendtAlderspensjon" : false,
                  "vinnendeBeregningsmetode" : "FOLKETRYGD",
                  "vinnendeBeregningsmetodeKap20" : "FOLKETRYGD",
                  "vurdertBeregningsmetodeFolketrygd" : false,
                  "vurdertBeregningsmetodeEOS" : false,
                  "vurdertBeregningsmetodeNordisk" : false,
                  "belop" : 15665,
                  "belopUtenAvkorting" : 15665,
                  "ytelseskomponentListe" : [ {
                    "ytelsesKomponentType" : "MIN_NIVA_TILL_INDV",
                    "belopTilUtbetaling" : 310,
                    "belopUtenAvkorting" : 310
                  }, {
                    "ytelsesKomponentType" : "GAP",
                    "belopTilUtbetaling" : 235,
                    "belopUtenAvkorting" : 235
                  }, {
                    "ytelsesKomponentType" : "IP",
                    "belopTilUtbetaling" : 1158,
                    "belopUtenAvkorting" : 1158
                  }, {
                    "ytelsesKomponentType" : "GP",
                    "belopTilUtbetaling" : 5189,
                    "belopUtenAvkorting" : 5189
                  }, {
                    "ytelsesKomponentType" : "TP",
                    "belopTilUtbetaling" : 8773,
                    "belopUtenAvkorting" : 8773
                  } ],
                  "benyttetSivilstand" : "ENSLIG",
                  "uforetidspunkt" : null
                }, {
                  "fom" : "2017-08-01",
                  "tom" : null,
                  "mottarMinstePensjonsniva" : false,
                  "gjenlevendetilleggAnvendtAlderspensjon" : false,
                  "yrkesskadeAnvendtAlderspensjon" : false,
                  "vinnendeBeregningsmetode" : "FOLKETRYGD",
                  "vinnendeBeregningsmetodeKap20" : "FOLKETRYGD",
                  "vurdertBeregningsmetodeFolketrygd" : false,
                  "vurdertBeregningsmetodeEOS" : false,
                  "vurdertBeregningsmetodeNordisk" : false,
                  "belop" : 15181,
                  "belopUtenAvkorting" : 15181,
                  "ytelseskomponentListe" : [ {
                    "ytelsesKomponentType" : "GP",
                    "belopTilUtbetaling" : 5047,
                    "belopUtenAvkorting" : 5047
                  }, {
                    "ytelsesKomponentType" : "GAP",
                    "belopTilUtbetaling" : 203,
                    "belopUtenAvkorting" : 203
                  }, {
                    "ytelsesKomponentType" : "IP",
                    "belopTilUtbetaling" : 1158,
                    "belopUtenAvkorting" : 1158
                  }, {
                    "ytelsesKomponentType" : "TP",
                    "belopTilUtbetaling" : 8773,
                    "belopUtenAvkorting" : 8773
                  } ],
                  "benyttetSivilstand" : "ENSLIG",
                  "uforetidspunkt" : null
                }, {
                  "fom" : "2017-05-01",
                  "tom" : null,
                  "mottarMinstePensjonsniva" : false,
                  "gjenlevendetilleggAnvendtAlderspensjon" : false,
                  "yrkesskadeAnvendtAlderspensjon" : false,
                  "vinnendeBeregningsmetode" : "FOLKETRYGD",
                  "vinnendeBeregningsmetodeKap20" : "FOLKETRYGD",
                  "vurdertBeregningsmetodeFolketrygd" : false,
                  "vurdertBeregningsmetodeEOS" : false,
                  "vurdertBeregningsmetodeNordisk" : false,
                  "belop" : 15181,
                  "belopUtenAvkorting" : 15181,
                  "ytelseskomponentListe" : [ {
                    "ytelsesKomponentType" : "TP",
                    "belopTilUtbetaling" : 8773,
                    "belopUtenAvkorting" : 8773
                  }, {
                    "ytelsesKomponentType" : "IP",
                    "belopTilUtbetaling" : 1158,
                    "belopUtenAvkorting" : 1158
                  }, {
                    "ytelsesKomponentType" : "GAP",
                    "belopTilUtbetaling" : 203,
                    "belopUtenAvkorting" : 203
                  }, {
                    "ytelsesKomponentType" : "GP",
                    "belopTilUtbetaling" : 5047,
                    "belopUtenAvkorting" : 5047
                  } ],
                  "benyttetSivilstand" : "ENSLIG",
                  "uforetidspunkt" : null
                }, {
                  "fom" : "2017-01-01",
                  "tom" : null,
                  "mottarMinstePensjonsniva" : false,
                  "gjenlevendetilleggAnvendtAlderspensjon" : false,
                  "yrkesskadeAnvendtAlderspensjon" : false,
                  "vinnendeBeregningsmetode" : "FOLKETRYGD",
                  "vinnendeBeregningsmetodeKap20" : "FOLKETRYGD",
                  "vurdertBeregningsmetodeFolketrygd" : false,
                  "vurdertBeregningsmetodeEOS" : false,
                  "vurdertBeregningsmetodeNordisk" : false,
                  "belop" : 15122,
                  "belopUtenAvkorting" : 15122,
                  "ytelseskomponentListe" : [ {
                    "ytelsesKomponentType" : "IP",
                    "belopTilUtbetaling" : 1153,
                    "belopUtenAvkorting" : 1153
                  }, {
                    "ytelsesKomponentType" : "TP",
                    "belopTilUtbetaling" : 8739,
                    "belopUtenAvkorting" : 8739
                  }, {
                    "ytelsesKomponentType" : "GAP",
                    "belopTilUtbetaling" : 202,
                    "belopUtenAvkorting" : 202
                  }, {
                    "ytelsesKomponentType" : "GP",
                    "belopTilUtbetaling" : 5028,
                    "belopUtenAvkorting" : 5028
                  } ],
                  "benyttetSivilstand" : "ENSLIG",
                  "uforetidspunkt" : null
                }, {
                  "fom" : "2016-05-01",
                  "tom" : null,
                  "mottarMinstePensjonsniva" : false,
                  "gjenlevendetilleggAnvendtAlderspensjon" : false,
                  "yrkesskadeAnvendtAlderspensjon" : false,
                  "vinnendeBeregningsmetode" : "FOLKETRYGD",
                  "vinnendeBeregningsmetodeKap20" : "FOLKETRYGD",
                  "vurdertBeregningsmetodeFolketrygd" : false,
                  "vurdertBeregningsmetodeEOS" : false,
                  "vurdertBeregningsmetodeNordisk" : false,
                  "belop" : 14958,
                  "belopUtenAvkorting" : 14958,
                  "ytelseskomponentListe" : [ {
                    "ytelsesKomponentType" : "TP",
                    "belopTilUtbetaling" : 8739,
                    "belopUtenAvkorting" : 8739
                  }, {
                    "ytelsesKomponentType" : "GP",
                    "belopTilUtbetaling" : 4894,
                    "belopUtenAvkorting" : 4894
                  }, {
                    "ytelsesKomponentType" : "GAP",
                    "belopTilUtbetaling" : 172,
                    "belopUtenAvkorting" : 172
                  }, {
                    "ytelsesKomponentType" : "IP",
                    "belopTilUtbetaling" : 1153,
                    "belopUtenAvkorting" : 1153
                  } ],
                  "benyttetSivilstand" : "ENSLIG",
                  "uforetidspunkt" : null
                }, {
                  "fom" : "2016-03-01",
                  "tom" : null,
                  "mottarMinstePensjonsniva" : false,
                  "gjenlevendetilleggAnvendtAlderspensjon" : false,
                  "yrkesskadeAnvendtAlderspensjon" : false,
                  "vinnendeBeregningsmetode" : "FOLKETRYGD",
                  "vinnendeBeregningsmetodeKap20" : "FOLKETRYGD",
                  "vurdertBeregningsmetodeFolketrygd" : false,
                  "vurdertBeregningsmetodeEOS" : false,
                  "vurdertBeregningsmetodeNordisk" : false,
                  "belop" : 14574,
                  "belopUtenAvkorting" : 14574,
                  "ytelseskomponentListe" : [ {
                    "ytelsesKomponentType" : "IP",
                    "belopTilUtbetaling" : 1124,
                    "belopUtenAvkorting" : 1124
                  }, {
                    "ytelsesKomponentType" : "GAP",
                    "belopTilUtbetaling" : 168,
                    "belopUtenAvkorting" : 168
                  }, {
                    "ytelsesKomponentType" : "GP",
                    "belopTilUtbetaling" : 4768,
                    "belopUtenAvkorting" : 4768
                  }, {
                    "ytelsesKomponentType" : "TP",
                    "belopTilUtbetaling" : 8514,
                    "belopUtenAvkorting" : 8514
                  } ],
                  "benyttetSivilstand" : "ENSLIG",
                  "uforetidspunkt" : null
                } ]
              },
              "brukersBarnListe" : {
                "brukersBarnListe" : [ ]
              },
              "status" : "INNV"
            }
        """.trimIndent()

//        println(response)
        assertEquals(expected, response)
    }

    @Test
    fun `sjekk om resultat er gyldig pensjoninfo`() {
        val mockVedtakid = "213123333"
        val mockClient = fraFil("BARNEP-PlukkBestOpptjening.xml")

        val mockController = PensjonController(mockClient, auditLogger,)
        mockController.initMetrics()
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$mockVedtakid/pensjoninfo")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
{
  "avdod" : {
    "avdod" : null,
    "avdodMor" : "19037019618",
    "avdodFar" : "13127425838",
    "avdodAktorId" : null,
    "avdodMorAktorId" : "2794802022824",
    "avdodFarAktorId" : "2552933419183",
    "avdodBoddArbeidetUtland" : null,
    "avdodFarBoddArbeidetUtland" : true,
    "avdodMorBoddArbeidetUtland" : true,
    "avdodVurdereTrygdeavtale" : null,
    "avdodMorVurdereTrygdeavtale" : false,
    "avdodFarVurdereTrygdeavtale" : false
  },
  "inngangOgEksport" : {
    "unntakForutgaendeMedlemskap" : null,
    "unntakForutgaendeMedlemskapAvdod" : null,
    "unntakForutgaendeMedlemskapMor" : null,
    "unntakForutgaendeMedlemskapFar" : null,
    "minstTreArsFMNorgeAvdod" : null,
    "minstTreArsFMNorge" : null,
    "minstEtArFMNorge94Virk90Avdod" : null,
    "minstEtArFMNorge94Virk90" : null,
    "forutgaendeMedlemskapAvdod" : null,
    "forutgaendeMedlemskap" : null,
    "oppfyltEtterGamleReglerAvdod" : null,
    "oppfyltEtterGamleRegler" : null,
    "oppfyltVedSammenleggingAvdod" : null,
    "oppfyltVedSammenlegging" : null,
    "minstTreArTrygdetidNorgeKap19" : null,
    "minstTreArTrygdetidNorgeKap19Avdod" : null,
    "minstTreArTrygdetidNorgeKap20" : null,
    "minstTreArTrygdetidNorgeKap20Avdod" : null,
    "forutgaendeTrygdetid" : null
  },
  "person" : {
    "pid" : "18050776456",
    "aktorId" : "2304193180107"
  },
  "sakAlder" : {
    "sakType" : "BARNEP",
    "uttakFor67" : false
  },
  "trygdeavtale" : {
    "erArt10BruktGP" : null,
    "erArt10BruktTP" : null,
    "erArt10BruktGPAvdod" : null,
    "erArt10BruktTPAvdod" : null,
    "erArt10BruktGPMor" : null,
    "erArt10BruktTPMor" : null,
    "erArt10BruktGPFar" : null,
    "erArt10BruktTPFar" : null
  },
  "trygdetidAvdodFarListe" : {
    "trygdetidAvdodFarListe" : [ {
      "fom" : "1986-03-19",
      "tom" : "1996-12-31",
      "land" : "Norge",
      "ikkeProrata" : false,
      "poengIInnAr" : false,
      "poengIUtAr" : false
    }, {
      "fom" : "2010-02-01",
      "tom" : "2020-11-30",
      "land" : "Norge",
      "ikkeProrata" : false,
      "poengIInnAr" : false,
      "poengIUtAr" : false
    } ]
  },
  "trygdetidAvdodListe" : {
    "trygdetidAvdodListe" : [ ]
  },
  "trygdetidAvdodMorListe" : {
    "trygdetidAvdodMorListe" : [ {
      "fom" : "1990-12-13",
      "tom" : "1998-12-31",
      "land" : "Norge",
      "ikkeProrata" : false,
      "poengIInnAr" : false,
      "poengIUtAr" : false
    }, {
      "fom" : "2010-03-01",
      "tom" : "2014-11-30",
      "land" : "Norge",
      "ikkeProrata" : false,
      "poengIInnAr" : false,
      "poengIUtAr" : false
    } ]
  },
  "trygdetidListe" : {
    "trygdetidListe" : [ {
      "fom" : "2007-05-18",
      "tom" : "2022-02-10",
      "land" : "Norge",
      "ikkeProrata" : false,
      "poengIInnAr" : false,
      "poengIUtAr" : false
    } ]
  },
  "vedtak" : {
    "virkningstidspunkt" : "2021-01-01",
    "kravGjelder" : "F_BH_MED_UTL",
    "kravVelgType" : "FORELDRELOS",
    "hovedytelseTrukket" : false,
    "barnetilleggTrukket" : false,
    "ektefelletilleggTrukket" : false,
    "boddArbeidetUtland" : false,
    "vurdereTrygdeAvtale" : false,
    "datoFattetVedtak" : "2022-02-10",
    "vedtakStatus" : "IVERKS",
    "vedtaksDato" : "2022-02-10"
  },
  "vilkarsvurderingListe" : {
    "vilkarsvurderingListe" : [ {
      "fom" : "2021-01-01",
      "tom" : null,
      "vilkarsvurderingUforetrygd" : null,
      "vilkarsvurderingMedlemstid" : null,
      "resultatHovedytelse" : "INNV",
      "resultatBarnetillegg" : null,
      "resultatEktefelletillegg" : null,
      "resultatFasteUtgifter" : null,
      "resultatGjenlevendetillegg" : null,
      "avslagHovedytelse" : null,
      "avslagBarnetillegg" : null,
      "avslagEktefelletillegg" : null,
      "avslagFasteUtgifter" : null,
      "avslagGjenlevendetillegg" : null
    } ]
  },
  "ytelsePerMaanedListe" : {
    "ytelsePerMaanedListe" : [ {
      "fom" : "2021-01-01",
      "tom" : "2021-04-30",
      "mottarMinstePensjonsniva" : false,
      "gjenlevendetilleggAnvendtAlderspensjon" : null,
      "yrkesskadeAnvendtAlderspensjon" : null,
      "vinnendeBeregningsmetode" : "FOLKETRYGD",
      "vinnendeBeregningsmetodeKap20" : null,
      "vurdertBeregningsmetodeFolketrygd" : true,
      "vurdertBeregningsmetodeEOS" : false,
      "vurdertBeregningsmetodeNordisk" : false,
      "belop" : 14358,
      "belopUtenAvkorting" : 14358,
      "ytelseskomponentListe" : [ {
        "ytelsesKomponentType" : "GP",
        "belopTilUtbetaling" : 7179,
        "belopUtenAvkorting" : 7179
      }, {
        "ytelsesKomponentType" : "ST",
        "belopTilUtbetaling" : 7179,
        "belopUtenAvkorting" : 7179
      } ],
      "benyttetSivilstand" : "ENSLIG",
      "uforetidspunkt" : null
    }, {
      "fom" : "2021-05-01",
      "tom" : null,
      "mottarMinstePensjonsniva" : false,
      "gjenlevendetilleggAnvendtAlderspensjon" : null,
      "yrkesskadeAnvendtAlderspensjon" : null,
      "vinnendeBeregningsmetode" : "FOLKETRYGD",
      "vinnendeBeregningsmetodeKap20" : null,
      "vurdertBeregningsmetodeFolketrygd" : true,
      "vurdertBeregningsmetodeEOS" : false,
      "vurdertBeregningsmetodeNordisk" : false,
      "belop" : 15074,
      "belopUtenAvkorting" : 15074,
      "ytelseskomponentListe" : [ {
        "ytelsesKomponentType" : "GP",
        "belopTilUtbetaling" : 7537,
        "belopUtenAvkorting" : 7537
      }, {
        "ytelsesKomponentType" : "ST",
        "belopTilUtbetaling" : 7537,
        "belopUtenAvkorting" : 7537
      } ],
      "benyttetSivilstand" : "ENSLIG",
      "uforetidspunkt" : null
    } ]
  },
  "brukersSakerListe" : null,
  "brukersBarnListe" : null,
  "kravHistorikkListe" : null,
  "ektefellePartnerSamboerListe" : null
}
        """.trimIndent()
        assertEquals(expected, response)
    }


    fun fraFil(responseXMLfilename: String): PensjonsinformasjonClient {
        val resource = ResourceUtils.getFile("classpath:pensjonsinformasjon/$responseXMLfilename").readText()
        val readXMLresponse = ResponseEntity(resource, HttpStatus.OK)

        val mockRestTemplate: RestTemplate = mockk()

        every { mockRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } returns readXMLresponse
        val pensjonsinformasjonClient = PensjonsinformasjonClient(mockRestTemplate, PensjonRequestBuilder())
        pensjonsinformasjonClient.initMetrics()
        return pensjonsinformasjonClient
    }
}

