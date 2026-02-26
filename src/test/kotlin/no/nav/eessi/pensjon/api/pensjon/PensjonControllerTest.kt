package no.nav.eessi.pensjon.api.pensjon

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiFellesDto
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PesysService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate
import java.util.*

private const val AKTOERID = "1234567890123"
private const val SOME_SAKID = "10000"
private val SOME_SAK_TYPE = EessiFellesDto.EessiSakType.ALDER
private const val SOME_VEDTAK_ID = "213123333"
private const val KRAV_ID = "345345"

@Suppress("DEPRECATION") // hentKunSakType / hentAltPaaAktoerId
class PensjonControllerTest {

//    private var pensjonsinformasjonClient: PensjonsinformasjonClient = mockk()
    private var pesysService: PesysService = mockk()
    private var innhentingService: InnhentingService = mockk()

    @SpyK
    private var auditLogger: AuditLogger = AuditLogger()

    @InjectMockKs
    private val controller = PensjonController(pesysService, auditLogger, innhentingService)
    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @BeforeEach
    fun setup(){
    }

    @Test
    fun `hentPensjonSakType gitt en aktoerId saa slaa opp fnr og hent deretter sakstype`() {
        every { pesysService.hentSaktype(SOME_SAKID) } returns SOME_SAK_TYPE

//        every { pensjonsinformasjonClient.hentKunSakType(SOME_SAKID, AKTOERID) } returns Pensjontype(SOME_SAKID, "Type")
        controller.hentPensjonSakType(SOME_SAKID, AKTOERID)

//        verify { pensjonsinformasjonClient.hentKunSakType(eq(SOME_SAKID), eq(AKTOERID)) }
    }

    @Test
    fun `hentPensjonSakType gitt at det svar fra PESYS er tom`() {

        every { pesysService.hentSaktype(SOME_SAKID) } returns null

//        every { pensjonsinformasjonClient.hentKunSakType(SOME_SAKID, AKTOERID) } returns Pensjontype(SOME_SAKID, "")
//        every { pesysService.hentSaktype(SOME_SAKID) } returns EessiFellesDto.EessiSakType.valueOf(SOME_SAKID)
        val response = controller.hentPensjonSakType(SOME_SAKID, AKTOERID)

//        verify { pensjonsinformasjonClient.hentKunSakType(eq(SOME_SAKID), eq(AKTOERID)) }

        assertEquals("Sakstype ikke funnet for sakId: $SOME_SAKID", response?.body)
    }

    private fun getExpectedKunSakType() = """
                {
                  "sakId" : "10000",
                  "sakType" : ""
                }
            """.trimIndent()

    @Test
    fun `hentPensjonSakType gitt at pesys gir null`() {
        every { pesysService.hentSaktype(SOME_SAKID)} returns null
        val response = controller.hentPensjonSakType(SOME_SAKID, AKTOERID)

        verify { pesysService.hentSaktype(SOME_SAKID) }
        assertEquals("Sakstype ikke funnet for sakId: $SOME_SAKID", response?.body)
    }

    @Test
    fun `Gitt det finnes pensjonsak paa aktoer saa skal det returneres en liste over alle saker til aktierid`() {
        every { innhentingService.hentFnrfraAktoerService(any()) } returns NorskIdent(AKTOERID)
        val saker = listOf(
            EessiFellesDto.PensjonSakDto(
                "1010",
                EessiFellesDto.EessiSakType.ALDER,
                EessiFellesDto.EessiSakStatus.INNV
            ),
            EessiFellesDto.PensjonSakDto(
                "2020",
                EessiFellesDto.EessiSakType.UFOREP,
                EessiFellesDto.EessiSakStatus.AVSL
            )
        )

        every { pesysService.hentSakListe(AKTOERID) } returns saker

        val result = controller.hentPensjonSakIder(AKTOERID)
        verify { pesysService.hentSakListe(AKTOERID) }

        assertEquals(2, result.size)
        val expected1 = PensjonSak("1010", ALDER.name, LOPENDE)
        assertEquals(expected1.toJson(), result.first().toJson())
        val expected2 = PensjonSak("2020", UFOREP.name, AVSLUTTET)
        assertEquals(expected2.toJson(), result.last().toJson())

        assertEquals(AVSLUTTET, expected2.sakStatus)
    }

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

        every { pesysService.hentKravdato( KRAV_ID, ) } returns LocalDate.parse(kravDato)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/pensjon/kravdato/saker/$SOME_SAKID/krav/$KRAV_ID/aktor/$AKTOERID")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x_request_id", UUID.randomUUID().toString())

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
        val ufoereTidspunkt = LocalDate.now()
        val virkningsTidspunkts = LocalDate.now().plusDays(10)
        val dto = EessiFellesDto.EessiUfoeretidspunktDto(ufoereTidspunkt, virkningsTidspunkts)

        every { pesysService.hentUfoeretidspunktOnVedtak(any()) } returns dto
        val mockMvc2 = MockMvcBuilders.standaloneSetup(controller).build()

        val result = mockMvc2.perform(
            MockMvcRequestBuilders.get("/pensjon/vedtak/$SOME_VEDTAK_ID/uforetidspunkt")
                .contentType(MediaType.APPLICATION_JSON)
            )
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals(dto, mapJsonToAny<EessiFellesDto.EessiUfoeretidspunktDto>(response))
    }

    @Test
    fun `Sjekke for hentKravDatoFraAktor ikke kaster en unormal feil`() {
        MDC.put("x_request_id","AAA-BBB")
        every { pesysService.hentKravdato(any()) } returns null

        val response = controller.hentKravDatoFraAktor(SOME_SAKID, KRAV_ID, AKTOERID)

        JSONAssert.assertEquals(
            """{"success": false, "error": "Feiler å hente kravDato", "uuid": "AAA-BBB"}""",
            response?.body, true
        )
    }

    @Test
    @Disabled("IKKE LENGER NØDVENDIG, Slettes")
    fun `sjekk om resultat er gyldig pensjoninfo`() {
        val mockVedtakid = SOME_VEDTAK_ID
//        val mockClient = fraFil("BARNEP-PlukkBestOpptjening.xml")

        val mockController = PensjonController(PesysService(mockk()), auditLogger, innhentingService)
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

