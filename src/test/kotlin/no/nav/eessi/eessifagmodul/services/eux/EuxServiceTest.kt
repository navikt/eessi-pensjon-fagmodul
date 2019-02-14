package no.nav.eessi.eessifagmodul.services.eux

import com.nhaarman.mockito_kotlin.*
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.String
import kotlin.test.assertTrue


@RunWith(MockitoJUnitRunner::class)
class EuxServiceTest {

    private val logger: Logger by lazy { LoggerFactory.getLogger(EuxServiceTest::class.java) }

    private lateinit var service: EuxService

    @Mock
    private lateinit var mockrestTemplate: RestTemplate


    @Before
    fun setup() {
        logger.debug("Starting tests.... ...")
        service = EuxService(mockrestTemplate)
    }

    @After
    fun takedown() {
        //Mockito.reset(service)
        Mockito.reset(mockrestTemplate)
    }


    @Test
    fun opprettUriComponentPath() {
        val path = "/buc/{RinaSakId}/sed"
        val uriParams = mapOf("RinaSakId" to "12345")
        val builder = UriComponentsBuilder.fromUriString(path)
                .queryParam("KorrelasjonsId", "c0b0c068-4f79-48fe-a640-b9a23bf7c920")
                .buildAndExpand(uriParams)
        val str = builder.toUriString()
        assertEquals("/buc/12345/sed?KorrelasjonsId=c0b0c068-4f79-48fe-a640-b9a23bf7c920", str)
    }


    //opprett buc og sed ok
    @Test
    fun `Calling EuxService| forventer korrekt svar tilbake fra et kall til opprettBucSed`() {
        val bucresp = BucSedResponse("123456", "2a427c10325c4b5eaf3c27ba5e8f1877")
        val response: ResponseEntity<String> = ResponseEntity(mapAnyToJson(bucresp), HttpStatus.OK)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val httpEntity = HttpEntity(SED("P2000").toJson(), headers)

        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(response)
        val result = service.opprettBucSed(SED("P2000"), "P_BUC_99", "NAVT003", "1234567")

        assertEquals("123456", result.caseId)
        assertEquals("2a427c10325c4b5eaf3c27ba5e8f1877", result.documentId)

    }

    //opprett buc og sed feiler ved oppreting
    @Test(expected = RinaCasenrIkkeMottattException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til opprettBucSed`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.BAD_REQUEST)
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(errorresponse)
        service.opprettBucSed(SED("P2200"), "P_BUC_99", "NAVT003", "1231233")
    }

    //opprett buc og sed feil med eux service
    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til opprettBucSed`() {
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        service.opprettBucSed(SED("P2000"), "P_BUC_99", "NAVT003", "213123")
    }

    @Test
    fun `forventer et korrekt navsed P6000 ved kall til getSedOnBucByDocumentId`() {
        val filepath = "src/test/resources/json/nav/P6000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        //val response = euxOidcRestTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, String::class.java)
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(response)
        val result = service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")

        assertEquals(orgsed, result)
        assertEquals("P6000", result.sed)

    }

    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til getSedOnBucByDocumentId`() {
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(java.lang.RuntimeException::class.java)
        service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")
    }

    @Test(expected = SedDokumentIkkeLestException::class)
    fun `Calling EuxService| feiler med motta navsed fra eux med kall til getSedOnBucByDocumentId`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.UNAUTHORIZED)
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(errorresponse)
        service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")
    }

    //Test Hent Buc
    @Test
    fun `Calling EuxService| forventer korrekt svar tilbake fra et kall til hentbuc`() {
        val filepath = "src/test/resources/json/buc/buc-22909_v4.1.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        kotlin.test.assertTrue(validateJson(json))
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(response)
        val result = service.getBuc("P_BUC_99")
        assertEquals("22909", result.id)
    }

    @Test(expected = BucIkkeMottattException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til hentbuc`() {
        val errorresponse = ResponseEntity<String?>("", HttpStatus.BAD_REQUEST)
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(errorresponse)
        service.getBuc("P_BUC_99")
    }

    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til hentbuc`() {
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        service.getBuc("P_BUC_99")
    }


    //opprett sed på en valgt buc ok
    @Test
    fun `Calling EuxService| forventer korrekt svar tilbake fra et kall til opprettSedOnBuc`() {
        val response: ResponseEntity<String> = ResponseEntity("323413415dfvsdfgq343145sdfsdfg34135", HttpStatus.OK)
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(response)

        val result = service.opprettSedOnBuc(SED("P2000"), "123456")

        assertEquals("123456", result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    //opprett sed på en valgt buc, feiler ved oppreting
    @Test(expected = SedDokumentIkkeOpprettetException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til opprettSedOnBuc`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.BAD_REQUEST)
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(errorresponse)
        service.opprettSedOnBuc(SED("P2200"), "1231233")
    }

    //opprett sed på en valgt buc, feil med eux service
    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til opprettSedOnBuc`() {
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        service.opprettSedOnBuc(SED("P2000"), "213123")
    }


    @Test
    fun `Calling EuxService| forventer OK ved sletting av valgt SED på valgt buc`() {
        val response: ResponseEntity<String> = ResponseEntity(HttpStatus.OK)
        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doReturn(response).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.eq("/buc/${euxCaseId}/sed/${documentId}"),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.eq(null),
                ArgumentMatchers.eq(String::class.java)
        )

        val result = service.deleteDocumentById(euxCaseId, documentId)
        assertEquals(true, result)
    }


    @Test(expected = SedIkkeSlettetException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til deleteDocumentById`() {
        val response: ResponseEntity<String> = ResponseEntity(HttpStatus.NOT_EXTENDED)
        whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.DELETE), eq(null), eq(String::class.java))).thenReturn(response)
        service.deleteDocumentById("12132131", "12312312-123123123123")
    }

    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til deleteDocumentById`() {
        //whenever(mockrestTemplate.exchange(anyString(), eq(HttpMethod.DELETE), eq(null), eq(String::class.java))).thenThrow(RuntimeException::class.java)

        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doThrow(RuntimeException("error")).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.eq("/buc/${euxCaseId}/sed/${documentId}/send"),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.eq(null),
                ArgumentMatchers.eq(String::class.java)
        )

        service.deleteDocumentById(euxCaseId, documentId)
    }

    @Test
    fun `Calling EuxService| forventer korrekt svar tilbake når SED er sendt OK på sendDocumentById`() {
        val response: ResponseEntity<String> = ResponseEntity(HttpStatus.OK)
        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doReturn(response).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.eq("/buc/${euxCaseId}/sed/${documentId}/send"),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.eq(null),
                ArgumentMatchers.eq(String::class.java)
        )

        val result = service.sendDocumentById(euxCaseId, documentId)
        assertEquals(true, result)
    }

    @Test(expected = SedDokumentIkkeSendtException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til sendDocumentById`() {
        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"
        val errorresponse = ResponseEntity.badRequest().body("")

        doReturn(errorresponse).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.eq("/buc/${euxCaseId}/sed/${documentId}/send"),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.eq(null),
                ArgumentMatchers.eq(String::class.java)
        )
        service.sendDocumentById(euxCaseId, documentId)
    }

    //opprett sed på en valgt buc, feil med eux service
    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til sendDocumentById`() {
        doThrow(RuntimeException("error")).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.eq("/buc/1234567/sed/3123sfdf23-4324svfsdf324/send"),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.eq(null),
                ArgumentMatchers.eq(String::class.java)
        )
        service.sendDocumentById("123456", "213213-123123-123123")
    }


}

