package no.nav.eessi.pensjon.integrationtest.buc

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.FunctionAnswer
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.Month

private const val FNR = "1234567890000"
private const val AVDOD_FNR = "01010100001"
private const val AVDOD_FNR2 = "6789567890000"
private const val AVDOD_AKTOERID = "8888123123123123"

private const val SAKNR = "1203201322"
private const val SAKNR2 = "100001000"
private const val EUXCASE_ID = "80001"
private const val EUXCASE_ID2 = "900001"
private const val VEDTAKID = "2312123123123"
private const val AKTOERID = "1123123123123123"

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
    MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class]),
    MockkBean(name = "euxNavIdentRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pensjonsinformasjonClient", classes = [PensjonsinformasjonClient::class])
)
internal class BucViewDetaljIntegrationTest: BucBaseTest() {

    @Autowired
    private lateinit var euxNavIdentRestTemplate: RestTemplate

    @Autowired
    private lateinit var safGraphQlOidcRestTemplate: RestTemplate

    @Autowired
    private lateinit var pensjonsinformasjonClient: PensjonsinformasjonClient

    @Autowired
    private lateinit var personService: PersonService

    @Autowired
    private lateinit var gcpStorageService: GcpStorageService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Gitt korrekt request med aktorid med avdod Så skal en BucAndSedView json vises`() {
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)

        //buc02
        val docItems = listOf(
            documentsItem("1","sent", P2100),
            documentsItem("2","draft", P4000)
        )
        val buc02 = Buc(id = EUXCASE_ID, processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$EUXCASE_ID"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //buc02 sed
        val rinabucdocumentidpath = "/buc/$EUXCASE_ID/sed/1"
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()

        every { euxNavIdentRestTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$EUXCASE_ID/aktoerid/$AKTOERID/saknr/$SAKNR/avdodfnr/$AVDOD_FNR/kilde/$AVDOD")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_02","caseId":"$EUXCASE_ID","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":"2020-08-07","lastUpdate":"2020-08-07","institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P2100","conversations":null,"isSendExecuted":null,"id":"1","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":{"gjenlevende":{"fnr":"1234567890000"},"avdod":{"fnr":"01010100001"}}}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$EUXCASE_ID", HttpMethod.GET, null, String::class.java)  }
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$EUXCASE_ID/sed/1", HttpMethod.GET, null, String::class.java) }

    }

    @Test
    fun `Gitt korrekt request med aktorid med avdod og kilde saf Så skal en BucAndSedView json vises`() {
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)

        //buc02
        val docItems = listOf(
            documentsItem("1","sent", P15000, "IN"),
            documentsItem( "2", "draft", P4000)
        )
        val buc10 = Buc(id = EUXCASE_ID, processDefinitionName = "P_BUC_10", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$EUXCASE_ID"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc10.toJson() )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$EUXCASE_ID/aktoerid/$AKTOERID/saknr/$SAKNR/avdodfnr/$AVDOD_FNR/kilde/$SAF")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_10","caseId":"$EUXCASE_ID","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":"2020-08-07","lastUpdate":"2020-08-07","institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P15000","conversations":null,"isSendExecuted":null,"id":"1","direction":"IN","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":{"gjenlevende":{"fnr":"1234567890000"},"avdod":{"fnr":"01010100001"}}}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$EUXCASE_ID", HttpMethod.GET, null, String::class.java)  }

    }

    @Test
    fun `Gitt Det er en SingleBucRequest uten avdod skal det vises korrekt bucsedview resulat`() {
        //aktoerid -> fnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)

        //buc01
        val docItems = listOf(
            documentsItem("1", "sent", P2000),
            documentsItem("2", "draft", P4000)
        )
        val buc01 = Buc(id = EUXCASE_ID2, processDefinitionName = "P_BUC_01", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$EUXCASE_ID2"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc01.toJson() )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$EUXCASE_ID2/aktoerid/$AKTOERID/saknr/$SAKNR/kilde/$BRUKER")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_01","caseId":"900001","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":"2020-08-07","lastUpdate":"2020-08-07","institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P2000","conversations":null,"isSendExecuted":null,"id":"1","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":null}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$EUXCASE_ID2", HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `gitt en singleBucRequest uten avdod, og rinaid som ikke finnes så skal det kastes et NOT_FOUND exception`() {
        //aktoerid -> fnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)

        val rinabucpath = "/buc/$EUXCASE_ID2"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$EUXCASE_ID2/aktoerid/$AKTOERID/saknr/$SAKNR/kilde/$SAF")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val bucView = mapJsonToAny<BucAndSedView>(response)
        assertEquals("404 NOT_FOUND", bucView.error)

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$EUXCASE_ID2", HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `Hent mulige rinasaker for aktoer og saf med vedtak uten avdod`() {
        every { pensjonsinformasjonClient.hentAltPaaVedtak(VEDTAKID) } .answers( FunctionAnswer { Thread.sleep(56); mockVedtakUtenAvdod(AKTOERID) } )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR2/vedtak/$VEDTAKID")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals("[]", response)
    }

    @Test
    fun `Hent mulige rinasaker for aktoer og saf med vedtak med avdod`() {
        every { pensjonsinformasjonClient.hentAltPaaVedtak(VEDTAKID) } .answers( FunctionAnswer { Thread.sleep(56); mockVedtak(AVDOD_FNR2, AKTOERID) } )

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AVDOD_AKTOERID)) } returns NorskIdent(AVDOD_FNR2)

        val rinaSakerBuc02 = listOf(
            dummyRinasak("5010", P_BUC_02.name),
            dummyRinasak("3202", P_BUC_03.name),
            dummyRinasak("5005", AD_BUC_01.name),
            dummyRinasak("14675", P_BUC_06.name)
        )
        val rinaBuc02url = dummyRinasakAvdodUrl(AVDOD_FNR2)

        val answer = FunctionAnswer { Thread.sleep(220); ResponseEntity.ok().body(rinaSakerBuc02.toJson() ) }
        every { euxNavIdentRestTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } .answers(answer)

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(AKTOERID))
        every { safGraphQlOidcRestTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina( "5010", "344000" ) )

        every { euxNavIdentRestTemplate.exchange( "/buc/5010", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5010", processDefinitionName = "P_BUC_02").toJson() )
        every { euxNavIdentRestTemplate.exchange("/buc/344000", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "344000", processDefinitionName = "P_BUC_03").toJson() )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR/vedtak/$VEDTAKID")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        //data og spurt eux
        verify (exactly = 1) { safGraphQlOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
            [{"euxCaseId":"5010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"6789567890000","kilde":"SAF"},
            {"euxCaseId":"14675","buctype":"P_BUC_06","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"6789567890000","kilde":"AVDOD"},
            {"euxCaseId":"344000","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":null,"kilde":"SAF"}]

        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }

    @Test
    fun `Hent mulige rinasaker for aktoer og saf`() {
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(AKTOERID))
        every { safGraphQlOidcRestTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina( "5195021", "5922554" ) )

       //gjenlevende rinasak
        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250);  ResponseEntity.ok().body( listOf(dummyRinasak("5195021", "P_BUC_05") ).toJson() ) } )
        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5922554", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5195021", processDefinitionName = "P_BUC_03").toJson() )
        every { gcpStorageService.gjennySakFinnes("5195021") } returns false
        every { gcpStorageService.gjennySakFinnes("5922554") } returns false

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { safGraphQlOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
                [{"euxCaseId":"5195021","buctype":"P_BUC_05","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":null,"kilde":"BRUKER"},
                {"euxCaseId":"5922554","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":null,"kilde":"SAF"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }

    @Test
    fun `Hent mulige rinasaker for aktoer og saf uten å vise gjennybuc`() {
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(AKTOERID))
        every { safGraphQlOidcRestTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina( "5195021", "5922554" ) )

        //gjenlevende rinasak
        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250);  ResponseEntity.ok().body( listOf(dummyRinasak("5195021", "P_BUC_05") ).toJson() ) } )
        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5922554", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5195021", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/000001", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "000001", processDefinitionName = "P_BUC_02").toJson() )

        every { gcpStorageService.gjennySakFinnes("5922554") } returns false
        every { gcpStorageService.gjennySakFinnes("5195021") } returns false
        every { gcpStorageService.gjennySakFinnes("000001") } returns true

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR2")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { safGraphQlOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
                [{"euxCaseId":"5195021","buctype":"P_BUC_05","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"},
                {"euxCaseId":"5922554","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"SAF"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }

    @Test
    fun `Hent mulige rinasaker for aktoer uten vedtak og saf`() {
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(AKTOERID))
        every { safGraphQlOidcRestTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body( dummySafMetaResponse() )

        //gjenlevende rinasak
        val rinaSakerBuc = listOf(dummyRinasak("3010", "P_BUC_01"), dummyRinasak("75312", "P_BUC_03"))
        val rinaGjenlevUrl = dummyRinasakUrl(FNR)
        every { euxNavIdentRestTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( rinaSakerBuc.toJson())

        every { euxNavIdentRestTemplate.exchange( "/buc/3010", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "3010", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/75312", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "75312", processDefinitionName = "P_BUC_03").toJson() )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR2")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { safGraphQlOidcRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
            [{"euxCaseId":"3010","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null},{"euxCaseId":"75312","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)

        val requestlist = mapJsonToAny<List<BucView>>(response)

        assertEquals(2, requestlist.size)
        assertEquals("3010", requestlist.first().euxCaseId)
    }

    @Test
    fun `Gitt at vi får inn en ikke migrert rinasak i metadata fra saf og fra rina saa skal denne fltreres vekk i begge view`() {
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(AKTOERID)) } returns NorskIdent(FNR)
        every { safGraphQlOidcRestTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(dummyHeader(dummySafReqeust(AKTOERID))), eq(String::class.java)) } returns
                ResponseEntity.ok().body(  dummySafMetaResponseMedRina("9859667") )

        val rinaSakerBuc = listOf(dummyRinasak("3010", "P_BUC_01"), dummyRinasak("75312", "P_BUC_03"))
        every { euxNavIdentRestTemplate.exchange(dummyRinasakUrl(FNR).toUriString(), HttpMethod.GET, null, String::class.java) } returns
                ResponseEntity.ok().body( rinaSakerBuc.toJson())

        every { euxNavIdentRestTemplate.exchange( "/buc/3010", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "3010", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/75312", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "75312", processDefinitionName = "P_BUC_03").toJson() )
        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.perform(
                MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals(2, mapJsonToAny<List<BucView>>(response).size)
    }


    @Test
    fun `Gitt at saksbehandler går via brukerkontekst og har et avdød fnr når avdøds bucer søkes etter så returneres avdøds saker `() {
        val rinaSakerBuc = listOf(dummyRinasak("3010", "P_BUC_02"), dummyRinasak("75312", "P_BUC_03"))
        val rinaGjenlevUrl = dummyRinasakAvdodUrl(AVDOD_FNR)
        every { euxNavIdentRestTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( rinaSakerBuc.toJson())

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR2/avdod/$AVDOD_FNR")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        //data og spurt eux
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }

        val expected = """
            [{"euxCaseId":"3010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":"01010100001"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)

        val requestlist = mapJsonToAny<List<BucView>>(response)

        assertEquals(1, requestlist.size)
        assertEquals("3010", requestlist.first().euxCaseId)
        assertEquals(P_BUC_02, requestlist.first().buctype)
    }

    @Test
    fun `Gitt at saksbehandler går via brukerkontekst og har et avdød fnr når avdøds bucer søkes etter så returneres ingen saker `() {
        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(AVDOD_FNR)
        every { euxNavIdentRestTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body("[]")

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$AKTOERID/saknr/$SAKNR2/avdod/$AVDOD_FNR")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }

        val requestlist = mapJsonToAny<List<BucView>>(response)
        assertEquals(0, requestlist.size)
    }

    private fun documentsItem(id: String, status: String, sedType: SedType, direction: String? = "OUT") =
        DocumentsItem(
            id = id,
            creationDate = lastupdate,
            lastUpdate = lastupdate,
            status = status,
            type = sedType,
            direction = direction,
            receiveDate = null
        )

}