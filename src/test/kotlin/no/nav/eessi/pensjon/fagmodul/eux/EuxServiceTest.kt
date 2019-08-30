package no.nav.eessi.pensjon.fagmodul.eux

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.security.oidc.MockOIDCRequestContextHolder
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.utils.validateJson
import no.nav.security.spring.oidc.SpringOIDCRequestContextHolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.AdditionalMatchers.not
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.*
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import kotlin.String
import kotlin.streams.toList


@ExtendWith(MockitoExtension::class)
class EuxServiceTest {

    private lateinit var service: EuxService

    @Mock
    private lateinit var mockEuxrestTemplate: RestTemplate

    @Mock
    private lateinit var mockAsyncEuxService: AsyncEuxService

    @BeforeEach
    fun setup() {
        mockEuxrestTemplate.errorHandler = DefaultResponseErrorHandler()
        service = EuxService(mockEuxrestTemplate, SpringOIDCRequestContextHolder(), mockAsyncEuxService)
    }

    @AfterEach
    fun takedown() {
        Mockito.reset(mockEuxrestTemplate, mockAsyncEuxService)
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
    fun `Calling EuxService  forventer korrekt svar tilbake fra et kall til opprettBucSed`() {
        val bucresp = BucSedResponse("123456", "2a427c10325c4b5eaf3c27ba5e8f1877")
        val response: ResponseEntity<String> = ResponseEntity(mapAnyToJson(bucresp), HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(response)
        val result = service.opprettBucSed(SED("P2000"), "P_BUC_99", "NO:NAVT003", "1234567")

        assertEquals("123456", result.caseId)
        assertEquals("2a427c10325c4b5eaf3c27ba5e8f1877", result.documentId)

    }

    //opprett type og sed feiler ved oppreting
    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til opprettBucSed`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.BAD_REQUEST)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(errorresponse)

        assertThrows<RinaCasenrIkkeMottattException> {
            service.opprettBucSed(SED("P2200"), "P_BUC_99", "NO:NAVT003", "1231233")
        }
    }

    //opprett type og sed feil med eux service
    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til opprettBucSed`() {
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        assertThrows<EuxServerException> {
            service.opprettBucSed(SED("P2000"), "P_BUC_99", "NO:NAVT003", "213123")
        }
    }

    @Test
    fun `forventer et korrekt navsed P6000 ved kall til getSedOnBucByDocumentId`() {
        val filepath = "src/test/resources/json/nav/P6000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = SED.fromJson(json)
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        //val response = euxOidcRestTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, String::class.java)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(response)

        val result = service.getSedOnBucByDocumentId("12345678900", "0bb1ad15987741f1bbf45eba4f955e80")

        assertEquals(orgsed, result)
        assertEquals("P6000", result.sed)

    }

    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til getSedOnBucByDocumentId`() {
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(java.lang.RuntimeException::class.java)
        assertThrows<EuxServerException> {
            service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxService  feiler med motta navsed fra eux med kall til getSedOnBucByDocumentId`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.UNAUTHORIZED)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(errorresponse)
        assertThrows<SedDokumentIkkeLestException> {
            service.getSedOnBucByDocumentId("12345678900", "P_BUC_99")
        }
    }

    //Test Hent Buc
    @Test
    fun `Calling EuxService  forventer korrekt svar tilbake fra et kall til hentbuc`() {
        val filepath = "src/test/resources/json/buc/buc-22909_v4.1.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(response)
        val result = service.getBuc("P_BUC_99")
        assertEquals("22909", result.id)
    }

    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til hentbuc`() {
        val errorresponse = ResponseEntity<String?>("", HttpStatus.BAD_REQUEST)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(errorresponse)
        assertThrows<BucIkkeMottattException> {
            service.getBuc("P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til hentbuc`() {
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        assertThrows<EuxServerException> {
            service.getBuc("P_BUC_99")
        }
    }


    //opprett sed på en valgt type ok
    @Test
    fun `Calling EuxService  forventer korrekt svar tilbake fra et kall til opprettSedOnBuc`() {
        val response: ResponseEntity<String> = ResponseEntity("323413415dfvsdfgq343145sdfsdfg34135", HttpStatus.OK)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(response)

        val result = service.opprettSedOnBuc(SED("P2000"), "123456")

        assertEquals("123456", result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    //opprett sed på en valgt type, feiler ved oppreting
    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til opprettSedOnBuc`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.BAD_REQUEST)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(errorresponse)
        assertThrows<SedDokumentIkkeOpprettetException> {
            service.opprettSedOnBuc(SED("P2200"), "1231233")
        }
    }

    //opprett sed på en valgt type, feil med eux service
    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til opprettSedOnBuc`() {
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenThrow(RuntimeException::class.java)
        assertThrows<EuxGenericServerException> {
            service.opprettSedOnBuc(SED("P2000"), "213123")
        }
    }


    @Test
    fun `Calling EuxService  forventer OK ved sletting av valgt SED paa valgt buc`() {
        val response: ResponseEntity<String> = ResponseEntity(HttpStatus.OK)
        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doReturn(response).whenever(mockEuxrestTemplate).exchange(
                eq("/buc/${euxCaseId}/sed/${documentId}"),
                any(),
                eq(null),
                ArgumentMatchers.eq(String::class.java)
        )

        val result = service.deleteDocumentById(euxCaseId, documentId)
        assertEquals(true, result)
    }


    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til deleteDocumentById`() {
        val response: ResponseEntity<String> = ResponseEntity(HttpStatus.NOT_EXTENDED)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.DELETE), eq(null), eq(String::class.java))).thenReturn(response)
        assertThrows<SedIkkeSlettetException> {
            service.deleteDocumentById("12132131", "12312312-123123123123")
        }
    }

    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til deleteDocumentById`() {
        //whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.DELETE), eq(null), eq(String::class.java))).thenThrow(RuntimeException::class.java)

        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doThrow(RuntimeException("error")).whenever(mockEuxrestTemplate).exchange(
                eq("/type/${euxCaseId}/sed/${documentId}/send"),
                any(),
                eq(null),
                ArgumentMatchers.eq(String::class.java)
        )

        assertThrows<EuxServerException> {
            service.deleteDocumentById(euxCaseId, documentId)
        }
    }

    @Test
    fun `Calling EuxService  forventer korrekt svar tilbake naar SED er sendt OK paa sendDocumentById`() {
        val response: ResponseEntity<String> = ResponseEntity(HttpStatus.OK)
        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"

        doReturn(response).whenever(mockEuxrestTemplate).exchange(
                eq("/buc/${euxCaseId}/sed/${documentId}/send"),
                any(),
                eq(null),
                ArgumentMatchers.eq(String::class.java)
        )

        val result = service.sendDocumentById(euxCaseId, documentId)
        assertEquals(true, result)
    }

    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til sendDocumentById`() {
        val euxCaseId = "123456"
        val documentId = "213213-123123-123123"
        val errorresponse = ResponseEntity.badRequest().body("")

        doReturn(errorresponse).whenever(mockEuxrestTemplate).exchange(
                eq("/buc/${euxCaseId}/sed/${documentId}/send"),
                any(),
                eq(null),
                ArgumentMatchers.eq(String::class.java)
        )
        assertThrows<SedDokumentIkkeSendtException> {
            service.sendDocumentById(euxCaseId, documentId)
        }
    }

    //opprett sed på en valgt type, feil med eux service
    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til sendDocumentById`() {
        doThrow(RuntimeException("error")).whenever(mockEuxrestTemplate).exchange(
                eq("/type/1234567/sed/3123sfdf23-4324svfsdf324/send"),
                any(),
                eq(null),
                ArgumentMatchers.eq(String::class.java)
        )
        assertThrows<EuxServerException> {
            service.sendDocumentById("123456", "213213-123123-123123")
        }
    }


    @Test
    fun callingEuxServiceListOfRinasaker_Ok() {
        val filepath = "src/test/resources/json/rinasaker/rinasaker_12345678901.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        val orgRinasaker = mapJsonToAny(json, typeRefs<List<Rinasak>>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                eq(String::class.java))
        ).thenReturn(response)

        val result = service.getRinasaker("12345678900", "T8")

        assertEquals(154, orgRinasaker.size)
        assertEquals(orgRinasaker.size, result.size)
        JSONAssert.assertEquals(json, mapAnyToJson(result), true)
    }

    @Test
    fun callingEuxServiceListOfRinasaker_IOError() {
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(ResourceAccessException("I/O error"))
        assertThrows<IOException> {
            service.getRinasaker("12345678900", "T8")
        }
    }

    @Test
    fun callingEuxServiceListOfRinasaker_ClientError() {
        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Error in Token", HttpHeaders(), "Error in Token".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(clientError)
        assertThrows<HttpClientErrorException> {
            service.getRinasaker("12345678900", "T8")
        }
    }

    @Test
    fun callingEuxServiceListOfRinasaker_ServerError() {
        val serverError = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Error in Gate", HttpHeaders(), "Error in Gate".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenThrow(serverError)
        assertThrows<HttpServerErrorException> {
            service.getRinasaker("12345678900", "T8")
        }
    }

    @Test
    fun callingEuxServiceListOfRinasakerNoFnrFoundBUCInQ2_Ok() {
        //Kun for CT
        val filepath = "src/test/resources/json/rinasaker/rinasaker_12345678901.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        val orgRinasaker = mapJsonToAny(json, typeRefs<List<Rinasak>>())

        val responseEmpty: ResponseEntity<String> = ResponseEntity("[]", HttpStatus.OK)
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                eq(String::class.java))
        ).thenReturn(responseEmpty)
                .thenReturn(response)
                .thenReturn(responseEmpty)
                .thenReturn(responseEmpty)
                .thenReturn(responseEmpty)
                .thenReturn(responseEmpty)
                .thenReturn(responseEmpty)
                .thenReturn(responseEmpty)
        val result = service.getRinasaker("12345678900", "Q2")
        assertEquals(154, orgRinasaker.size)
        assertEquals(orgRinasaker.size, result.size)
    }

    @Test
    fun callingEuxServiceListOfRinasakerNoFnrCheckT8() {
        //Kun for CT
        //service.fasitEnvName = "T8"
        val filepath = "src/test/resources/json/rinasaker/rinasaker_12345678901.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val responseEmpty: ResponseEntity<String> = ResponseEntity("[]", HttpStatus.OK)
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                eq(String::class.java))
        ).thenReturn(responseEmpty)
        val result = service.getRinasaker("12345678900", "T8")
        assertEquals(0, result.size)
    }

    @Test
    fun callingEuxServiceFormenuUI_AllOK() {
        val rinasakerjson = "src/test/resources/json/rinasaker/rinasaker_34567890111.json"
        val rinasakStr = String(Files.readAllBytes(Paths.get(rinasakerjson)))
        assertTrue(validateJson(rinasakStr))

        doReturn(ResponseEntity.ok(rinasakStr))
                .whenever(mockEuxrestTemplate).exchange(
                    eq("/rinasaker?fødselsnummer=12345678900&rinasaksnummer=&buctype=&status="),
                    eq(HttpMethod.GET), anyOrNull(), eq(String::class.java) )

        val rinasakresult = service.getRinasaker("12345678900", "T8")

        val orgRinasaker = mapJsonToAny(rinasakStr, typeRefs<List<Rinasak>>())
        println("rinaSaker size: " + orgRinasaker.size)

        val eucCaseIds = rinasakresult.stream().map { it.id!! }.toList()

        val bucjson = "src/test/resources/json/buc/buc-158123_2_v4.1.json"
        val bucStr = String(Files.readAllBytes(Paths.get(bucjson)))
        val buc = mapJsonToAny(bucStr, typeRefs<Buc>())

        assertTrue(validateJson(bucStr))
        doReturn( CompletableFuture.completedFuture( BucAndSedView.from(buc, buc.id!!, "001122334455")))
                .whenever(mockAsyncEuxService)
                .getBucAsync(any<String>(),any<String>())

        val result = service.getBucAndSedView(eucCaseIds, "001122334455", "ffsdfwewerwerwer")

        assertNotNull(result)
        assertEquals(6, orgRinasaker.size)
        assertEquals(6, result.size)

        val firstJson = result.first()
        assertEquals("158123", firstJson.caseId)

        var lastUpdate: Long = 0
        firstJson.lastUpdate?.let { lastUpdate = it }
        assertEquals("2019-05-20T16:35:34",  Instant.ofEpochMilli(lastUpdate).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
        assertEquals(18, firstJson.seds?.size)
        val json = mapAnyToJson(firstJson)

        println(json)

        val bucdetaljerpath = "src/test/resources/json/buc/bucdetaljer-158123.json"
        val bucdetaljer = String(Files.readAllBytes(Paths.get(bucdetaljerpath)))
        assertTrue(validateJson(bucdetaljer))
        JSONAssert.assertEquals(bucdetaljer, json, true)
    }

    @Test
    fun callingEuxServiceForSinglemenuUI_AllOK() {
        val bucjson = "src/test/resources/json/buc/buc-158123_2_v4.1.json"
        val bucStr = String(Files.readAllBytes(Paths.get(bucjson)))
        assertTrue(validateJson(bucStr))

        whenever(mockEuxrestTemplate.exchange(
                not(eq("/rinasaker?Fødselsnummer=12345678900&RINASaksnummer=&BuCType=&Status=")),
                eq(HttpMethod.GET), eq(null), eq(String::class.java)))
                .thenReturn(ResponseEntity.ok(bucStr))

        val firstJson = service.getSingleBucAndSedView("158123")

        assertEquals("158123", firstJson.caseId)
        var lastUpdate: Long = 0
        firstJson.lastUpdate?.let { lastUpdate = it }
        assertEquals("2019-05-20T16:35:34",  Instant.ofEpochMilli(lastUpdate).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
        assertEquals(18, firstJson.seds?.size)
    }

    @Test
    fun callingEuxServiceCreateBuc_Ok() {

        val mockBuc = "12345678909999"
        val response: ResponseEntity<String> = ResponseEntity("12345678909999", HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenReturn(response)

        val result = service.createBuc("P_BUC_01")

        assertEquals(mockBuc, result)
    }

    @Test
    fun callingEuxServiceCreateBuc_IOError() {
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenThrow(ResourceAccessException("I/O error"))

        assertThrows<IOException> {
            service.createBuc("P_BUC_01")
        }
    }

    @Test
    fun callingEuxServiceCreateBuc_ClientError() {
        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Error in Token", HttpHeaders(), "Error in Token".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenThrow(clientError)

        assertThrows<HttpClientErrorException> {
            service.createBuc("P_BUC_01")
        }

    }

    @Test
    fun callingEuxServiceCreateBuc_ServerError() {

        val serverError = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Error in Gate", HttpHeaders(), "Error in Gate".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenThrow(serverError)

        assertThrows<HttpServerErrorException> {
            service.createBuc("P_BUC_01")
        }
    }

    @Test
    fun callingEuxServicePutBucDeltager_WrongParticipantInput() {
        assertThrows<IllegalArgumentException> {
            service.putBucMottakere("126552", listOf(InstitusjonItem("NO", "NAVT", "Dummy")))
        }
    }

    @Test
    fun callingEuxServicePutBucDeltager_ClientError() {

        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Token authorization error", HttpHeaders(),"Token authorization error".toByteArray(),Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(clientError)

        assertThrows<HttpClientErrorException> {
            service.putBucMottakere("126552", listOf(InstitusjonItem("NO", "NO:NAVT007", "NAV")))
        }
    }

    @Test
    fun putBucDeltager_ServerError(){

        val serverError = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY,"Server error",HttpHeaders(),"Server error".toByteArray(),Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(serverError)

        assertThrows<HttpServerErrorException> {
            service.putBucMottakere("122732", listOf(InstitusjonItem("NO", "NO:NAVT02", "NAV")))
        }
    }

    @Test
    fun putBucDeltager_ResourceAccessError() {
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(ResourceAccessException("I/O Error"))

        assertThrows<IOException> {
            service.putBucMottakere("122732", listOf(InstitusjonItem("NO", "NO:NAVT02", "NAV")))
        }
    }


    @Test
    fun callingPutBucDeltager_OK() {

        val theResponse = ResponseEntity.ok().body("")

        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                ArgumentMatchers.eq(String::class.java))
        ).thenReturn(theResponse)

        val result = service.putBucMottakere("122732", listOf(InstitusjonItem("NO","NO:NAVT005","NAV")))
        assertEquals(true, result)

    }

    @Test
    fun hentNorskFnrPaalisteavPin() {
        val list = listOf(
                PinItem(sektor = "03", land = "SE", identifikator = "00987654321", institusjonsnavn = "SE"),
                PinItem(sektor = "02", land = "NO", identifikator = "12345678900", institusjonsnavn = "NAV"),
                PinItem(sektor = "02", land = "DK", identifikator = "05467898321", institusjonsnavn = "DK")
            )

        val result = service.getFnrMedLandkodeNO(list)
        assertEquals("12345678900", result)
    }

    @Test
    fun hentNorskFnrPaalisteavPinListeTom() {
        val result = service.getFnrMedLandkodeNO(listOf())
        assertEquals(null, result)
    }

    @Test
    fun hentNorskFnrPaalisteavPinListeIngenNorske() {
        val list = listOf(
                PinItem(sektor = "03", land = "SE", identifikator = "00987654321", institusjonsnavn = "SE"),
                PinItem(sektor = "02", land = "DK", identifikator = "05467898321", institusjonsnavn = "DK")
        )
        val result = service.getFnrMedLandkodeNO(list)
        assertEquals(null, result)

    }


    @Test
    fun hentYtelseKravtypeTesterPaaP15000Alderpensjon() {
        val filepath = "src/test/resources/json/nav/P15000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                ArgumentMatchers.eq(String::class.java))
        ).thenReturn(response)

        val result = service.hentFnrOgYtelseKravtype("1234567890","100001000010000")
        assertEquals("21712", result.fnr)
        assertEquals("01", result.krav?.type)
        assertEquals("2019-02-01", result.krav?.dato)
    }

    @Test
    fun hentYtelseKravtypeTesterPaaP15000Gjennlevende() {
        val filepath = "src/test/resources/json/nav/P15000Gjennlevende-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                ArgumentMatchers.eq(String::class.java))
        ).thenReturn(response)

        val result = service.hentFnrOgYtelseKravtype("1234567890","100001000010000")
        assertEquals("32712", result.fnr)
        assertEquals("02", result.krav?.type)
        assertEquals("2019-02-01", result.krav?.dato)
    }

    @Test
    fun hentYtelseKravtypeTesterPaaP2100OK() {
        val filepath = "src/test/resources/json/nav/P2100-NAV-unfin.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                ArgumentMatchers.eq(String::class.java))
        ).thenReturn(response)

        val result = service.hentFnrOgYtelseKravtype("1234567890","100001000010000")

        assertEquals("123456789012", result.fnr)
        assertEquals(null, result.krav?.type)
        assertEquals("2015-12-17", result.krav?.dato)
    }

    @Test
    fun hentYtelseKravtypeTesterPaaP15000FeilerVedUgyldigSED() {
        val filepath = "src/test/resources/json/nav/P9000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val orgsed = mapJsonToAny(json, typeRefs<SED>())

        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                ArgumentMatchers.eq(String::class.java))
        ).thenReturn(response)

        assertThrows<SedDokumentIkkeGyldigException> {
            service.hentFnrOgYtelseKravtype("1234567890", "100001000010000")
        }
    }

    @Test
    fun `Calling euxService getAvailableSEDonBuc returns BuC lists`() {

        var buc = "P_BUC_01"
        var expectedResponse = listOf("P2000")
        var generatedResponse = EuxService.getAvailableSedOnBuc (buc)
        assertEquals(generatedResponse, expectedResponse)

        buc = "P_BUC_06"
        expectedResponse = listOf("P5000", "P6000", "P7000", "P10000")
        generatedResponse = EuxService.getAvailableSedOnBuc(buc)
        assertEquals(generatedResponse, expectedResponse)
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on BUC_01` () {
        val euxCaseId = "123456"
        val bucPath = "src/test/resources/json/buc/buc-158123_2_v4.1.json"
        val bucJson = String(Files.readAllBytes(Paths.get(bucPath)))
        assertTrue(validateJson(bucJson))
        val bucResponse: ResponseEntity<String> = ResponseEntity(bucJson, HttpStatus.OK)
        doReturn(bucResponse)
                .whenever(mockEuxrestTemplate)
                .exchange(eq("/buc/$euxCaseId"), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        doReturn(mockSedResponse(getTestJsonFile("P2000-NAV.json")))
                .whenever(mockEuxrestTemplate)
                .exchange(ArgumentMatchers.contains("buc/$euxCaseId/sed/"), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertEquals("1980-01-01", service.getFDatoFromSed(euxCaseId,"P_BUC_01"))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on BUC_06` () {
        val euxCaseId = "123456"
        val bucPath = "src/test/resources/json/buc/buc-175254_noX005_v4.1.json"
        val bucJson = String(Files.readAllBytes(Paths.get(bucPath)))
        assertTrue(validateJson(bucJson))
        val bucResponse: ResponseEntity<String> = ResponseEntity(bucJson, HttpStatus.OK)
        doReturn(bucResponse)
                .whenever(mockEuxrestTemplate)
                .exchange(eq("/buc/$euxCaseId"), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        doReturn(mockSedResponse(getTestJsonFile("P10000-NAV.json")))
                .whenever(mockEuxrestTemplate)
                .exchange(ArgumentMatchers.contains("buc/$euxCaseId/sed/"), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertEquals("1948-06-28", service.getFDatoFromSed(euxCaseId,"P_BUC_06"))
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on P2100 in BUC_02` () {
        val euxCaseId = "123456"
        val bucPath = "src/test/resources/json/buc/buc-239200_buc02_v4.1.json"
        val bucJson = String(Files.readAllBytes(Paths.get(bucPath)))
        assertTrue(validateJson(bucJson))
        val bucResponse: ResponseEntity<String> = ResponseEntity(bucJson, HttpStatus.OK)
        doReturn(bucResponse)
                .whenever(mockEuxrestTemplate)
                .exchange(eq("/buc/$euxCaseId"), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        doReturn(mockSedResponse(getTestJsonFile("P2100-NAV-unfin.json")))
                .whenever(mockEuxrestTemplate)
                .exchange(ArgumentMatchers.contains("buc/$euxCaseId/sed/"), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertEquals("1969-09-11", service.getFDatoFromSed(euxCaseId,"P_BUC_02"))
    }

    @Test
    fun `Calling getFDatoFromSed   returns exception when seddocumentId is not found` () {
        val euxCaseId = "123456"
        val bucPath = "src/test/resources/json/buc/buc-158123_v4.1.json"
        val bucJson = String(Files.readAllBytes(Paths.get(bucPath)))
        assertTrue(validateJson(bucJson))
        val bucResponse: ResponseEntity<String> = ResponseEntity(bucJson, HttpStatus.OK)
        doReturn(bucResponse)
                .whenever(mockEuxrestTemplate)
                .exchange(eq("/buc/$euxCaseId"), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        doReturn(mockSedResponse(getTestJsonFile("P10000-NAV.json")))
                .whenever(mockEuxrestTemplate)
                .exchange(ArgumentMatchers.contains("buc/$euxCaseId/sed/"), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertThrows<NoSuchFieldException> {
            service.getFDatoFromSed(euxCaseId, "P_BUC_03")
        }
    }

    @Test
    fun `Calling getFDatoFromSed   returns exception when foedselsdato is not found` () {
        val euxCaseId = "123456"
        val bucPath = "src/test/resources/json/buc/buc-158123_v4.1.json"
        val bucJson = String(Files.readAllBytes(Paths.get(bucPath)))
        assertTrue(validateJson(bucJson))

        val sed = SED("P2000")
        sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy")))

        val bucResponse: ResponseEntity<String> = ResponseEntity(bucJson, HttpStatus.OK)
        doReturn(bucResponse)
                .whenever(mockEuxrestTemplate)
                .exchange(eq("/buc/$euxCaseId"), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        doReturn(mockSedResponse(sed.toJson()))
                .whenever(mockEuxrestTemplate)
                .exchange(ArgumentMatchers.contains("buc/$euxCaseId/sed/"), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertThrows<IkkeFunnetException> {
            service.getFDatoFromSed(euxCaseId, "P_BUC_01")
        }
    }

    @Test
    fun `Calling getFDatoFromSed   returns Exception when unsupported buctype is entered` () {
        val euxCaseId = "123456"
        val bucType = "P_BUC_07"
        assertThrows<GenericUnprocessableEntity> {
            service.getFDatoFromSed(euxCaseId, bucType)
        }
    }

    fun mockSedResponse(sedJson: String): ResponseEntity<String> {
        return ResponseEntity(sedJson, HttpStatus.OK)
    }

    fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/json/nav/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }

    @Test
    fun gittEnGyldigListeAvInstitusjonerNaarHttpUrlGenereresSaaGenererEnListeAvMottakereSomPathParam() {
        val euxCaseId = "1234"
        val correlationId = 123456778
        val deltaker = listOf(InstitusjonItem("NO","NO:NAV02","NAV"), InstitusjonItem("SE", "SE:SE2", "SVER"))
        val builder = UriComponentsBuilder.fromPath("/buc/$euxCaseId/mottakere")
                .queryParam("KorrelasjonsId", correlationId)
                .build()
        val url = builder.toUriString() + service.convertListInstitusjonItemToString(deltaker)
        println(url)
        assertEquals("/buc/1234/mottakere?KorrelasjonsId=123456778&mottakere=NO:NAV02&mottakere=SE:SE2", url)
    }
}
