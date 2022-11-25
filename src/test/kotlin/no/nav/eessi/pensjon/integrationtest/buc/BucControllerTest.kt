package no.nav.eessi.pensjon.integrationtest.buc


import com.ninjasquad.springmockk.MockkBean
import io.mockk.FunctionAnswer
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
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
internal class BucControllerTest: BucBaseTest() {

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

    @MockkBean(name = "kodeverkRestTemplate")
    private lateinit var kodeverkRestTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    private lateinit var pensjonsinformasjonClient: PensjonsinformasjonClient

    @MockkBean
    private lateinit var personService: PersonService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Gitt det ikke finnes en P_BUC_02 uten noen SED med avdød så skal det vies et tomt resultat`() {

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"
        val saksNr =  null

        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) }returns NorskIdent(gjenlevendeFnr)

        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")
        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        //gjenlevende rinasak
        val rinaGjenlevUrl = dummyRinasakUrl(avdodFnr, status = "\"open\"")
        every { restEuxTemplate.exchange( rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

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
    fun `Gitt det finnes gjenlevende og en avdød på P_BUC_02 så skal det hentes og returneres en liste av buc`() {

        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"
        val vedtakid = "2312123123123"
        val saknr = "1203201322"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid) } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)
        //gjenlevende aktoerid -> gjenlevendefnr
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId)) } returns NorskIdent(gjenlevendeFnr)

        //buc02 - avdød rinasak
        val rinaSakerBuc02 = listOf(dummyRinasak("1010", "P_BUC_02"), dummyRinasak("9859667", "P_BUC_01"))
        val rinaBuc02url = dummyRinasakUrl(avdodFnr, status =  "\"open\"")


        every { restEuxTemplate.exchange( rinaBuc02url.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(rinaSakerBuc02.toJson())

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) } returns ResponseEntity.ok().body(  dummySafMetaResponse() )
        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { restEuxTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )

        val result = mockMvc.perform(get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/$saknr/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
            [{"euxCaseId":"1010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"01010100001","kilde":"AVDOD"}]
        """.trimIndent()

        verify(atLeast = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, any(), String::class.java) }
        verify(atLeast = 1) { restSafTemplate.exchange("/", HttpMethod.POST, eq(httpEntity), String::class.java) }
        JSONAssert.assertEquals(expected, response, false)

    }

        @Test
        fun `Gitt det finnes gjenlevende og en avdød på buc02 og fra SAF så skal det hentes og lever en liste av buc`() {
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

            every { restEuxTemplate.exchange( "/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())


            val response = mockMvc.perform(get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/$saknr/vedtak/$vedtakid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn().response.contentAsString


            val bucViewResponse = """
                [{"euxCaseId":"1010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"01010100001","kilde":"SAF","internationalId":"1000100010001000"}]
            """.trimIndent()

            assertTrue {response.contains(avdodFnr)}
            JSONAssert.assertEquals(response, bucViewResponse, false)

            verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }
            verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }

        }

    @Test
    fun `Gitt det finnes gjenlevende og en avdød kun fra SAF så skal det hentes og lever en liste av buc med subject`() {
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
        val rinaBuc02url = dummyRinasakAvdodUrl(avdodFnr, "P_BUC_02")
        every { restEuxTemplate.exchange( eq(rinaBuc02url.toUriString()), eq(HttpMethod.GET), null, eq(String::class.java)) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

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

        //buc02 sed
        val rinabucdocumentidpath = "/buc/1010/sed/1"
        every { restEuxTemplate.exchange( rinabucdocumentidpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( sedjson )

        every { restEuxTemplate.exchange( "/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(emptyList<Rinasak>().toJson())

        val result = mockMvc.perform(get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/$saknr/vedtak/$vedtakid")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val bucViewResponse = """
            [{"euxCaseId":"1010","buctype":"P_BUC_02","aktoerId":"1123123123123123","saknr":"1203201322","avdodFnr":"01010100001","kilde":"SAF","internationalId":"1000100010001000"}]
        """.trimIndent()

        val response = result.response.getContentAsString(charset("UTF-8"))
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/buc/1010", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java) }
        assertTrue { response.contains(avdodFnr) }
        JSONAssert.assertEquals(response, bucViewResponse, false)
    }

    @Test
    fun `Gitt det finnes en gjenlevende og avdød hvor buc05 buc06 og buc10 finnes Så skal det returneres en liste av buc`() {

        val gjenlevendeFnr = "1234567890000"
        val gjenlevendeAktoerId = "1123123123123123"
        val avdodFnr = "01010100001"
        val vedtakid = "2312123123123"

        every { pensjonsinformasjonClient.hentAltPaaVedtak(vedtakid)  } returns mockVedtak(avdodFnr, gjenlevendeAktoerId)

        //gjenlevende aktoerid -> gjenlevendefnr
        every {personService.hentIdent(IdentType.NorskIdent, AktoerId(gjenlevendeAktoerId))  } returns NorskIdent(gjenlevendeFnr)

        //buc05, 06 og 10 avdød rinasaker
        val rinaSakerBuc05 = listOf(
            dummyRinasak("2020", "P_BUC_05"),
            dummyRinasak("3030", "P_BUC_06"),
            dummyRinasak("4040", "P_BUC_10")
        )

        every { restEuxTemplate.exchange( eq("/rinasaker?fødselsnummer=01010100001&status=\"open\""), eq(HttpMethod.GET), null, eq(String::class.java))  } returns ResponseEntity.ok().body( rinaSakerBuc05.toJson())

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina("2020") )


        val expected = """
            [
                {"euxCaseId":"2020","buctype":"P_BUC_05","aktoerId":"1123123123123123","saknr":"2020","avdodFnr":"01010100001","kilde":"SAF","internationalId":null},
                {"euxCaseId":"3030","buctype":"P_BUC_06","aktoerId":"1123123123123123","saknr":"2020","avdodFnr":"01010100001","kilde":"AVDOD","internationalId":null},
                {"euxCaseId":"4040","buctype":"P_BUC_10","aktoerId":"1123123123123123","saknr":"2020","avdodFnr":"01010100001","kilde":"AVDOD","internationalId":null}
            ]

        """.trimIndent()

        val result = mockMvc.perform(get("/buc/rinasaker/$gjenlevendeAktoerId/saknr/2020/vedtak/$vedtakid")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        println(response)

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restSafTemplate.exchange(eq("/") , HttpMethod.POST, eq(httpEntity), String::class.java) }
        JSONAssert.assertEquals(response, expected, false)
    }

    @Test
    fun `Hent mulige rinasaker for aktoer uten vedtak og saf`() {
        val aktoerId = "1123123123123123"
        val pesysSaknr = "100001000"

        val httpEntity = dummyHeader(dummySafReqeust(aktoerId))
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina( "5195021", "5922554" ) )

        every { restEuxTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5922554", processDefinitionName = "P_BUC_03").toJson() )
        every { restEuxTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5195021", processDefinitionName = "P_BUC_03").toJson() )

        val result = mockMvc.perform(
                get("/buc/rinasaker/joark/$aktoerId/pesyssak/$pesysSaknr")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
                [{"euxCaseId":"5195021","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"SAF"},
                {"euxCaseId":"5922554","buctype":"P_BUC_03","aktoerId":"1123123123123123","saknr":"100001000","avdodFnr":null,"kilde":"SAF"}]
        """.trimIndent()

        JSONAssert.assertEquals(expected, response, false)

    }

    @Test
    fun `Hent mulige rinasaker for fnr fra euxrina`() {
        val fnr = "1234567890000"
        val aktoerId = "1123123123123123"
        val pesyssaknr = "100001000"

        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnr)

        every { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&status=\"open\"", HttpMethod.GET, null, String::class.java) } .answers( FunctionAnswer { Thread.sleep(250);
            ResponseEntity.ok().body(listOf(dummyRinasak("5195021", "P_BUC_03"), dummyRinasak("5922554", "P_BUC_03") ).toJson() )
        })
        every { restEuxTemplate.exchange( "/buc/5922554", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5922554", processDefinitionName = "P_BUC_03").toJson() )
        every { restEuxTemplate.exchange( "/buc/5195021", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( Buc(id = "5195021", processDefinitionName = "P_BUC_03").toJson() )

        val result = mockMvc.perform(
                MockMvcRequestBuilders.get("/buc/rinasaker/euxrina/$aktoerId/pesyssak/$pesyssaknr")
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

}