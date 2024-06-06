package no.nav.eessi.pensjon.integrationtest.buc

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.FunctionAnswer
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde
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

//    @Autowired
//    private lateinit var safGraphQlOidcRestTemplate: RestTemplate

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
        val saknr = "1203201322"
        val aktoerid = "1123123123123123"
        val gjenlevFnr = "1234567890000"
        val avdodfnr = "01010100001"
        val euxCaseId = "80001"
        val kilde = BucViewKilde.AVDOD

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(aktoerid)) } returns NorskIdent(gjenlevFnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT")
        )
        val buc02 = Buc(id = euxCaseId, processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //buc02 sed
        val rinabucdocumentidpath = "/buc/$euxCaseId/sed/1"
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()
        every { euxNavIdentRestTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )
        every { gcpStorageService.eksisterer(any()) } returns false


        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/avdodfnr/$avdodfnr/kilde/$kilde")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_02","caseId":"$euxCaseId","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":"2020-08-07","lastUpdate":"2020-08-07","institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P2100","conversations":null,"isSendExecuted":null,"id":"1","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":{"gjenlevende":{"fnr":"1234567890000"},"avdod":{"fnr":"01010100001"}}}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$euxCaseId/sed/1", HttpMethod.GET, null, String::class.java) }

    }

    @Test
    fun `Gitt korrekt request med aktorid med avdod og kilde saf Så skal en BucAndSedView json vises`() {
        val saknr = "1203201322"
        val aktoerid = "1123123123123123"
        val gjenlevFnr = "1234567890000"
        val avdodfnr = "01010100001"
        val euxCaseId = "80001"
        val kilde = BucViewKilde.SAF

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(aktoerid)) } returns NorskIdent(gjenlevFnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P15000, direction = "IN"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT")
        )
        val buc10 = Buc(id = euxCaseId, processDefinitionName = "P_BUC_10", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc10.toJson() )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/avdodfnr/$avdodfnr/kilde/$kilde")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_10","caseId":"$euxCaseId","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":"2020-08-07","lastUpdate":"2020-08-07","institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P15000","conversations":null,"isSendExecuted":null,"id":"1","direction":"IN","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":{"gjenlevende":{"fnr":"1234567890000"},"avdod":{"fnr":"01010100001"}}}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
//        verify (exactly = 1) { restEuxTemplate.exchange("/buc/$euxCaseId/sed/1", HttpMethod.GET, null, String::class.java) }

    }

    @Test
    fun `Gitt Det er en SingleBucRequest uten avdod skal det vises korrekt bucsedview resulat`() {
        val saknr = "1203201322"
        val aktoerid = "1123123123123123"
        val fnr = "1234567890000"
        val euxCaseId = "900001"
        val kilde = BucViewKilde.BRUKER

        //aktoerid -> fnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(aktoerid)) } returns NorskIdent(fnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc01
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT")
        )
        val buc01 = Buc(id = euxCaseId, processDefinitionName = "P_BUC_01", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc01.toJson() )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/kilde/$kilde")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_01","caseId":"900001","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":"2020-08-07","lastUpdate":"2020-08-07","institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P2000","conversations":null,"isSendExecuted":null,"id":"1","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":null}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `gitt en singleBucRequest uten avdod, og rinaid som ikke finnes så skal det kastes et NOT_FOUND exception`() {
        val saknr = "1203201322"
        val aktoerid = "1123123123123123"
        val fnr = "1234567890000"
        val euxCaseId = "900001"
        val kilde = BucViewKilde.SAF

        //aktoerid -> fnr
        every { personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(aktoerid)) } returns NorskIdent(fnr)

        val rinabucpath = "/buc/$euxCaseId"
        every { euxNavIdentRestTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/kilde/$kilde")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val bucView = mapJsonToAny<BucAndSedView>(response)
        assertEquals("404 NOT_FOUND", bucView.error)

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `Hent mulige rinasaker for aktoer og saf med vedtak uten avdod`() {
        val gjenlevendeAktoerId = "1123123123123123"
        val vedtakid = "2312123123123"
        val saknr = "100001000"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } .answers( FunctionAnswer { Thread.sleep(56); mockVedtakUtenAvdod(gjenlevendeAktoerId) } )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/$saknr/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        assertEquals("[]", response)
    }

    @Test
    fun `Gitt at saksbehandler går via brukerkontekst og har et avdød fnr når avdøds bucer søkes etter så returneres ingen saker `() {
        val aktoerId = "1123123123123123"
        val saknr = "100001000"
        val avdodFnr = "01010100001"

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(avdodFnr)
        every { euxNavIdentRestTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body("[]")

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$aktoerId/saknr/$saknr/avdod/$avdodFnr")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }

        val requestlist = mapJsonToAny<List<BucView>>(response)
        assertEquals(0, requestlist.size)
    }
}