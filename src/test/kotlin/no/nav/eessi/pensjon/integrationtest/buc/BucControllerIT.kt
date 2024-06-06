package no.nav.eessi.pensjon.integrationtest.buc


import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.FunctionAnswer
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.model.SedType
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
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
    fun `Gitt det ikke finnes en P_BUC_02 uten noen SED med avdød så skal det vies et tomt resultat`() {

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"
        val saksNr =  null

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(gjenlevendeAktoerId)) }returns NorskIdent(gjenlevendeFnr)

        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr)
        every { euxNavIdentRestTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(avdodFnr)
        every { euxNavIdentRestTemplate.exchange( rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))

        every { restSafTemplate.exchange(eq("/"), HttpMethod.POST, httpEntity, String::class.java) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )


        val result = mockMvc.perform(get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/$saksNr/avdod/$avdodFnr")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("[]", response)
    }

    @Test
    fun `Hent mulige rinasaker for fnr fra euxrina`() {
        val fnr = "1234567890000"
        val aktoerId = "1123123123123123"
        val pesyssaknr = "100001000"

        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(aktoerId)) } returns NorskIdent(fnr)

        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250)
            ResponseEntity.ok().body(listOf(dummyRinasak("5195021", "P_BUC_03"), dummyRinasak("5922554", "P_BUC_03") ).toJson() )
        })
        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5922554", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5195021", processDefinitionName = "P_BUC_03").toJson() )
        every { gcpStorageService.eksisterer(any()) } returns false

        val result = mockMvc.perform(
                get("/buc/rinasaker/euxrina/$aktoerId/pesyssak/$pesyssaknr")
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
        val npid = "01220049651"
        val aktoerId = "1123123123123123"
        val pesyssaknr = "100001000"

        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(aktoerId)) } returns NorskIdent("")
        every { personService.hentIdent(IdentGruppe.NPID, AktoerId(aktoerId)) } returns Npid(npid)

        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01220049651&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250)
            ResponseEntity.ok().body(listOf(dummyRinasak("5195021", "P_BUC_03"), dummyRinasak("5922554", "P_BUC_03") ).toJson() ) })

        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5922554", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5195021", processDefinitionName = "P_BUC_03").toJson() )
        every { gcpStorageService.eksisterer(any()) } returns false

        val result = mockMvc.perform(
            get("/buc/rinasaker/euxrina/$aktoerId/pesyssak/$pesyssaknr")
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
        val npid = "01220049651"
        val aktoerId = "1123123123123123"
        val pesyssaknr = "100001000"

        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(aktoerId)) } returns NorskIdent("")
        every { personService.hentIdent(IdentGruppe.NPID, AktoerId(aktoerId)) } returns Npid(npid)

        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=01220049651&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250)
            ResponseEntity.ok().body(listOf(dummyRinasak("5195021", "P_BUC_03"), dummyRinasak("5922554", "P_BUC_03"), dummyRinasak("000001", "P_BUC_02") ).toJson() ) })

        every { euxNavIdentRestTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5922554", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5195021", processDefinitionName = "P_BUC_03").toJson() )
        every { euxNavIdentRestTemplate.exchange( "/buc/000001", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "000001", processDefinitionName = "P_BUC_02").toJson() )
        every { gcpStorageService.eksisterer("5922554") } returns false
        every { gcpStorageService.eksisterer("5195021") } returns false
        every { gcpStorageService.eksisterer("000001") } returns true

        val result = mockMvc.perform(
            get("/buc/rinasaker/euxrina/$aktoerId/pesyssak/$pesyssaknr")
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

//    @Test
//    fun `Hva skjer når vi forsøker å hente saker for et fnr som ikke finnes`() {
//        val aktoerId = "1123123123123123"
//        val pesyssaknr = "100001000"
//
//        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent("")
//        every { personService.hentIdent(IdentType.Npid, AktoerId(aktoerId)) } returns Npid("")
//
//        every { euxNavIdentRestTemplate.exchange("/rinasaker?fødselsnummer=&status=\"open\"", HttpMethod.GET, null, String::class.java) } .throws(HttpClientErrorException(HttpStatus.NOT_FOUND))
//
//        val result = mockMvc.perform(
//            MockMvcRequestBuilders.get("/buc/rinasaker/euxrina/$aktoerId/pesyssak/$pesyssaknr")
//                .contentType(MediaType.APPLICATION_JSON))
//            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
//            .andReturn()
//
//        val response = result.response.getContentAsString(charset("UTF-8"))
//        println(response)
//
//        JSONAssert.assertEquals(response, response, true)
//
//    }



}