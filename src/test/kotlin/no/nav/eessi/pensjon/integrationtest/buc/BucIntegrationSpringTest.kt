package no.nav.eessi.pensjon.integrationtest.buc

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxKlient
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucView
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Properties
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Traits
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.client.BrukerId
import no.nav.eessi.pensjon.vedlegg.client.BrukerIdType
import no.nav.eessi.pensjon.vedlegg.client.SafRequest
import no.nav.eessi.pensjon.vedlegg.client.Variables
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.util.ResourceUtils
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponents
import java.time.LocalDate
import java.time.Month
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
@EmbeddedKafka
class BucIntegrationSpringTest {

    @MockkBean
    lateinit var stsService: STSService

    @MockkBean(name = "euxOidcRestTemplate")
    private lateinit var restEuxTemplate: RestTemplate

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var restSafTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    private lateinit var pensjonsinformasjonClient: PensjonsinformasjonClient

    @MockkBean
    private lateinit var personService: PersonService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Gitt Det er en SingleBucRequest med avdod skal det vises korrekt resulat`() {
        val saknr = "1203201322"
        val aktoerid = "1123123123123123"
        val gjenlevFnr = "1234567890000"
        val avdodfnr = "01010100001"
        val euxCaseId = "80001"

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)) }returns NorskIdent(gjenlevFnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc02 = Buc(id = "$euxCaseId", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //buc02 sed
        val rinabucdocumentidpath = "/buc/$euxCaseId/sed/1"
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()
        every { restEuxTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )


        val result = mockMvc.perform(get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr/avdodfnr/$avdodfnr")
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
    fun `Gitt Det er en SingleBucRequest uten avdod skal det vises korrekt resulat`() {
        val saknr = "1203201322"
        val aktoerid = "1123123123123123"
        val fnr = "1234567890000"
        val euxCaseId = "900001"

        //aktoerid -> fnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)) }returns NorskIdent(fnr)

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc01
        val docItems = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc01 = Buc(id = "$euxCaseId", processDefinitionName = "P_BUC_01", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/$euxCaseId"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc01.toJson() )

        val result = mockMvc.perform(get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr")
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

        //aktoerid -> fnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)) }returns NorskIdent(fnr)

        val rinabucpath = "/buc/$euxCaseId"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val result = mockMvc.perform(get("/buc/enkeldetalj/$euxCaseId/aktoerid/$aktoerid/saknr/$saknr")
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
    fun `Hent mulige rinasaker for aktoer og vedtak og saf`() {
        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"

        val avdodFnr = "01010100001"
        val vedtakid = "2312123123123"
        val saknr = "100001000"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) } returns NorskIdent(gjenlevendeFnr)

        //buc02 - avdød rinasak
        val rinaSakerBuc02 = listOf(dummyRinasak("5010", "P_BUC_02"))
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, bucType = null)
        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(rinaSakerBuc02.toJson())

        //saf (sikker arkiv fasade) (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina("1010"))
        val rinaSafUrl = dummyRinasakUrl(euxCaseId =  "1010", status = "\"open\"")
        every { restEuxTemplate.exchange( eq(rinaSafUrl.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( rinaSakerBuc02.toJson())

        //gjenlevende rinasak
        val rinaSakerBuc = listOf(dummyRinasak("3010", "P_BUC_01"), dummyRinasak("75312", "P_BUC_03"))
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( rinaSakerBuc.toJson())


        val result = mockMvc.perform(get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/$saknr/vedtak/$vedtakid")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?rinasaksnummer=1010&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
            [{"euxCaseId":"3010","buctype":"P_BUC_01","aktoerId":"1123123123123123","saknr":"100001000","avodnr":null},{"euxCaseId":"75312","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avodnr":null},{"euxCaseId":"5010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"100001000","avodnr":null}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)

        val requestlist = mapJsonToAny(response, typeRefs<List<BucView>>())

        assertEquals(3, requestlist.size)
        val bucVeiw = requestlist.first()
        assertEquals("100001000", bucVeiw.saknr)
        assertEquals( BucType.P_BUC_01, bucVeiw.buctype)
        assertEquals( "3010", bucVeiw.euxCaseId)
        assertEquals( "1123123123123123", bucVeiw.aktoerId)

        println(requestlist.toJson())

    }

    @Test
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

        val result = mockMvc.perform(get("/buc/rinasaker/$aktoerId/saknr/$saknr")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        val expected = """
            [{"euxCaseId":"3010","aktoerId":"1123123123123123","saknr":"100001000","avodnr":null},{"euxCaseId":"75312","aktoerId":"1123123123123123","saknr":"100001000","avodnr":null}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)

        val requestlist = mapJsonToAny(response, typeRefs<List<BucView>>())

        assertEquals(2, requestlist.size)
        assertEquals("3010", requestlist.first().euxCaseId)

        println(requestlist.toJson())

    }


    @Test
    fun `Hent mulige rinasaker for aktoer med avdodfnr Så skal korrekt resultat vises`() {
        val fnr = "1234567890000"
        val aktoerId = "1123123123123123"
        val saknr = "100001000"
        val avdodFnr = "01010100001"

        //gjenlevende aktoerid -> gjenlevendefnr
        //every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnr)

        //gjenlevende rinasak
        val rinaSakerBuc = listOf(dummyRinasak("3010", "P_BUC_02"), dummyRinasak("75312", "P_BUC_03"))
        val rinaGjenlevUrl = dummyRinasakUrl(fnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( rinaSakerBuc.toJson())

        val result = mockMvc.perform(get("/buc/rinasaker/$aktoerId/saknr/$saknr/avdod/$avdodFnr")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }

//        val expected = """
//            [{"euxCaseId":"3010","aktoerId":"1123123123123123","saknr":"100001000","avodnr":null},{"euxCaseId":"75312","aktoerId":"1123123123123123","saknr":"100001000","avodnr":null}]
//        """.trimIndent()
//
//        JSONAssert.assertEquals(expected, response, false)
//
//        val requestlist = mapJsonToAny(response, typeRefs<List<BucView>>())
//
//        assertEquals(2, requestlist.size)
//        assertEquals("3010", requestlist.first().euxCaseId)

        println(response.toJson())

    }



    @Test
    fun `Gitt det ikke finnes noen SED i en buc med avdød så skal det vies et tomt resultat`() {

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) }returns NorskIdent(gjenlevendeFnr)

        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")
        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        //buc05 avdød rinasak
        val rinaSakerBuc05 = listOf(dummyRinasak("1010", "P_BUC_05"))
        val rinaBuc05url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_05")
        every { restEuxTemplate.exchange( rinaBuc05url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(rinaSakerBuc05.toJson())

        //buc06 avdød rinasak
        val rinaBuc06url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_06")
        every { restEuxTemplate.exchange(rinaBuc06url.toUriString(), HttpMethod.GET, null, String::class.java) } returns  ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc10 avdød rinasak
        val rinaBuc10url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_10")
        every { restEuxTemplate.exchange( rinaBuc10url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, status = "\"open\"")
        every { restEuxTemplate.exchange( rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        val buc05 = ResourceUtils.getFile("classpath:json/buc/buc-1190072-buc05_deletedP8000.json").readText()
        val rinabucpath = "/buc/1010"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns  ResponseEntity.ok().body( buc05 )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))

        every { restSafTemplate.exchange(eq("/"), HttpMethod.POST, httpEntity, String::class.java) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )

        val result = mockMvc.perform(get("/buc/detaljer/$gjenlevendeAktoerId/avdod/$avdodFnr")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("[]", response)
    }

    @Test
    fun `Gitt det finnes gjenlevende og en avdød på buc02 så skal det hentes og lever en liste av buc`() {

        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"
        val vedtakid = "2312123123123"
        val saknr = "1203201322"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) } returns NorskIdent(gjenlevendeFnr)

        //buc02 - avdød rinasak
        val rinaSakerBuc02 = listOf(dummyRinasak("1010", "P_BUC_02"))
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")

        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(rinaSakerBuc02.toJson())

        //buc05 avdød rinasak
        val rinaBuc05url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_05")
        val rinaSakerBuc05 = listOf(dummyRinasak("6006777", "P_BUC_05"))

        every { restEuxTemplate.exchange( rinaBuc05url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( rinaSakerBuc05.toJson() )

        //buc06 avdød rinasak
        val rinaBuc06url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_06")
        every { restEuxTemplate.exchange( rinaBuc06url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc10 avdød rinasak
        val rinaBuc10url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_10")
        every { restEuxTemplate.exchange(rinaBuc10url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, status = "\"open\"")
        println("***" + rinaGjenlevUrl.toUriString() + "***")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc02 = Buc(id = "1010", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/1010"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )
        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { restEuxTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )

        mockMvc.perform(get("/buc/detaljer/$gjenlevendeAktoerId/saknr/$saknr/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_06&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_10&status=\"open\"", HttpMethod.GET, null, String::class.java) }

        verify (exactly = 1) { restEuxTemplate.exchange("/buc/1010", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/1010/sed/1", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

    }

    @Test
    fun `Gitt det finnes gjenlevende og en avdød på buc02 og fra SAF så skal det hentes og lever en liste av buc`() {
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"
        val vedtakid = "2312123123123"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) } returns NorskIdent(gjenlevendeFnr)
        //buc02 - avdød rinasak
        val rinaSakerBuc02 = listOf(dummyRinasak("1010", "P_BUC_02"))
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")
        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(rinaSakerBuc02.toJson())

        //buc05 avdød rinasak
        val rinaBuc05url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_05")
        every { restEuxTemplate.exchange( rinaBuc05url.toUriString(), HttpMethod.GET, null, String::class.java ) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc06 avdød rinasak
        val rinaBuc06url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_06")
        every { restEuxTemplate.exchange( rinaBuc06url.toUriString(), HttpMethod.GET, null, String::class.java ) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc10 avdød rinasak
        val rinaBuc10url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_10")
        every { restEuxTemplate.exchange(rinaBuc10url.toUriString(), HttpMethod.GET, null, String::class.java ) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc02 = Buc(id = "1010", internationalId = "1000100010001000", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/1010"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina("1010"))
        val rinaSafUrl = dummyRinasakUrl(euxCaseId =  "1010", status = "\"open\"")
        every { restEuxTemplate.exchange( eq(rinaSafUrl.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( rinaSakerBuc02.toJson())

        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { restEuxTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )

        val result = mockMvc.perform(get("/buc/detaljer/$gjenlevendeAktoerId/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 2) { restEuxTemplate.exchange("/buc/1010", HttpMethod.GET, null, String::class.java)  }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/1010/sed/1", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        assertTrue { response.contains(avdodFnr) }
        JSONAssert.assertEquals(response, caseOneExpected(), false)
    }

    @Test
    fun `Gitt det finnes gjenlevende og en avdød kun fra SAF så skal det hentes og lever en liste av buc med subject`() {
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"
        val vedtakid = "2312123123123"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) } returns NorskIdent(gjenlevendeFnr)

        //buc02 - avdød rinasak
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")
        every { restEuxTemplate.exchange( eq(rinaBuc02url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        //buc05 avdød rinasak
        val rinaBuc05url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_05")
        every { restEuxTemplate.exchange( rinaBuc05url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc06 avdød rinasak
        val rinaBuc06url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_06")
        every { restEuxTemplate.exchange( rinaBuc06url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc10 avdød rinasak
        val rinaBuc10url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_10")
        every { restEuxTemplate.exchange( eq(rinaBuc10url.toUriString()), eq(HttpMethod.GET),null, eq(String::class.java )) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT", receiveDate = null),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT", receiveDate = null)
        )
        val buc02 = Buc(id = "1010", internationalId = "1000100010001000", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/1010"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))

        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns  ResponseEntity.ok().body(  dummySafMetaResponseMedRina("1010") )
        val rinaSafUrl = dummyRinasakUrl(euxCaseId =  "1010", status = "\"open\"")
        val rinaSakerBuc02 = listOf(dummyRinasak("1010", "P_BUC_02"))
        every { restEuxTemplate.exchange( eq(rinaSafUrl.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( rinaSakerBuc02.toJson())

        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { restEuxTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )

        val result = mockMvc.perform(get("/buc/detaljer/$gjenlevendeAktoerId/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?rinasaksnummer=1010&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/1010", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }
        assertTrue { response.contains(avdodFnr) }
        JSONAssert.assertEquals(response, caseOneExpected(), false)
    }


    @Test
    fun `Gitt det finnes en gjenlevende og avdød hvor buc02 og buc05 finnes skal det returneres en liste av buc`() {

        val sedP2100json = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()
        val sedP8000json = javaClass.getResource("/json/nav/P8000_NO-NAV.json").readText()

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"

        val vedtakid = "2312123123123"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) } returns NorskIdent(gjenlevendeFnr)

        //buc02 - avdød rinasak
        val rinaSakerBuc02 = listOf(dummyRinasak("1010", "P_BUC_02"))
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")
        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java ) } returns ResponseEntity.ok().body(rinaSakerBuc02.toJson())

        //buc05 avdød rinasak
        val rinaSakerBuc05 = listOf(dummyRinasak("2020", "P_BUC_05"))
        val rinaBuc05url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_05")
        every { restEuxTemplate.exchange( eq(rinaBuc05url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java ))  } returns ResponseEntity.ok().body( rinaSakerBuc05.toJson())

        //buc06 avdød rinasak
        val rinaBuc06url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_06")
        every { restEuxTemplate.exchange( rinaBuc06url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //buc10 avdød rinasak
        val rinaBuc10url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_10")
        every { restEuxTemplate.exchange( eq(rinaBuc10url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns  ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, status = "\"open\"")
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc02 = Buc(id = "1010", internationalId = "1000100010001000", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/1010"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //buc05
        val doc05Items = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P8000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc05 = Buc(id = "2020", internationalId = "2000200020002000", processDefinitionName = "P_BUC_05", startDate = lastupdate, lastUpdate = lastupdate,  documents = doc05Items)

        val rinabuc05path = "/buc/2020"
        every { restEuxTemplate.exchange( eq(rinabuc05path), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( buc05.toJson() )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )

        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { restEuxTemplate.exchange( eq(rinabucdocumentidpath), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( sedP2100json )

        //buc05 sed
        val rinabuc05documentidpath = "/buc/2020/sed/1"
        every { restEuxTemplate.exchange( eq(rinabuc05documentidpath), eq(HttpMethod.GET),null, eq(String::class.java)) } returns ResponseEntity.ok().body( sedP8000json )
        val result = mockMvc.perform(get("/buc/detaljer/$gjenlevendeAktoerId/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/1010", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/1010/sed/1", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/2020", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/2020/sed/1", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        JSONAssert.assertEquals(response, csseTwoExpected(), false)

    }

    @Test
    fun `Gitt det finnes en gjenlevende og avdød hvor buc05 buc06 og buc10 finnes Så skal det returneres en liste av buc`() {

        val sedP8000json = javaClass.getResource("/json/nav/P8000_NO-NAV.json").readText()

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"

        val vedtakid = "2312123123123"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid)  } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)

        //gjenlevende aktoerid -> gjenlevendefnr
        every {personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId))  } returns NorskIdent(gjenlevendeFnr)

        //buc02 - avdød rinasak
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")
        every { restEuxTemplate.exchange( eq(rinaBuc02url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns  ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        //buc05 avdød rinasak
        val rinaSakerBuc05 = listOf(dummyRinasak("2020", "P_BUC_05"))
        val rinaBuc05url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_05")
        every { restEuxTemplate.exchange( eq(rinaBuc05url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java))  } returns ResponseEntity.ok().body( rinaSakerBuc05.toJson())

        //buc06 avdød rinasak
        val rinaSakerBuc06 = listOf(dummyRinasak("3030", "P_BUC_06"))
        val rinaBuc06url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_06")
        every { restEuxTemplate.exchange( eq(rinaBuc06url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( rinaSakerBuc06.toJson())

        //buc10 avdød rinasak
        val rinaSakerBuc10 = listOf(dummyRinasak("4040", "P_BUC_10"))
        val rinaBuc10url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_10")
        every { restEuxTemplate.exchange( eq(rinaBuc10url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( rinaSakerBuc10.toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, status = "\"open\"")
        every { restEuxTemplate.exchange( eq(rinaGjenlevUrl.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc05
        val doc05Items = listOf(DocumentsItem(id = "5", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P8000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc05 = Buc(id = "2020", processDefinitionName = "P_BUC_05", startDate = lastupdate, lastUpdate = lastupdate,  documents = doc05Items)

        val rinabuc05path = "/buc/2020"
        every { restEuxTemplate.exchange( eq(rinabuc05path), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( buc05.toJson() )

        //buc06
        val doc06Items = listOf(DocumentsItem(id = "6", creationDate = lastupdate, lastUpdate = lastupdate, status = "new", type = SedType.P7000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc06 = Buc(id = "3030", processDefinitionName = "P_BUC_06", startDate = lastupdate, lastUpdate = lastupdate,  documents = doc06Items)

        val rinabuc06path = "/buc/3030"
        every { restEuxTemplate.exchange( eq(rinabuc06path), eq(HttpMethod.GET),null, eq(String::class.java)) } returns ResponseEntity.ok().body( buc06.toJson() )

        //buc10
        val doc10Items = listOf(DocumentsItem(id = "10", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P15000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc10 = Buc(id = "4040", processDefinitionName = "P_BUC_10", startDate = lastupdate, lastUpdate = lastupdate,  documents = doc10Items)

        val rinabuc10path = "/buc/4040"
        every { restEuxTemplate.exchange( eq(rinabuc10path), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( buc10.toJson() )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )

        //buc05 sed
        val rinabuc05documentidpath = "/buc/2020/sed/5"
        every { restEuxTemplate.exchange( eq(rinabuc05documentidpath), eq(HttpMethod.GET),null, eq(String::class.java)) } returns ResponseEntity.ok().body( sedP8000json )

        //buc06 sed
        val rinabuc06documentidpath = "/buc/3030/sed/6"
        every { restEuxTemplate.exchange( eq(rinabuc06documentidpath), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( SED(SedType.P7000, pensjon = Pensjon(gjenlevende = Bruker(person = Person(pin = listOf(
            PinItem(land = "NO", identifikator = gjenlevendeFnr)
        ), fornavn = "test", etternavn = "etter")))).toJsonSkipEmpty() )

        //buc10 sed
        val rinabucd10ocumentidpath = "/buc/4040/sed/10"
        every { restEuxTemplate.exchange( eq(rinabucd10ocumentidpath), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body( SED(SedType.P15000, pensjon = Pensjon(gjenlevende = Bruker(person = Person(pin = listOf(PinItem(land = "NO", identifikator = gjenlevendeFnr)), fornavn = "test", etternavn = "etter")))).toJsonSkipEmpty() )

        mockMvc.perform(get("/buc/detaljer/$gjenlevendeAktoerId/vedtak/$vedtakid")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_06&status=\"open\"", HttpMethod.GET, null, String::class.java)}
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&buctype=P_BUC_10&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/2020", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/2020/sed/5", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/3030", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/3030/sed/6", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/4040", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/4040/sed/10", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange(eq("/") , HttpMethod.POST, eq(httpEntity), String::class.java) }
    }

    private fun mockVedtak(avdofnr: String, gjenlevAktoerid: String): Pensjonsinformasjon {
        val pen = Pensjonsinformasjon()
        val avod = V1Avdod()
        val person = V1Person()
        avod.avdod = avdofnr
        person.aktorId = gjenlevAktoerid
        pen.avdod = avod
        pen.person = person

        return pen
    }


    private fun caseOneExpected(): String {
        return """
[
  {
    "type": "P_BUC_02",
    "caseId": "1010",
    "internationalId": "1000100010001000",
    "creator": {
      "country": "",
      "institution": "",
      "name": null,
      "acronym": null
    },
    "sakType": null,
    "status": null,
    "startDate": 1596751200000,
    "lastUpdate": 1596751200000,
    "institusjon": [],
    "seds": [
      {
        "attachments": [],
        "displayName": null,
        "type": "P2100",
        "conversations": null,
        "isSendExecuted": null,
        "id": "1",
        "direction": "OUT",
        "creationDate": 1596751200000,
        "receiveDate": null,
        "typeVersion": null,
        "allowsAttachments": null,
        "versions": null,
        "lastUpdate": 1596751200000,
        "parentDocumentId": null,
        "status": "sent",
        "participants": null,
        "firstVersion": null,
        "lastVersion": null,
        "version": "1",
        "message": null
      },
      {
        "attachments": [],
        "displayName": null,
        "type": "P4000",
        "conversations": null,
        "isSendExecuted": null,
        "id": "2",
        "direction": "OUT",
        "creationDate": 1596751200000,
        "receiveDate": null,
        "typeVersion": null,
        "allowsAttachments": null,
        "versions": null,
        "lastUpdate": 1596751200000,
        "parentDocumentId": null,
        "status": "draft",
        "participants": null,
        "firstVersion": null,
        "lastVersion": null,
        "version": "1",
        "message": null
      }
    ],
    "error": null,
    "readOnly": false,
    "subject": {
      "gjenlevende": {
        "fnr": "1234567890000"
      },
      "avdod": {
        "fnr": "01010100001"
      }
    }
  }
]
            """.trimIndent()
    }

    private fun csseTwoExpected(): String {
        return """
            [
  {
    "type": "P_BUC_02",
    "caseId": "1010",
    "internationalId": "1000100010001000",
    "creator": {
      "country": "",
      "institution": "",
      "name": null,
      "acronym": null
    },
    "sakType": null,
    "status": null,
    "startDate": 1596751200000,
    "lastUpdate": 1596751200000,
    "institusjon": [],
    "seds": [
      {
        "attachments": [],
        "displayName": null,
        "type": "P2100",
        "conversations": null,
        "isSendExecuted": null,
        "id": "1",
        "direction": "OUT",
        "creationDate": 1596751200000,
        "receiveDate": null,
        "typeVersion": null,
        "allowsAttachments": null,
        "versions": null,
        "lastUpdate": 1596751200000,
        "parentDocumentId": null,
        "status": "sent",
        "participants": null,
        "firstVersion": null,
        "lastVersion": null,
        "version": "1",
        "message": null
      },
      {
        "attachments": [],
        "displayName": null,
        "type": "P4000",
        "conversations": null,
        "isSendExecuted": null,
        "id": "2",
        "direction": "OUT",
        "creationDate": 1596751200000,
        "receiveDate": null,
        "typeVersion": null,
        "allowsAttachments": null,
        "versions": null,
        "lastUpdate": 1596751200000,
        "parentDocumentId": null,
        "status": "draft",
        "participants": null,
        "firstVersion": null,
        "lastVersion": null,
        "version": "1",
        "message": null
      }
    ],
    "error": null,
    "readOnly": false,
    "subject": {
      "gjenlevende": {
        "fnr": "1234567890000"
      },
      "avdod": {
        "fnr": "01010100001"
      }
    }
  },
  {
    "type": "P_BUC_05",
    "caseId": "2020",
    "internationalId": "2000200020002000",
    "creator": {
      "country": "",
      "institution": "",
      "name": null,
      "acronym": null
    },
    "sakType": null,
    "status": null,
    "startDate": 1596751200000,
    "lastUpdate": 1596751200000,
    "institusjon": [],
    "seds": [
      {
        "attachments": [],
        "displayName": null,
        "type": "P8000",
        "conversations": null,
        "isSendExecuted": null,
        "id": "1",
        "direction": "OUT",
        "creationDate": 1596751200000,
        "receiveDate": null,
        "typeVersion": null,
        "allowsAttachments": null,
        "versions": null,
        "lastUpdate": 1596751200000,
        "parentDocumentId": null,
        "status": "sent",
        "participants": null,
        "firstVersion": null,
        "lastVersion": null,
        "version": "1",
        "message": null
      },
      {
        "attachments": [],
        "displayName": null,
        "type": "P4000",
        "conversations": null,
        "isSendExecuted": null,
        "id": "2",
        "direction": "OUT",
        "creationDate": 1596751200000,
        "receiveDate": null,
        "typeVersion": null,
        "allowsAttachments": null,
        "versions": null,
        "lastUpdate": 1596751200000,
        "parentDocumentId": null,
        "status": "draft",
        "participants": null,
        "firstVersion": null,
        "lastVersion": null,
        "version": "1",
        "message": null
      }
    ],
    "error": null,
    "readOnly": false,
    "subject": {
      "gjenlevende": {
        "fnr": "1234567890000"
      },
      "avdod": {
        "fnr": "01010100001"
      }
    }
  }
]
        """.trimIndent()
    }

    private fun dummyHeader(value: String?): HttpEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return HttpEntity(value, headers)
    }

    private fun dummySafReqeust(aktoerId: String): String {
            val request = SafRequest(variables = Variables(BrukerId(aktoerId, BrukerIdType.AKTOERID), 10000))
            return request.toJson()
    }

    private fun dummySafMetaResponse(): String {
        return """
            {
              "data": {
                "dokumentoversiktBruker": {
                  "journalposter": [
                    {
                      "tilleggsopplysninger": [],
                      "journalpostId": "439532144",
                      "datoOpprettet": "2018-06-08T17:06:58",
                      "tittel": "MASKERT_FELT",
                      "tema": "PEN",
                      "dokumenter": []
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }

    private fun dummySafMetaResponseMedRina(rinaid: String): String {
        return """
            {
              "data": {
                "dokumentoversiktBruker": {
                  "journalposter": [
                    {
                      "tilleggsopplysninger": [
                          {
                              "nokkel":"eessi_pensjon_bucid",
                              "verdi":"$rinaid"
                            }  
                      ],
                      "journalpostId": "439532144",
                      "datoOpprettet": "2018-06-08T17:06:58",
                      "tittel": "MASKERT_FELT",
                      "tema": "PEN",
                      "dokumenter": []
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }

    private fun dummyRinasakAvdodUrl(avod: String? = null, bucType: String? = "P_BUC_02", status: String? =  "\"open\"") = dummyRinasakUrl(avod, bucType, null, status)
    private fun dummyRinasakUrl(fnr: String? = null, bucType: String? = null, euxCaseId: String? = null, status: String? = null) : UriComponents {
        return EuxKlient.getRinasakerUri(fnr, euxCaseId, bucType, status)
    }

    private fun dummyRinasak(rinaSakId: String, bucType: String): Rinasak {
        return Rinasak(rinaSakId, bucType, Traits(), "", Properties(), "open")
    }
}