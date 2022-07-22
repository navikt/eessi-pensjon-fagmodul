package no.nav.eessi.pensjon.integrationtest.sed

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.fagmodul.models.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.ApiSubject
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.KravType
import no.nav.eessi.pensjon.fagmodul.models.SubjectFnr
import no.nav.eessi.pensjon.integrationtest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate


@SpringBootTest(classes = [IntegrasjonsTestConfig::class,UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
@EmbeddedKafka
class OpprettPrefillSedIntegrationTest {

    @MockkBean(name = "prefillOAuthTemplate")
    private lateinit var prefillOAuthTemplate: RestTemplate

    @MockkBean(name = "euxNavIdentRestTemplate")
    private lateinit var restEuxTemplate: RestTemplate

    @MockkBean(name = "euxSystemRestTemplate")
    private lateinit var euxUserNameRestTemplate: RestTemplate

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var restSafTemplate: RestTemplate

    @MockkBean(name = "safRestOidcRestTemplate")
    private lateinit var safRestOidcRestTemplate: RestTemplate

    @MockkBean(name = "pensjoninformasjonRestTemplate")
    private lateinit var pensjoninformasjonRestTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    private lateinit var personService: PersonService

    @Autowired
    private lateinit var mockMvc: MockMvc

    private companion object {
        const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"

        const val X_REQUEST_ID = "21abba12-22gozilla12-31daftpunk10"
    }

    @Test
    fun `Gitt at det opprettes ny SED P2000 på ny tom BUC Når det mangler deltakere SÅ skal det kastes en exception`() {
        val euxRinaid = "1000000001"

        val apiRequest = dummyApijson(
            "1212000",
            "120012",
            AKTOER_ID,
            SedType.P2000,
            "P_BUC_01",
            null,
            KravType.ALDER,
            euxRinaid = euxRinaid
        )

        val tomBucJson = javaClass.getResource("/json/buc/P_BUC_01_4.2_tom.json").readText()

        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID)) } returns NorskIdent(FNR_VOKSEN)

        every { restEuxTemplate.exchange( "/buc/$euxRinaid", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(tomBucJson)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/sed/add/")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apiRequest.toJson()))
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()

        val responsePair = responseMvcDecode(result)

        assertEquals(HttpStatus.BAD_REQUEST, responsePair.first)
        assertEquals("Ingen deltakere/Institusjon er tom", responsePair.second)

    }

    @Test
    fun `Gitt at det opprettes ny SED P2000 på ny tom BUC Når deltaker er med SÅ skal SED opprettes og metadoc retureres`() {
        val euxRinaid = "1000000001"

        val nyDeltakere = listOf(
            InstitusjonItem(country = "FI", institution = "FI:200032", name="Finland test"),
            InstitusjonItem(country = "DK", institution = "DK:120030", name="Danmark test")
        )

        val apiRequest = dummyApijson(
            "1212000",
            "120012",
            AKTOER_ID,
            SedType.P2000,
            "P_BUC_01",
            null,
            KravType.ALDER,
            euxRinaid = euxRinaid,
            institutions = nyDeltakere)

        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID)) } returns NorskIdent(FNR_VOKSEN)

        val tomBucJson = javaClass.getResource("/json/buc/P_BUC_01_4.2_tom.json").readText()
        every { restEuxTemplate.exchange( "/buc/$euxRinaid", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(tomBucJson)

        val rinaputmottaker = "/buc/1000000001/mottakere?KorrelasjonsId=$X_REQUEST_ID&mottakere=FI:200032&mottakere=DK:120030"
        every { restEuxTemplate.exchange(rinaputmottaker, HttpMethod.PUT, null, String::class.java) } returns ResponseEntity.ok().body("")

        val prefillHeaders = HttpHeaders()
        prefillHeaders.contentType = MediaType.APPLICATION_JSON

        val prefillSEDjson = javaClass.getResource("/json/nav/P2000-NAV-FRA-UTLAND-KRAV.json").readText()
        every { prefillOAuthTemplate.exchange(
            "/sed/prefill",
            HttpMethod.POST,
            HttpEntity(apiRequest, prefillHeaders),
            String::class.java)
        } returns ResponseEntity.ok().body(prefillSEDjson)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val opprettSedHeader = HttpEntity(prefillSEDjson, headers)
        val ventePaAksjonVerdi = "false"

        every { restEuxTemplate.postForEntity(
            "/buc/$euxRinaid/sed?ventePaAksjon=$ventePaAksjonVerdi",
            opprettSedHeader,
            String::class.java) } returns ResponseEntity.ok().body("0b804938b8974c8ba52c253905424510")

        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/sed/add/")
                .header("x-request-id", X_REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(apiRequest.toJson()))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andReturn()

        val responsePair = responseMvcDecode(result)
        val responseData = responsePair.second

        val expected = """
            {"attachments":[],"displayName":"Old age pension claim","type":"P2000","conversations":null,"isSendExecuted":null,"id":"0b804938b8974c8ba52c253905424510","direction":"OUT","creationDate":1586931418000,"receiveDate":null,"typeVersion":null,"allowsAttachments":true,"versions":null,"lastUpdate":1586931418000,"parentDocumentId":null,"status":"empty","participants":null,"firstVersion":{"date":"2020-04-15T06:16:58.000+0000","id":"1"},"lastVersion":{"date":"2020-04-15T06:16:58.000+0000","id":"1"},"version":"1","message":null}
        """.trimIndent()

        JSONAssert.assertEquals(responseData, expected, false)

    }

    @Test
    fun `Gitt at det opprettes P6000 på existerende Buc med nye deltaker NÅR det alt finnes en X005 kladd SÅ skal det kastes en exceptipn`() {
        val euxRinaid = "1000000001"

        val nyDeltakere = listOf(
            InstitusjonItem(country = "FI", institution = "FI:200032", name="Finland test"),
            InstitusjonItem(country = "DK", institution = "DK:120030", name="Danmark test")
        )

        val apiRequest = dummyApijson(
            "1212000",
            "120012",
            AKTOER_ID,
            SedType.P6000,
            "P_BUC_02",
            FNR_VOKSEN_2,
            KravType.GJENLEV,
            euxRinaid = euxRinaid,
            institutions = nyDeltakere)

        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID)) } returns NorskIdent(FNR_VOKSEN)
        every { personService.hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_2)) } returns AktoerId("23423423423423423423423423423423423423423423423423")

        val tomBucJson = javaClass.getResource("/json/buc/buc-rina2020-P2K-X005.json").readText()
        every { restEuxTemplate.exchange( "/buc/$euxRinaid", HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(tomBucJson)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/sed/add/")
                .header("x-request-id", X_REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(apiRequest.toJson()))
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()

        val responsePair = responseMvcDecode(result)
        val responseStatus = responsePair.first
        val responseBody = responsePair.second

        assertEquals(HttpStatus.BAD_REQUEST, responseStatus)
        assertEquals("Utkast av type X005, finnes allerede i BUC", responseBody)

    }


    fun dummyApijson(
        sakid: String,
        vedtakid: String? = null,
        aktoerId: String,
        sedType: SedType = SedType.P2000,
        buc: String? = "P_BUC_06",
        fnravdod: String? = null,
        kravtype: KravType? = null,
        kravdato: String? = null,
        euxRinaid: String? = "12345",
        institutions: List<InstitusjonItem>? = emptyList()
    ): ApiRequest {

        val subject = if (fnravdod != null) {
            ApiSubject(null, SubjectFnr(fnravdod))
        } else {
            null
        }

        return ApiRequest(
            sakId = sakid,
            vedtakId = vedtakid,
            kravId = null,
            aktoerId = aktoerId,
            sed = sedType.name,
            buc = buc,
            kravType = kravtype,
            kravDato = kravdato,
            euxCaseId = euxRinaid,
            institutions = institutions,
            subject = subject
        )
    }

    fun responseMvcDecode(result: MvcResult): Pair<HttpStatus, String> {
        val status = HttpStatus.valueOf(result.response.status)
        return when(status) {
            HttpStatus.OK, HttpStatus.ACCEPTED, HttpStatus.CREATED -> Pair(HttpStatus.OK, result.response.getContentAsString(charset("UTF-8"))).also { println("ResponseDecode: $it") }
            else ->  Pair(status, result.response.errorMessage!!)
        }
    }


}