package no.nav.eessi.pensjon.integrationtest.buc

import com.ninjasquad.springmockk.MockkBean
import io.mockk.FunctionAnswer
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucView
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucViewKilde
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Disabled
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
import kotlin.test.assertEquals

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
@EmbeddedKafka
internal class BucViewDetaljIntegrationTest: BucBaseTest() {

    @MockkBean(name = "prefillOAuthTemplate")
    private lateinit var prefillOAuthTemplate: RestTemplate

    @MockkBean(name = "euxNavIdentRestTemplate")
    private lateinit var restEuxTemplate: RestTemplate

    @MockkBean(name = "euxSystemRestTemplate")
    private lateinit var euxUsernameOidcRestTemplate: RestTemplate

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var restSafTemplate: RestTemplate

    @MockkBean(name = "safRestOidcRestTemplate")
    private lateinit var safRestOidcRestTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    private lateinit var pensjonsinformasjonClient: PensjonsinformasjonClient

    @MockkBean
    private lateinit var personService: PersonService

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
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)) } returns NorskIdent(gjenlevFnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT")
        )
        val buc02 = Buc(id = "$euxCaseId", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //buc02 sed
        val rinabucdocumentidpath = "/buc/$euxCaseId/sed/1"
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()
        every { restEuxTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )


        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/avdodfnr/$avdodfnr/kilde/$kilde")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_02","caseId":"$euxCaseId","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":1596751200000,"lastUpdate":1596751200000,"institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P2100","conversations":null,"isSendExecuted":null,"id":"1","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":{"gjenlevende":{"fnr":"1234567890000"},"avdod":{"fnr":"01010100001"}}}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/$euxCaseId/sed/1", HttpMethod.GET, null, String::class.java) }

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
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)) } returns NorskIdent(gjenlevFnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P15000, direction = "IN"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT")
        )
        val buc10 = Buc(id = "$euxCaseId", processDefinitionName = "P_BUC_10", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc10.toJson() )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/avdodfnr/$avdodfnr/kilde/$kilde")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_10","caseId":"$euxCaseId","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":1596751200000,"lastUpdate":1596751200000,"institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P15000","conversations":null,"isSendExecuted":null,"id":"1","direction":"IN","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":{"gjenlevende":{"fnr":"1234567890000"},"avdod":{"fnr":"01010100001"}}}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
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
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)) } returns NorskIdent(fnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc01
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT")
        )
        val buc01 = Buc(id = "$euxCaseId", processDefinitionName = "P_BUC_01", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc01.toJson() )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/kilde/$kilde")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            {"type":"P_BUC_01","caseId":"900001","internationalId":"n/a","creator":{"country":"","institution":"","name":null,"acronym":null},"sakType":null,"status":null,"startDate":1596751200000,"lastUpdate":1596751200000,"institusjon":[],"seds":[{"attachments":[],"displayName":null,"type":"P2000","conversations":null,"isSendExecuted":null,"id":"1","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"sent","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null},{"attachments":[],"displayName":null,"type":"P4000","conversations":null,"isSendExecuted":null,"id":"2","direction":"OUT","creationDate":1596751200000,"receiveDate":null,"typeVersion":null,"allowsAttachments":null,"versions":null,"lastUpdate":1596751200000,"parentDocumentId":null,"status":"draft","participants":null,"firstVersion":null,"lastVersion":null,"version":"1","message":null}],"error":null,"readOnly":false,"subject":null}
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `Gitt Det er en SingleBucRequest uten avdod på rinaid som ikke finnes`() {
        val saknr = "1203201322"
        val aktoerid = "1123123123123123"
        val fnr = "1234567890000"
        val euxCaseId = "900001"
        val kilde = BucViewKilde.SAF

        //aktoerid -> fnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)) } returns NorskIdent(fnr)

        val rinabucpath = "/buc/$euxCaseId"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/kilde/$kilde")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val bucView = mapJsonToAny(response, typeRefs<BucAndSedView>())
        val expectederror = """
            404 NOT_FOUND "Ikke funnet"
            """.trimIndent()
        assertEquals(expectederror, bucView.error)

        verify (exactly = 6) { restEuxTemplate.exchange("/buc/$euxCaseId", HttpMethod.GET, null, String::class.java)  }
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
    fun `Hent mulige rinasaker for aktoer og saf med vedtak med avdod`() {
        val gjenlevendeFnr = "1234567890000"
        val avdodFnr = "6789567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodAktoerId = "8888123123123123"
        val vedtakid = "2312123123123"
        val saknr = "100001000"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } .answers( FunctionAnswer { Thread.sleep(56); mockVedtak(avdodFnr,gjenlevendeAktoerId) } )

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) } returns NorskIdent(gjenlevendeFnr)
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(avdodAktoerId)) } returns NorskIdent(avdodFnr)

        val rinaSakerBuc02 = listOf(dummyRinasak("5010", "P_BUC_02"), dummyRinasak("3202", BucType.P_BUC_03.name), dummyRinasak("5005", BucType.AD_BUC_01.name), dummyRinasak("14675", BucType.P_BUC_06.name))
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, bucType = null)

        val answer = FunctionAnswer { Thread.sleep(220); ResponseEntity.ok().body(rinaSakerBuc02.toJson() ) }
        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } .answers(answer)

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina( "5010", "344000" ) )

        every { restEuxTemplate.exchange( "/buc/5010", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5010", processDefinitionName = "P_BUC_02").toJson() )
        every { restEuxTemplate.exchange("/buc/344000", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "344000", processDefinitionName = "P_BUC_03").toJson() )

/*
        val answer1 = FunctionAnswer { Thread.sleep(220); ResponseEntity.ok().body(listOf(dummyRinasak("5010", "P_BUC_02")).toJson() ) }
        every { restEuxTemplate.exchange( dummyRinasakUrl(euxCaseId = "5010", bucType = BucType.P_BUC_02.name, status = "\"open\"").toUriString(), HttpMethod.GET, null, String::class.java) } .answers(answer1)

        val answer2 = FunctionAnswer { Thread.sleep(220); ResponseEntity.ok().body(listOf(dummyRinasak("344000", BucType.P_BUC_03.name)).toJson() ) }
        every { restEuxTemplate.exchange( dummyRinasakUrl(euxCaseId = "344000", bucType = BucType.P_BUC_03.name, status = "\"open\"").toUriString(), HttpMethod.GET, null, String::class.java) } .answers(answer2)
*/


        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/$saknr/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        //data og spurt eux
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
            [{"euxCaseId":"5010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":"6789567890000","kilde":"SAF"},
            {"euxCaseId":"14675","buctype":"P_BUC_06","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":"6789567890000","kilde":"AVDOD"},
            {"euxCaseId":"344000","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"SAF"}]

        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }

    @Test
    @Disabled
    fun `Hent mulige rinasaker for aktoer og saf`() {
        val fnr = "1234567890000"
        val aktoerId = "1123123123123123"
        val saknr = "100001000"


        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnr)

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(aktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina( "5195021", "5922554" ) )

        val rinaSafUrl1 = dummyRinasakUrl(euxCaseId =  "5922554", status = "\"open\"")
        every { restEuxTemplate.exchange( eq( rinaSafUrl1.toUriString() ), eq(HttpMethod.GET), null, eq(String::class.java)) } .answers( FunctionAnswer { Thread.sleep(250); ResponseEntity.ok().body( listOf(dummyRinasak("5922554", BucType.P_BUC_03.name)).toJson() ) } )

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(fnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250); ResponseEntity.ok().body( listOf(dummyRinasak("5195021", "P_BUC_05") ).toJson() ) } )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$aktoerId/saknr/$saknr")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
                [{"euxCaseId":"5195021","buctype":"P_BUC_05","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"BRUKER"},
                {"euxCaseId":"5922554","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"SAF"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, true)
    }


    @Test
    @Disabled
    fun `Hent mulige rinasaker for aktoer uten vedtak og saf`() {
        val fnr = "1234567890000"
        val aktoerId = "1123123123123123"
        val saknr = "100001000"

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnr)

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(aktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body( dummySafMetaResponse() )

        //gjenlevende rinasak
        val rinaSakerBuc = listOf(dummyRinasak("3010", "P_BUC_01"), dummyRinasak("75312", "P_BUC_03"))
        val rinaGjenlevUrl = dummyRinasakUrl(fnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( rinaSakerBuc.toJson())

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$aktoerId/saknr/$saknr")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
            [{"euxCaseId":"3010","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null},{"euxCaseId":"75312","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)

        val requestlist = mapJsonToAny(response, typeRefs<List<BucView>>())

        assertEquals(2, requestlist.size)
        assertEquals("3010", requestlist.first().euxCaseId)

        println(requestlist.toJson())

    }


    @Test
    fun `Gitt at saksbehandler går via brukerkontekst og har et avdød fnr når avdøds bucer søkes etter så returneres avdøds saker `() {
        val aktoerId = "1123123123123123"
        val saknr = "100001000"
        val avdodFnr = "01010100001"

        val rinaSakerBuc = listOf(dummyRinasak("3010", "P_BUC_02"), dummyRinasak("75312", "P_BUC_03"))
        val rinaGjenlevUrl = dummyRinasakAvdodUrl(avdodFnr, null)
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( rinaSakerBuc.toJson())

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$aktoerId/saknr/$saknr/avdod/$avdodFnr")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        //data og spurt eux
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }

        val expected = """
            [{"euxCaseId":"3010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":"01010100001"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)

        val requestlist = mapJsonToAny(response, typeRefs<List<BucView>>())

        assertEquals(1, requestlist.size)
        assertEquals("3010", requestlist.first().euxCaseId)
        assertEquals(BucType.P_BUC_02, requestlist.first().buctype)

        println(response.toJson())

    }

    @Test
    fun `Gitt at saksbehandler går via brukerkontekst og har et avdød fnr når avdøds bucer søkes etter så returneres ingen saker `() {
        val aktoerId = "1123123123123123"
        val saknr = "100001000"
        val avdodFnr = "01010100001"

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(avdodFnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body("[]")

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/buc/rinasaker/$aktoerId/saknr/$saknr/avdod/$avdodFnr")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }

        val requestlist = mapJsonToAny(response, typeRefs<List<BucView>>())
        assertEquals(0, requestlist.size)

    }
}