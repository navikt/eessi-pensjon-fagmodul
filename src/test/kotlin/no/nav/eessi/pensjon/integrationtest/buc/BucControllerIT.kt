package no.nav.eessi.pensjon.integrationtest.buc


import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.FunctionAnswer
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.P2100
import no.nav.eessi.pensjon.eux.model.SedType.P4000
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Npid
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.Month

private const val NPID = "01220049651"
private const val SAKNR = "1203201322"
private const val AVDOD_FNR = "01010100001"
private const val VEDTAKID = "2312123123123"
private const val GJENLEVENDE_FNR = "1234567890000"
private const val GJENLEV_AKTOERID = "1123123123123123"

private val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
@EmbeddedKafka
@MockkBeans(
    MockkBean(name = "personService", classes = [PersonService::class]),
    MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "kodeverkRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "prefillOAuthTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxSystemRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class]),
    MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxNavIdentRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pensjonsinformasjonClient", classes = [PensjonsinformasjonClient::class])
)
internal class BucControllerIT: BucBaseTest() {

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var restSafTemplate: RestTemplate

    @Autowired
    private lateinit var euxNavIdentRestTemplate: RestTemplate

    @Autowired
    private lateinit var pensjonsinformasjonClient: PensjonsinformasjonClient

    @Autowired
    private lateinit var personService: PersonService

    @Autowired
    private lateinit var gcpStorageService: GcpStorageService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Gitt det ikke finnes en P_BUC_02 med SED der avdød ikke finnes så skal det returneres et tomt resultat`() {
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID)) }returns NorskIdent(GJENLEVENDE_FNR)

        val rinaBuc02url = dummyRinasakAvdodUrl(AVDOD_FNR)
        every { euxNavIdentRestTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(AVDOD_FNR)
        every { euxNavIdentRestTemplate.exchange( rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(GJENLEV_AKTOERID))

        every { restSafTemplate.exchange(eq("/"), HttpMethod.POST, httpEntity, String::class.java) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )


        val result = mockMvc.perform(get("/buc/rinasaker/$GJENLEV_AKTOERID/saknr/null/avdod/$AVDOD_FNR")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("[]", response)
    }

    @Test
    fun `Gitt det finnes gjenlevende og en avdød på P_BUC_02 så skal det hentes og returneres en liste av buc`() {
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()

        every { pensjonsinformasjonClient.hentAltPaaVedtak(VEDTAKID) } returns mockVedtak(AVDOD_FNR, GJENLEV_AKTOERID)

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID)) } returns NorskIdent(GJENLEVENDE_FNR)

        //buc02 - avdød rinasak
        val rinaSakerBuc02 = listOf(dummyRinasak("1010", "P_BUC_02"), dummyRinasak("9859667", "P_BUC_01"))
        val rinaBuc02url = dummyRinasakUrl(AVDOD_FNR)

        every { euxNavIdentRestTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(rinaSakerBuc02.toJson())

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(GJENLEV_AKTOERID))
        every { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )
        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { euxNavIdentRestTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(get("/buc/rinasaker/$GJENLEV_AKTOERID/saknr/$SAKNR/vedtak/$VEDTAKID")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            [{"euxCaseId":"1010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"01010100001","kilde":"AVDOD"}]
        """.trimIndent()

        verify(atLeast = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, any(), String::class.java) }
        verify(atLeast = 1) { restSafTemplate.exchange("/", HttpMethod.POST, eq(httpEntity), String::class.java) }
        JSONAssert.assertEquals(expected, response, false)

    }

        @Test
        fun `Gitt det finnes gjenlevende og en avdød på buc02 og fra SAF så skal det hentes og lever en liste av buc`() {
            val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()

            every { pensjonsinformasjonClient.hentAltPaaVedtak(VEDTAKID) } returns mockVedtak(AVDOD_FNR, GJENLEV_AKTOERID)
            //gjenlevende aktoerid -> gjenlevendefnr
            every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID)) } returns NorskIdent(GJENLEVENDE_FNR)
            //buc02 - avdød rinasak
            val rinaSakerBuc02 = listOf(dummyRinasak("1010", "P_BUC_02"))
            val rinaBuc02url = dummyRinasakAvdodUrl(AVDOD_FNR)
            every { euxNavIdentRestTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(rinaSakerBuc02.toJson())

            //buc02
            val docItems = listOf(
                documentsItem("1", "sent", P2100),
                documentsItem("2", "draft", P4000))
            val buc02 = Buc(id = "1010", internationalId = "1000100010001000", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

            val rinabucpath = "/buc/1010"
            every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

            //saf (vedlegg meta) gjenlevende
            val httpEntity = dummyHeader(dummySafReqeust(GJENLEV_AKTOERID))
            every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina("1010"))

            val rinaSafUrl = dummyRinasakUrl(euxCaseId =  "1010")
            every { euxNavIdentRestTemplate.exchange( eq(rinaSafUrl.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( rinaSakerBuc02.toJson())

            //buc02 sed
            val rinabucdocumentidpath = "/buc/1010/sed/1"
            every { euxNavIdentRestTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )

            every { euxNavIdentRestTemplate.exchange( "/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())
            every { gcpStorageService.gjennySakFinnes(any()) } returns false

            val response = mockMvc.perform(get("/buc/rinasaker/$GJENLEV_AKTOERID/saknr/$SAKNR/vedtak/$VEDTAKID")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn().response.contentAsString

            val bucViewResponse = """
                [{"euxCaseId":"1010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"01010100001","kilde":"SAF","internationalId":"1000100010001000"}]
            """.trimIndent()

            assertTrue {response.contains(AVDOD_FNR)}
            JSONAssert.assertEquals(response, bucViewResponse, false)

            verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }
            verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        }

    @Test
    fun `Gitt det finnes gjenlevende og en avdød kun fra SAF så skal det hentes og lever en liste av buc med subject`() {
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()

        every { pensjonsinformasjonClient.hentAltPaaVedtak(VEDTAKID) } returns mockVedtak(AVDOD_FNR, GJENLEV_AKTOERID)

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID)) } returns NorskIdent(GJENLEVENDE_FNR)

        //buc02 - avdød rinasak
        val rinaBuc02url = dummyRinasakAvdodUrl(AVDOD_FNR)
        every { euxNavIdentRestTemplate.exchange( eq(rinaBuc02url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(GJENLEVENDE_FNR)
        every { euxNavIdentRestTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc02
        val docItems = listOf(
            documentsItem("1", "sent", P2100),
            documentsItem("2", "draft", P4000),
        )
        val buc02 = Buc(id = "1010", internationalId = "1000100010001000", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/1010"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(GJENLEV_AKTOERID))

        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns  ResponseEntity.ok().body(  dummySafMetaResponseMedRina("1010") )

        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { euxNavIdentRestTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )

        every { euxNavIdentRestTemplate.exchange( "/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(get("/buc/rinasaker/$GJENLEV_AKTOERID/saknr/$SAKNR/vedtak/$VEDTAKID")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val bucViewResponse = """
            [{"euxCaseId":"1010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"01010100001","kilde":"SAF","internationalId":"1000100010001000"}]
        """.trimIndent()

        val response = result.response.getContentAsString(charset("UTF-8"))
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/1010", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }
        assertTrue { response.contains(AVDOD_FNR) }
        JSONAssert.assertEquals(response, bucViewResponse, false)

    }

    @Test
    fun `Gitt det finnes en gjenlevende og avdød hvor buc05 buc06 og buc10 finnes Så skal det returneres en liste av buc`() {

        every { pensjonsinformasjonClient.hentAltPaaVedtak(VEDTAKID)  } returns mockVedtak(AVDOD_FNR, GJENLEV_AKTOERID)

        //gjenlevende aktoerid -> gjenlevendefnr
        every {personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID))  } returns NorskIdent(GJENLEVENDE_FNR)

        //buc05, 06 og 10 avdød rinasaker
        val rinaSakerBuc05 = listOf(
            dummyRinasak("2020", "P_BUC_05"),
            dummyRinasak("3030", "P_BUC_06"),
            dummyRinasak("4040", "P_BUC_10")
        )

        every { euxNavIdentRestTemplate.exchange( eq("/rinasaker?fødselsnummer=01010100001&status=\"open\""), eq(HttpMethod.GET), null, eq(String::class.java))  } returns ResponseEntity.ok().body( rinaSakerBuc05.toJson())

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(GJENLEV_AKTOERID))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina("2020") )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val expected = """
            [
                {"euxCaseId":"2020","buctype":"P_BUC_05","aktoerId":"1123123123123123","saknr":"2020","avdodFnr":"01010100001","kilde":"SAF","internationalId":null},
                {"euxCaseId":"3030","buctype":"P_BUC_06","aktoerId":"1123123123123123","saknr":"2020","avdodFnr":"01010100001","kilde":"AVDOD","internationalId":null},
                {"euxCaseId":"4040","buctype":"P_BUC_10","aktoerId":"1123123123123123","saknr":"2020","avdodFnr":"01010100001","kilde":"AVDOD","internationalId":null}
            ]

        """.trimIndent()

        val result = mockMvc.perform(get("/buc/rinasaker/$GJENLEV_AKTOERID/saknr/2020/vedtak/$VEDTAKID")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        println(response)

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange(eq("/") , HttpMethod.POST, eq(httpEntity), String::class.java) }
        JSONAssert.assertEquals(response, expected, false)
    }

    @Test
    fun `Hent mulige rinasaker for fnr fra euxrina`() {
        val pesyssaknr = "100001000"

        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID)) } returns NorskIdent(GJENLEVENDE_FNR)

        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250)
            ResponseEntity.ok().body(listOf(dummyRinasak("5195021", "P_BUC_03"), dummyRinasak("5922554", "P_BUC_03") ).toJson() )
        })
        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc("5922554", documents = null, internasjonalId = null) )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc("5195021", documents = null, internasjonalId = null) )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(
                get("/buc/rinasaker/euxrina/$GJENLEV_AKTOERID/pesyssak/$pesyssaknr")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
                [{"euxCaseId":"5195021","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"},
                {"euxCaseId":"5922554","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }


    @Test
    fun `Hent mulige rinasaker for NPID fra euxrina`() {
        val pesyssaknr = "100001000"

        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID)) } returns NorskIdent("")
        every { personService.hentIdent(IdentGruppe.NPID, AktoerId(GJENLEV_AKTOERID)) } returns Npid(NPID)

        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01220049651&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250)
            ResponseEntity.ok().body(listOf(dummyRinasak("5195021", "P_BUC_03"), dummyRinasak("5922554", "P_BUC_03") ).toJson() ) })

        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc(
            "5922554",
            documents = null,
            internasjonalId = null
        ) )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc(
            "5195021",
            documents = null,
            internasjonalId = null
        ) )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(
            get("/buc/rinasaker/euxrina/$GJENLEV_AKTOERID/pesyssak/$pesyssaknr")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
                [{"euxCaseId":"5195021","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"},
                {"euxCaseId":"5922554","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }

    @Test
    fun `Hent mulige rinasaker for NPID fra euxrina uten å vise gjenny saker`() {
        val pesyssaknr = "100001000"

        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(GJENLEV_AKTOERID)) } returns NorskIdent("")
        every { personService.hentIdent(IdentGruppe.NPID, AktoerId(GJENLEV_AKTOERID)) } returns Npid(NPID)

        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01220049651&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250)
            ResponseEntity.ok().body(listOf(dummyRinasak("5195021", "P_BUC_03"), dummyRinasak("5922554", "P_BUC_03"), dummyRinasak("000001", "P_BUC_02") ).toJson() ) })

        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc("592254", documents = null, internasjonalId = null) )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc("5195021", documents = null, internasjonalId = null) )
        every { euxNavIdentRestTemplate.exchange( "/buc/000001", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc("000001", P_BUC_02, null, null) )
        every { gcpStorageService.gjennySakFinnes("5922554") } returns false
        every { gcpStorageService.gjennySakFinnes("5195021") } returns false
        every { gcpStorageService.gjennySakFinnes("000001") } returns true

        val result = mockMvc.perform(
            get("/buc/rinasaker/euxrina/$GJENLEV_AKTOERID/pesyssak/$pesyssaknr")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
                [{"euxCaseId":"5195021","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"},
                {"euxCaseId":"5922554","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }
    private fun documentsItem(id: String, status: String, sedType: SedType) =
        DocumentsItem(
            id = id,
            creationDate = lastupdate,
            lastUpdate = lastupdate,
            status = status,
            type = sedType,
            direction = "OUT",
            receiveDate = null
        )

    private fun buc(
        id: String?,
        bucType: BucType? = P_BUC_03,
        documents: List<DocumentsItem>?,
        internasjonalId: String?
    ) =
        Buc(
            id,
            bucType.toString(),
            documents = documents,
            internationalId = internasjonalId
        ).toJson()

}