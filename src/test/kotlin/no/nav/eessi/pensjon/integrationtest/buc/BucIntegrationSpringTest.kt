package no.nav.eessi.pensjon.integrationtest.buc

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Properties
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Traits
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.time.Month
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class] ,webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
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
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, null, null, null)
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
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, null, null, null)
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

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&rinasaksnummer=&buctype=&status=", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_06&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_10&status=\"open\"", HttpMethod.GET, null, String::class.java) }

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
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, null, null, null)
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
        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns ResponseEntity.ok().body(  dummySafMetaResponseMedRina("1010"))
        val rinaSafUrl = dummyRinasakUrl("", null, "1010", null)
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

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&rinasaksnummer=&buctype=&status=", HttpMethod.GET, null, String::class.java) }
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
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, null, null, null)
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(
            DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT")
        )
        val buc02 = Buc(id = "1010", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/1010"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //saf (vedlegg meta) gjenlevende
        val httpEntity = dummyHeader(dummySafReqeust(gjenlevendeAktoerId))

        every { restSafTemplate.exchange(eq("/"), eq(HttpMethod.POST), eq(httpEntity), eq(String::class.java)) } returns  ResponseEntity.ok().body(  dummySafMetaResponseMedRina("1010") )
        val rinaSafUrl = dummyRinasakUrl("", null, "1010", null)
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

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&rinasaksnummer=&buctype=&status=", HttpMethod.GET, null, String::class.java) }
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
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, null, null, null)
        every { restEuxTemplate.exchange(rinaGjenlevUrl.toUriString(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( emptyList<Rinasak>().toJson())

        //dummy date
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        //buc02
        val docItems = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P2100, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc02 = Buc(id = "1010", processDefinitionName = "P_BUC_02", startDate = lastupdate, lastUpdate = lastupdate,  documents = docItems)

        val rinabucpath = "/buc/1010"
        every { restEuxTemplate.exchange( rinabucpath, HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body( buc02.toJson() )

        //buc05
        val doc05Items = listOf(DocumentsItem(id = "1", creationDate = lastupdate, lastUpdate = lastupdate, status = "sent", type = SedType.P8000, direction = "OUT"),
            DocumentsItem(id = "2", creationDate = lastupdate,  lastUpdate = lastupdate, status = "draft", type = SedType.P4000, direction = "OUT"))
        val buc05 = Buc(id = "2020", processDefinitionName = "P_BUC_05", startDate = lastupdate, lastUpdate = lastupdate,  documents = doc05Items)

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

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_02&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&rinasaksnummer=&buctype=&status=", HttpMethod.GET, null, String::class.java) }
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
        val rinaGjenlevUrl = dummyRinasakUrl(gjenlevendeFnr, null, null, null)
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

        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_05&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_06&status=\"open\"", HttpMethod.GET, null, String::class.java)}
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=01010100001&rinasaksnummer=&buctype=P_BUC_10&status=\"open\"", HttpMethod.GET, null, String::class.java) }
        verify (exactly = 1) { restEuxTemplate.exchange("/rinasaker?fødselsnummer=1234567890000&rinasaksnummer=&buctype=&status=", HttpMethod.GET, null, String::class.java) }
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
                    "id": "1",
                    "parentDocumentId": null,
                    "type": "P2100",
                    "conversations":null,
                    "isSendExecuted":null,
                    "direction": "OUT",
                    "status": "sent",
                    "creationDate": 1596751200000,
                    "lastUpdate": 1596751200000,
                    "displayName": null,
                    "participants": null,
                    "attachments": [],
                    "version": "1",
                    "versions":null,
                    "firstVersion": null,
                    "lastVersion": null,
                    "allowsAttachments": null,
                    "typeVersion":null,
                    "message": null
                  },
                  {
                    "id": "2",
                    "parentDocumentId": null,
                    "type": "P4000",
                    "conversations":null,
                    "isSendExecuted":null,
                    "direction": "OUT",
                    "status": "draft",
                    "creationDate": 1596751200000,
                    "lastUpdate": 1596751200000,
                    "displayName": null,
                    "participants": null,
                    "attachments": [],
                    "version": "1",
                    "versions":null,
                    "firstVersion": null,
                    "lastVersion": null,
                    "allowsAttachments": null,
                    "typeVersion":null,
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
                    "id": "1",
                    "parentDocumentId": null,
                    "type": "P2100",
                    "status": "sent",
                    "creationDate": 1596751200000,
                    "lastUpdate": 1596751200000,
                    "displayName": null,
                    "participants": null,
                    "attachments": [],
                    "version": "1",
                    "firstVersion": null,
                    "lastVersion": null,
                    "allowsAttachments": null,
                    "message": null,
                    "isSendExecuted":null,
                    "direction": "OUT",
                    "versions":null,
                    "typeVersion":null,
                    "conversations":null
                  },
                  {
                    "id": "2",
                    "parentDocumentId": null,
                    "type": "P4000",
                    "status": "draft",
                    "creationDate": 1596751200000,
                    "lastUpdate": 1596751200000,
                    "displayName": null,
                    "participants": null,
                    "attachments": [],
                    "version": "1",
                    "firstVersion": null,
                    "lastVersion": null,
                    "allowsAttachments": null,
                    "message": null,
                    "isSendExecuted":null,
                    "direction": "OUT",
                    "versions":null,
                    "typeVersion":null,
                    "conversations":null
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
                    "id": "1",
                    "parentDocumentId": null,
                    "type": "P8000",
                    "status": "sent",
                    "creationDate": 1596751200000,
                    "lastUpdate": 1596751200000,
                    "displayName": null,
                    "participants": null,
                    "attachments": [],
                    "version": "1",
                    "firstVersion": null,
                    "lastVersion": null,
                    "allowsAttachments": null,
                    "message": null,
                    "isSendExecuted":null,
                    "direction": "OUT",
                    "versions":null,
                    "typeVersion":null,
                    "conversations":null
                  },
                  {
                    "id": "2",
                    "parentDocumentId": null,
                    "type": "P4000",
                    "status": "draft",
                    "creationDate": 1596751200000,
                    "lastUpdate": 1596751200000,
                    "displayName": null,
                    "participants": null,
                    "attachments": [],
                    "version": "1",
                    "firstVersion": null,
                    "lastVersion": null,
                    "allowsAttachments": null,
                    "message": null,
                    "isSendExecuted":null,
                    "direction": "OUT",
                    "versions":null,
                    "typeVersion":null,
                    "conversations":null
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

    private fun dummyRinasakAvdodUrl(avod: String, bucType: String? = "P_BUC_02", status: String? =  "\"open\"") = dummyRinasakUrl(avod, bucType, null, status)
    private fun dummyRinasakUrl(fnr: String, bucType: String? = null, euxCaseId: String? = null, status: String? = null) : UriComponents {
        val uriComponent = UriComponentsBuilder.fromPath("/rinasaker")
                .queryParam("fødselsnummer", fnr)
                .queryParam("rinasaksnummer", euxCaseId ?: "")
                .queryParam("buctype", bucType ?: "")
                .queryParam("status", status ?: "")
                .build()
        return uriComponent
    }

    private fun dummyRinasak(rinaSakId: String, bucType: String): Rinasak {
        return Rinasak(rinaSakId, bucType, Traits(), "", Properties(), "open")
    }
}