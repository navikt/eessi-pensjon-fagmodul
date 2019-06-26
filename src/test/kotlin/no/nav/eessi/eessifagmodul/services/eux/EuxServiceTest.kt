package no.nav.eessi.eessifagmodul.services.eux

import com.nhaarman.mockito_kotlin.*
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.services.saf.SafService
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
import org.springframework.web.client.*
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.String
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(MockitoJUnitRunner::class)
class EuxServiceTest {

    private val logger: Logger by lazy { LoggerFactory.getLogger(EuxServiceTest::class.java) }

    private lateinit var service: EuxService

    @Mock
    private lateinit var mockEuxrestTemplate: RestTemplate

    @Mock
    private lateinit var mockSafService: SafService

    @Before
    fun setup() {
        logger.debug("Starting tests.... ...")
        mockEuxrestTemplate.errorHandler = DefaultResponseErrorHandler()
        service = EuxService(mockEuxrestTemplate, mockSafService)
    }

    @After
    fun takedown() {
        Mockito.reset(mockEuxrestTemplate)
    }


    @Test
    fun opprettUriComponentPath() {
        val path = "/type/{RinaSakId}/sed"
        val uriParams = mapOf("RinaSakId" to "12345")
        val builder = UriComponentsBuilder.fromUriString(path)
                .queryParam("KorrelasjonsId", "c0b0c068-4f79-48fe-a640-b9a23bf7c920")
                .buildAndExpand(uriParams)
        val str = builder.toUriString()
        assertEquals("/type/12345/sed?KorrelasjonsId=c0b0c068-4f79-48fe-a640-b9a23bf7c920", str)
    }


    //opprett type og sed ok
    @Test
    fun `Calling EuxService| forventer korrekt svar tilbake fra et kall til opprettBucSed`() {
        val bucresp = BucSedResponse("123456", "2a427c10325c4b5eaf3c27ba5e8f1877")
        val response: ResponseEntity<String> = ResponseEntity(mapAnyToJson(bucresp), HttpStatus.OK)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val httpEntity = HttpEntity(SED("P2000").toJson(), headers)

        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(response)
        val result = service.opprettBucSed(SED("P2000"), "P_BUC_99", "NO:NAVT003", "1234567")

        assertEquals("123456", result.caseId)
        assertEquals("2a427c10325c4b5eaf3c27ba5e8f1877", result.documentId)

    }

    //opprett type og sed feiler ved oppreting
    @Test(expected = RinaCasenrIkkeMottattException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til opprettBucSed`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.BAD_REQUEST)
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(errorresponse)
        service.opprettBucSed(SED("P2200"), "P_BUC_99", "NO:NAVT003", "1231233")
    }

    //opprett type og sed feil med eux service
    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til opprettBucSed`() {
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        service.opprettBucSed(SED("P2000"), "P_BUC_99", "NO:NAVT003", "213123")
    }

    @Test
    fun `forventer et korrekt navsed P6000 ved kall til getSedOnBucByDocumentId`() {
        val filepath = "src/test/resources/json/nav/P6000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        //val response = euxOidcRestTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, String::class.java)
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(response)
        val result = service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")

        assertEquals(orgsed, result)
        assertEquals("P6000", result.sed)

    }

    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til getSedOnBucByDocumentId`() {
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(java.lang.RuntimeException::class.java)
        service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")
    }

    @Test(expected = SedDokumentIkkeLestException::class)
    fun `Calling EuxService| feiler med motta navsed fra eux med kall til getSedOnBucByDocumentId`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.UNAUTHORIZED)
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(errorresponse)
        service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")
    }

    //Test Hent Buc
    @Test
    fun `Calling EuxService| forventer korrekt svar tilbake fra et kall til hentbuc`() {
        val filepath = "src/test/resources/json/buc/buc-22909_v4.1.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(response)
        val result = service.getBuc("P_BUC_99")
        assertEquals("22909", result.id)
    }

    @Test(expected = BucIkkeMottattException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til hentbuc`() {
        val errorresponse = ResponseEntity<String?>("", HttpStatus.BAD_REQUEST)
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(errorresponse)
        service.getBuc("P_BUC_99")
    }

    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til hentbuc`() {
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        service.getBuc("P_BUC_99")
    }


    //opprett sed på en valgt type ok
    @Test
    fun `Calling EuxService| forventer korrekt svar tilbake fra et kall til opprettSedOnBuc`() {
        val response: ResponseEntity<String> = ResponseEntity("323413415dfvsdfgq343145sdfsdfg34135", HttpStatus.OK)
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(response)

        val result = service.opprettSedOnBuc(SED("P2000"), "123456")

        assertEquals("123456", result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    //opprett sed på en valgt type, feiler ved oppreting
    @Test(expected = SedDokumentIkkeOpprettetException::class)
    fun `Calling EuxService| feiler med svar tilbake fra et kall til opprettSedOnBuc`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.BAD_REQUEST)
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(errorresponse)
        service.opprettSedOnBuc(SED("P2200"), "1231233")
    }

    //opprett sed på en valgt type, feil med eux service
    @Test(expected = EuxGenericServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til opprettSedOnBuc`() {
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        service.opprettSedOnBuc(SED("P2000"), "213123")
    }


    @Test
    fun `Calling EuxService| forventer OK ved sletting av valgt SED på valgt buc`() {
        val response: ResponseEntity<String> = ResponseEntity(HttpStatus.OK)
        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doReturn(response).whenever(mockEuxrestTemplate).exchange(
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
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.DELETE), eq(null), eq(String::class.java))).thenReturn(response)
        service.deleteDocumentById("12132131", "12312312-123123123123")
    }

    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til deleteDocumentById`() {
        //whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.DELETE), eq(null), eq(String::class.java))).thenThrow(RuntimeException::class.java)

        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doThrow(RuntimeException("error")).whenever(mockEuxrestTemplate).exchange(
                ArgumentMatchers.eq("/type/${euxCaseId}/sed/${documentId}/send"),
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

        doReturn(response).whenever(mockEuxrestTemplate).exchange(
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

        doReturn(errorresponse).whenever(mockEuxrestTemplate).exchange(
                ArgumentMatchers.eq("/buc/${euxCaseId}/sed/${documentId}/send"),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.eq(null),
                ArgumentMatchers.eq(String::class.java)
        )
        service.sendDocumentById(euxCaseId, documentId)
    }

    //opprett sed på en valgt type, feil med eux service
    @Test(expected = EuxServerException::class)
    fun `Calling EuxService| feiler med kontakt fra eux med kall til sendDocumentById`() {
        doThrow(RuntimeException("error")).whenever(mockEuxrestTemplate).exchange(
                ArgumentMatchers.eq("/type/1234567/sed/3123sfdf23-4324svfsdf324/send"),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.eq(null),
                ArgumentMatchers.eq(String::class.java)
        )
        service.sendDocumentById("123456", "213213-123123-123123")
    }


    @Test
    fun callingEuxServiceListOfRinasaker_Ok() {
        val filepath = "src/test/resources/json/rinasaker/rinasaker_12345678901.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        val orgRinasaker = mapJsonToAny(json, typeRefs<List<Rinasak>>())
        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                eq(String::class.java))
        ).thenReturn(response)


        val result = service.getRinasaker("12345678900")

        assertEquals(orgRinasaker, result)
        assertEquals(orgRinasaker.size, result.size)

    }

    @Test(expected = IOException::class)
    fun callingEuxServiceListOfRinasaker_IOError() {

        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(ResourceAccessException("I/O error"))
        service.getRinasaker("12345678900")

    }

    @Test(expected = HttpClientErrorException::class)
    fun callingEuxServiceListOfRinasaker_ClientError() {

        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Error in Token", HttpHeaders(), "Error in Token".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(clientError)
        service.getRinasaker("12345678900")

    }

    @Test(expected = HttpServerErrorException::class)
    fun callingEuxServiceListOfRinasaker_ServerError() {

        val serverError = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Error in Gate", HttpHeaders(), "Error in Gate".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(anyString(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(serverError)
        service.getRinasaker("12345678900")

    }

    @Test
    fun callingEuxServiceFormenuUI_AllOK() {

        val rinasakerjson = "src/test/resources/json/rinasaker/rinasaker_34567890111.json"
        val rinasakStr = String(Files.readAllBytes(Paths.get(rinasakerjson)))
        assertTrue(validateJson(rinasakStr))

        val bucjson = "src/test/resources/json/buc/buc-22909_v4.1.json"
        val bucStr = String(Files.readAllBytes(Paths.get(bucjson)))
        assertTrue(validateJson(bucStr))

        val orgRinasaker = mapJsonToAny(rinasakStr, typeRefs<List<Rinasak>>())
        val orgBuc = mapJsonToAny(bucStr, typeRefs<Buc>())

        val mockService: EuxService = Mockito.mock(EuxService::class.java)

        whenever(mockService.getRinasaker("12345678900")).thenReturn(orgRinasaker)

        whenever(mockService.getBucUtils("8877665511")).thenReturn(BucUtils(orgBuc))

        val result = service.getBucAndSedView("12345678900", "001122334455", null, null, mockService)

        assertNotNull(result)

        assertEquals(orgRinasaker.size, result.size)


    }


    @Test
    fun callingEuxServiceCreateBuc_Ok() {

        val mockBuc = "12345678909999"
        val response: ResponseEntity<String> = ResponseEntity("12345678909999", HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenReturn(response)

        val result = service.createBuc("P_BUC_01")

        assertEquals(mockBuc, result)
    }

    @Test(expected = IOException::class)
    fun callingEuxServiceCreateBuc_IOError() {

        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenThrow(ResourceAccessException("I/O error"))

        service.createBuc("P_BUC_01")

    }

    @Test(expected = HttpClientErrorException::class)
    fun callingEuxServiceCreateBuc_ClientError() {

        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Error in Token", HttpHeaders(), "Error in Token".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenThrow(clientError)

        service.createBuc("P_BUC_01")

    }

    @Test(expected = HttpServerErrorException::class)
    fun callingEuxServiceCreateBuc_ServerError() {

        val serverError = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Error in Gate", HttpHeaders(), "Error in Gate".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenThrow(serverError)

        service.createBuc("P_BUC_01")

    }

    @Test(expected = IllegalArgumentException::class)
    fun callingEuxServicePutBucDeltager_WrongParticipantInput() {
        service.putBucDeltager("126552","NO")
    }

    @Test(expected = HttpClientErrorException::class)
    fun callingEuxServicePutBucDeltager_ClientError() {

        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Token authorization error", HttpHeaders(),"Token authorization error".toByteArray(),Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(clientError)

        service.putBucDeltager("126552","NO:NAVT007")
    }

    @Test(expected = HttpServerErrorException::class)
    fun putBucDeltager_ServerError(){

        val serverError = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY,"Server error",HttpHeaders(),"Server error".toByteArray(),Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(serverError)

        service.putBucDeltager("122732","NO:NAVT02")
    }

    @Test(expected = IOException::class)
    fun putBucDeltager_ResourceAccessError() {
        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(ResourceAccessException("I/O Error"))

        service.putBucDeltager("122732","NO:NAVT02")
    }


    @Test
    fun callingPutBucDeltager_OK() {

        val theResponse = ResponseEntity.ok().body("")

        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenReturn(theResponse)

        val result = service.putBucDeltager("122732","NO:NAVT005")
        assertEquals(true,result)

    }

    @Test
    fun hentYtelseKravtypeTesterPaaP15000ok() {
        val filepath = "src/test/resources/json/nav/P15000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                eq(String::class.java))
        ).thenReturn(response)

        //val sed = getSedOnBucByDocumentId(euxCaseId, documentId)

        val kravytelse = service.hentYtelseKravtype("1234567890","100001000010000")

        assertEquals("01", kravytelse.type)
        assertEquals("2019-02-01", kravytelse.dato)
    }

    @Test(expected = SedDokumentIkkeGyldigException::class)
    fun hentYtelseKravtypeTesterPaaP15000FeilerVedUgyldigSED() {
        val filepath = "src/test/resources/json/nav/P9000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                eq(String::class.java))
        ).thenReturn(response)

        service.hentYtelseKravtype("1234567890","100001000010000")
    }



}

