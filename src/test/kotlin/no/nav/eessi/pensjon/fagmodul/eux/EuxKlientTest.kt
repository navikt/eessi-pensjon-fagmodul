package no.nav.eessi.pensjon.fagmodul.eux

import com.nhaarman.mockitokotlin2.*
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.security.sts.typeRef
import no.nav.eessi.pensjon.utils.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.*
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.String


@ExtendWith(MockitoExtension::class)
class EuxKlientTest {

    private lateinit var klient: EuxKlient

    @Mock
    private lateinit var mockEuxrestTemplate: RestTemplate


    @BeforeEach
    fun setup() {
        mockEuxrestTemplate.errorHandler = DefaultResponseErrorHandler()
        mockEuxrestTemplate.interceptors = listOf( RequestResponseLoggerInterceptor() )
        klient = EuxKlient(mockEuxrestTemplate, overrideWaitTimes = 0L)
        klient.initMetrics()
    }

    @AfterEach
    fun takedown() {
        Mockito.reset(mockEuxrestTemplate)
    }

    @Test
    fun `Opprett Uri component path`() {
        val path = "/type/{RinaSakId}/sed"
        val uriParams = mapOf("RinaSakId" to "12345")
        val builder = UriComponentsBuilder.fromUriString(path)
                .queryParam("KorrelasjonsId", "c0b0c068-4f79-48fe-a640-b9a23bf7c920")
                .buildAndExpand(uriParams)
        val str = builder.toUriString()
        assertEquals("/type/12345/sed?KorrelasjonsId=c0b0c068-4f79-48fe-a640-b9a23bf7c920", str)
    }

    @Test
    fun `Calling EuxService  forventer korrekt svar tilbake fra et kall til hentbuc`() {
        val filepath = "src/test/resources/json/buc/buc-22909_v4.1.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        val response: ResponseEntity<String> = ResponseEntity(json, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(response)
        val result = klient.getBucJson("P_BUC_99")
        assertEquals(json, result)
    }

    private fun createDummyServerRestExecption(httpstatus: HttpStatus, dummyBody: String)
            = HttpServerErrorException.create (httpstatus, httpstatus.name, HttpHeaders(), dummyBody.toByteArray(), Charset.defaultCharset())

    private fun createDummyClientRestExecption(httpstatus: HttpStatus, dummyBody: String)
            = HttpClientErrorException.create (httpstatus, httpstatus.name, HttpHeaders(), dummyBody.toByteArray(), Charset.defaultCharset())

    @Test
    fun `Calling EuxService feiler med BAD_REQUEST fra kall til getBuc`() {
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<GenericUnprocessableEntity> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxService feiler med en UNAUTHORIZED Exception fra kall til hentbuc`() {
        doThrow(HttpClientErrorException(HttpStatus.UNAUTHORIZED))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxService feiler med en FORBIDDEN Exception fra kall til hentbuc`() {
        doThrow(createDummyClientRestExecption(HttpStatus.FORBIDDEN, "Gateway body dummy timeout"))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<ForbiddenException> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxService feiler med en NOT FOUND Exception fra kall til hentbuc`() {
        doThrow(createDummyClientRestExecption(HttpStatus.NOT_FOUND, "Gateway body dummy timeout"))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<IkkeFunnetException> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxService feiler med en UNPROCESSABLE ENTITY Exception fra kall til hentbuc`() {
        doThrow(createDummyClientRestExecption(HttpStatus.UNPROCESSABLE_ENTITY, "unprocesable dummy timeout"))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<GenericUnprocessableEntity> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxService kaster en GATEWAY_TIMEOUT Exception ved kall til hentbuc`() {
        doThrow(createDummyServerRestExecption(HttpStatus.GATEWAY_TIMEOUT, "Gateway body dummy timeout"))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<GatewayTimeoutException> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun `Euxservice kaster en IO_EXCEPTION ved kall til getBuc`() {
        doThrow(RuntimeException(HttpStatus.I_AM_A_TEAPOT.name))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
                assertThrows<ServerException> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun `getBuc mock response HttpStatus NOT_FOUND excpecting IkkeFunnetException`() {
        doThrow(createDummyClientRestExecption(HttpStatus.NOT_FOUND,"Dummy body for Not Found exception"))
                .whenever(mockEuxrestTemplate).exchange( any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<IkkeFunnetException> {
            klient.getBucJson("P_BUC_99")
        }
    }

    @Test
    fun testMapsParams() {
        val uriParams1 = mapOf("RinaSakId" to "121312", "DokuemntId" to null).filter { it.value != null }
        assertEquals(1, uriParams1.size)
        val uriParams2 = mapOf("RinaSakId" to "121312", "DokuemntId" to "98d6879827594d1db425dbdfef399ea8")
        assertEquals(2, uriParams2.size)
    }

    @Test
    fun callingEuxServiceListOfRinasaker_IOError() {
        doThrow(createDummyServerRestExecption(HttpStatus.INTERNAL_SERVER_ERROR,"Serverfeil, I/O-feil"))
                .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<EuxRinaServerException> {
            klient.getRinasaker("12345678900", null, null, null)
        }
    }

    @Test
    fun callingEuxServiceListOfRinasaker_ClientError() {
        doThrow(createDummyClientRestExecption(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED"))
                .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.getRinasaker("12345678900", null, null, null)
        }
    }

    @Test
    fun callingEuxServiceListOfRinasaker_ServerError() {

        doThrow(createDummyServerRestExecption(HttpStatus.BAD_GATEWAY, "Dummybody"))
                .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))
        assertThrows<GenericUnprocessableEntity> {
            klient.getRinasaker("12345678900", null, null, null)
        }
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

        val result = klient.createBuc("P_BUC_01")

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

        assertThrows<ServerException> {
            klient.createBuc("P_BUC_01")
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

        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.createBuc("P_BUC_01")
        }

    }

    @Test
    fun callingEuxServiceCreateBuc_ServerError() {

        val serverError = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Error in Gate", HttpHeaders(), "Error in Gate".toByteArray(), Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.POST),
                eq(null),
                eq(String::class.java))
        ).thenThrow(serverError)

        assertThrows<EuxRinaServerException> {
            klient.createBuc("P_BUC_01")
        }
    }

    @Test
    fun callingEuxServicePutBucDeltager_WrongParticipantInput() {
        assertThrows<IllegalArgumentException> {
            klient.putBucMottakere("126552", (listOf("NAVT")))
        }
    }

    @Test
    fun `call putBucMottakere feiler med UNAUTHORIZED forventer RinaIkkeAutorisertBrukerException`() {
        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Token authorization error", HttpHeaders(),"Token authorization error".toByteArray(),Charset.defaultCharset())
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(clientError)

        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.putBucMottakere("126552", listOf("NO:NAVT07"))
        }
    }

    @Test
    fun `call putBucMottaker feiler ved INTERNAL_SERVER_ERROR forventer UgyldigCaseIdException`() {
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(createDummyServerRestExecption(HttpStatus.INTERNAL_SERVER_ERROR,"Dummy Internal Server Error body"))

        assertThrows<EuxRinaServerException> {
            klient.putBucMottakere("122732", listOf("NO:NAVT02"))
        }
    }


    @Test
    fun putBucDeltager_ResourceAccessError() {
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(ResourceAccessException("Other unknown Error"))

        assertThrows<ServerException> {
            klient.putBucMottakere("122732", listOf("NO:NAVT02"))
        }
    }

    @Test
    fun putBucDeltager_RuntimeExceptionError() {
        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.PUT),
                eq(null),
                eq(String::class.java))
        ).thenThrow(RuntimeException("Error"))

        assertThrows<RuntimeException> {
            klient.putBucMottakere("122732", listOf("NO:NAVT02"))
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

        val result = klient.putBucMottakere("122732", listOf("NO:NAVT05"))
        assertEquals(true, result)
    }

    @Test
    fun `gitt en gyldig liste av Institusjoner naar http url genereres saa generer en liste av mottakere som path param`() {
        val euxCaseId = "1234"
        val correlationId = 123456778
        val deltaker = listOf("NO:NAV02", "SE:SE2")
        val builder = UriComponentsBuilder.fromPath("/buc/$euxCaseId/mottakere")
                .queryParam("KorrelasjonsId", correlationId)
                .build()
        val url = builder.toUriString() + klient.convertListInstitusjonItemToString(deltaker)
        assertEquals("/buc/1234/mottakere?KorrelasjonsId=123456778&mottakere=NO:NAV02&mottakere=SE:SE2", url)
    }

    @Test
    fun `Tester og evaluerer om require statement blir oppfylt`() {
        assertThrows<IllegalArgumentException> { dummyRequirement(null, null) }
        assertTrue( dummyRequirement("grtg", null))
        assertTrue( dummyRequirement(null, "hhgi"))
        assertTrue( dummyRequirement("kufghj", "fjhgb"))
    }

    @Test
    fun testHentInstitutionsGyldigDatasetFraEuxVilReturenereEnListeAvInstitution() {
        val instiutionsMegaJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/institusjoner/deltakere_p_buc_01_all.json")))
        val response: ResponseEntity<String> = ResponseEntity(instiutionsMegaJson, HttpStatus.OK)

        whenever(mockEuxrestTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                ArgumentMatchers.eq(String::class.java))
        ).thenReturn(response)

        val expected = 248
        val actual = klient.getInstitutions("P_BUC_01")

        assertEquals(expected, actual.size)

    }

    @Test
    fun `Calling EuxKlient  feiler med kontakt fra eux med kall til getSedOnBucByDocumentId`() {
        doThrow(createDummyServerRestExecption(HttpStatus.BAD_GATEWAY, "Dummybody"))
                .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertThrows<GenericUnprocessableEntity> {
            klient.getSedOnBucByDocumentIdAsJson("12345678900", "P_BUC_99")
        }
    }

    @Test
    fun `Calling EuxKlient  feiler med motta navsed fra eux med kall til getSedOnBucByDocumentId`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.UNAUTHORIZED)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))).thenReturn(errorresponse)
        assertThrows<SedDokumentIkkeLestException> {
            klient.getSedOnBucByDocumentIdAsJson("12345678900", "P_BUC_99")
        }
    }

    @Test
    fun `EuxKlient forventer korrekt svar tilbake fra et kall til opprettSedOnBuc`() {
        val response: ResponseEntity<String> = ResponseEntity("323413415dfvsdfgq343145sdfsdfg34135", HttpStatus.OK)
        whenever(mockEuxrestTemplate.exchange(any<String>(), eq(HttpMethod.POST), any(), eq(String::class.java))).thenReturn(response)

        val result = klient.opprettSed("/buc/{RinaSakId}/sed",
                SED("P2000").toJsonSkipEmpty(),
                "123456",
                MetricsHelper(SimpleMeterRegistry()).init("dummy"),
                "Feil ved opprettSed",
                null)

        assertEquals("123456", result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til opprettSedOnBuc`() {
        doThrow(createDummyClientRestExecption(HttpStatus.BAD_REQUEST, "Dummy clent error"))
                .whenever(mockEuxrestTemplate).exchange(
                        any<String>(),
                        eq(HttpMethod.POST),
                        any(),
                        eq(String::class.java)
                )

        assertThrows<GenericUnprocessableEntity> {
            klient.opprettSed("/buc/{RinaSakId}/sed",
                    SED("P2200").toJsonSkipEmpty(),
                    "1231233",
                    MetricsHelper(SimpleMeterRegistry()).init("dummy"),
                    "Feil ved opprettSed",
                    null)
        }
    }

    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til opprettSedOnBuc forventer GatewayTimeoutException`() {
        doThrow(createDummyServerRestExecption(HttpStatus.GATEWAY_TIMEOUT,"Dummy body"))
                .whenever(mockEuxrestTemplate).exchange(
                        any<String>(),
                        eq(HttpMethod.POST),
                        any(),
                        eq(String::class.java)
                )
        assertThrows<GatewayTimeoutException> {
            klient.opprettSed("/buc/{RinaSakId}/sed",
                    SED("P2000").toJsonSkipEmpty(),
                    "213123",
                    MetricsHelper(SimpleMeterRegistry()).init("dummy"),
                    "Feil ved opprettSed",
                    null)
        }
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc, ved feil skal det prøves noen ganger også returneres en liste over sedid`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))

        val mockEuxRinaid = "123456"
        val mockResponse = ResponseEntity.ok().body(gyldigBuc)

        doThrow(HttpClientErrorException(HttpStatus.UNAUTHORIZED, "This did not work 1"))
                .doThrow(HttpClientErrorException(HttpStatus.UNAUTHORIZED, "This did not work 2"))
                .doReturn(mockResponse)
                .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        val actual = klient.getBucJson(mockEuxRinaid)

        assertNotNull(actual)
    }


    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc, ved feil skal det prøves noen ganger så exception til slutt`() {
        val mockEuxRinaid = "123456"

        doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY,"This did not work 1"))
                .doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY,"This did not work 2"))
                .doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY,"This did not work 3"))
                .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertThrows<GenericUnprocessableEntity> {
            klient.getBucJson(mockEuxRinaid)
        }
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid skal det returneres en liste av Buc deltakere`() {
        val mockEuxRinaid = "123456"
        val mockResponse = ResponseEntity.ok().body(listOf(
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc"))
        ))
        doReturn(mockResponse).whenever(mockEuxrestTemplate).exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                eq(typeRef<List<ParticipantsItem>>()))

        val deltakere = klient.getBucDeltakere(mockEuxRinaid)
        assertEquals(2, deltakere.size)
    }

    private fun dummyRequirement(dummyparam1: String?, dummyparam2: String?): Boolean{
        require(!(dummyparam1 == null && dummyparam2 == null)) { "Minst et søkekriterie må fylles ut for å få et resultat fra Rinasaker" }
        return true
    }
}
