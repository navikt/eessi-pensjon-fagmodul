package no.nav.eessi.pensjon.api.pensjon

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PesysService
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

private const val AKTOERID = "1234567890123"
private const val SOME_SAKID = "10000"
private const val SOME_VEDTAK_ID = "213123333"
private const val KRAV_ID = "345345"

@Suppress("DEPRECATION") // hentKunSakType / hentAltPaaAktoerId
class PensjonControllerTest {

//    private var pensjonsinformasjonClient: PensjonsinformasjonClient = mockk()
    private var pesysService: PesysService = mockk()

    @SpyK
    private var auditLogger: AuditLogger = AuditLogger()

    @InjectMockKs
    private val controller = PensjonController(PesysService(mockk()), auditLogger)
    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `hentPensjonSakType gitt en aktoerId saa slaa opp fnr og hent deretter sakstype`() {
//        every { pensjonsinformasjonClient.hentKunSakType(SOME_SAKID, AKTOERID) } returns Pensjontype(SOME_SAKID, "Type")
        controller.hentPensjonSakType(SOME_SAKID, AKTOERID)

//        verify { pensjonsinformasjonClient.hentKunSakType(eq(SOME_SAKID), eq(AKTOERID)) }
    }

    @Test
    fun `hentPensjonSakType gitt at det svar fra PESYS er tom`() {
//        every { pensjonsinformasjonClient.hentKunSakType(SOME_SAKID, AKTOERID) } returns Pensjontype(SOME_SAKID, "")
        val response = controller.hentPensjonSakType(SOME_SAKID, AKTOERID)

//        verify { pensjonsinformasjonClient.hentKunSakType(eq(SOME_SAKID), eq(AKTOERID)) }

        assertEquals(getExpectedKunSakType(), response?.body)
    }

    private fun getExpectedKunSakType() = """
                {
                  "sakId" : "10000",
                  "sakType" : ""
                }
            """.trimIndent()

    @Test
    fun `hentPensjonSakType gitt at det svar feiler fra PESYS`() {
//        every { pensjonsinformasjonClient.hentKunSakType(SOME_SAKID, AKTOERID) } returns Pensjontype(SOME_SAKID, "")
        val response = controller.hentPensjonSakType(SOME_SAKID, AKTOERID)

//        verify { pensjonsinformasjonClient.hentKunSakType(eq(SOME_SAKID), eq(AKTOERID)) }
        assertEquals(getExpectedKunSakType(), response?.body)
    }

    @Test
    fun `Gitt det finnes pensjonsak paa aktoer saa skal det returneres en liste over alle saker til aktierid`() {
//        val mockpen = Pensjonsinformasjon()

//        val mocksak1 = V1Sak()
//        mocksak1.sakId = 1010
//        mocksak1.status = INNV.name
//        mocksak1.sakType = ALDER.name
//        mockpen.brukersSakerListe = V1BrukersSakerListe()
//        mockpen.brukersSakerListe.brukersSakerListe.add(mocksak1)
//
//        val mocksak2 = V1Sak()
//        mocksak2.sakId = 2020
//        mocksak2.status = AVSL.name
//        mocksak2.sakType = UFOREP.name
//        mockpen.brukersSakerListe.brukersSakerListe.add(mocksak2)
//
//        every { pensjonsinformasjonClient.hentAltPaaAktoerId(AKTOERID) } returns mockpen

        val result = controller.hentPensjonSakIder(AKTOERID)
//        verify { pensjonsinformasjonClient.hentAltPaaAktoerId(eq(AKTOERID)) }

//        verify { pensjonsinformasjonClient.hentAltPaaAktoerId(eq(AKTOERID)) }

        assertEquals(2, result.size)
        val expected1 = PensjonSak("1010", ALDER.name, LOPENDE)
        assertEquals(expected1.toJson(), result.first().toJson())
        val expected2 = PensjonSak("2020", UFOREP.name, AVSLUTTET)
        assertEquals(expected2.toJson(), result.last().toJson())

        assertEquals(AVSLUTTET, expected2.sakStatus)
    }

//    @Test
//    fun `Gitt det ikke finnes pensjonsak paa aktoer saa skal det returneres et tomt svar tom liste`() {
////        val mockpen = Pensjonsinformasjon()
//        mockpen.brukersSakerListe = V1BrukersSakerListe()
//
////        every { (pensjonsinformasjonClient.hentAltPaaAktoerId(AKTOERID)) } returns mockpen
//
//        val result = controller.hentPensjonSakIder(AKTOERID)
//        verify(exactly = 1) { pensjonsinformasjonClient.hentAltPaaAktoerId(any())
//            assertEquals(0, result.size)
//        }
//    }

    @Test
    fun `sjekk paa forskjellige verdier av sakstatus fra pensjoninformasjon konvertere de til enum`() {
        assertEquals(TIL_BEHANDLING, SakStatus.from("TIL_BEHANDLING"))
        assertEquals(AVSLUTTET, SakStatus.from("AVSL"))
        assertEquals(LOPENDE, SakStatus.from("INNV"))
        assertEquals(OPPHOR, SakStatus.from("OPPHOR"))
        assertEquals(UKJENT, SakStatus.from("CrazyIkkeIbrukTull"))
    }

    @Test
    fun `hentKravDato skal gi en data hentet fra aktorid og vedtaksid `() {
        val kravDato = "2020-01-01"

//        every { pensjonsinformasjonClient.hentKravDatoFraAktor(AKTOERID, SOME_SAKID, KRAV_ID) } returns kravDato

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/pensjon/kravdato/saker/$SOME_SAKID/krav/$KRAV_ID/aktor/$AKTOERID")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("""{ "kravDato": "$kravDato" }""", response)
    }

    @Test
    fun `hentKravDato skal gi 400 og feilmelding ved manglende parameter`() {
        val kravId = ""

        mockMvc.perform(
            MockMvcRequestBuilders.get("/pensjon/kravdato/saker/$SOME_SAKID/krav/$kravId/aktor/$AKTOERID")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun uthentingAvUforeTidspunkt() {
//        val mockClient = fraFil("VEDTAK-UT-MUTP.xml")
        val mockController = PensjonController(PesysService(mockk()), auditLogger)
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$SOME_VEDTAK_ID/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
            )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = ufoereTidspunkt("2020-02-29", "2015-12-01")
        assertEquals(expected, response)
    }

    @Test
    fun uthentingAvUforeTidspunktMedGMTZ() {
//        val mockClient = fraFil("VEDTAK-UT-MUTP-GMTZ.xml")
        val mockController = PensjonController(PesysService(mockk()), auditLogger)
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$SOME_VEDTAK_ID/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = ufoereTidspunkt("2020-03-01", "2015-12-01")
        assertEquals(expected, response)
    }

    @Test
    fun uthentingAvUforeTidspunktSomErTom() {
//        val mockClient = fraFil("VEDTAK-UT.xml")
        val mockController = PensjonController(PesysService(mockk()), auditLogger)
        val mockMvc2 = MockMvcBuilders.standaloneSetup(mockController).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$SOME_VEDTAK_ID/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = ufoereTidspunkt()

        assertEquals(expected, response)
    }

    private fun ufoereTidspunkt(ufoereTidspunkt: String? = null, virkningsTidspunkt: String? = null) = """
                {
                  "uforetidspunkt" : ${if(ufoereTidspunkt == null) null else "\"$ufoereTidspunkt\""},
                  "virkningstidspunkt" : ${if(virkningsTidspunkt == null) null else  "\"$virkningsTidspunkt\""}
                }
            """.trimIndent()

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

//        val verdi = controller.transformXMLGregorianCalendarToJson(xmlDate)
//        assertEquals(resultat, verdi.toString())
//        assert( verdi.dayOfMonth == 1 == check )
    }

    @Test
    fun `Sjekke for hentKravDatoFraAktor ikke kaster en unormal feil`() {
        MDC.put("x_request_id","AAA-BBB")
//        every { pensjonsinformasjonClient.hentKravDatoFraAktor(any(), any(), any()) } returns null

        val result = controller.hentKravDatoFraAktor(SOME_SAKID, KRAV_ID, AKTOERID)
        JSONAssert.assertEquals(
            """{"success": false, "error": "Feiler å hente kravDato", "uuid": "AAA-BBB"}""",
            result?.body, true
        )
    }

    @Test
    fun `sjekk om resultat er gyldig pensjoninfo`() {
        val mockVedtakid = SOME_VEDTAK_ID
//        val mockClient = fraFil("BARNEP-PlukkBestOpptjening.xml")

        val mockController = PensjonController(PesysService(mockk()), auditLogger)
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


//    fun fraFil(responseXMLfilename: String): PensjonsinformasjonClient {
//        val resource = ResourceUtils.getFile("classpath:pensjonsinformasjon/$responseXMLfilename").readText()
//        val readXMLresponse = ResponseEntity(resource, HttpStatus.OK)
//
//        val mockRestTemplate: RestTemplate = mockk()
//
//        every { mockRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } returns readXMLresponse
//        val pensjonsinformasjonClient = PensjonsinformasjonClient(mockRestTemplate, PensjonRequestBuilder())
//        pensjonsinformasjonClient.initMetrics()
//        return pensjonsinformasjonClient
//    }
}

