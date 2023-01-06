package no.nav.eessi.pensjon.fagmodul.eux


import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType.P2000
import no.nav.eessi.pensjon.eux.model.SedType.P2200
import no.nav.eessi.pensjon.eux.model.buc.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.buc.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxTestUtils.Companion.dummyRequirement
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.String

private const val P_BUC_99 = "P_BUC_99"
private const val NAVT02 = "NO:NAVT02"

class EuxKlientTest {

    lateinit var klient: EuxKlient

    @MockK
    lateinit var mockEuxrestTemplate: RestTemplate
    @MockK
    lateinit var euxUsernameOidcRestTemplate: RestTemplate

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        mockEuxrestTemplate.errorHandler = DefaultResponseErrorHandler()
        mockEuxrestTemplate.interceptors = listOf( RequestResponseLoggerInterceptor() )
        klient = EuxKlient(mockEuxrestTemplate, euxUsernameOidcRestTemplate, overrideWaitTimes = 0L)
        klient.initMetrics()
    }
    @AfterEach
    fun takedown() {
        clearAllMocks()
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

        every { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null, String::class.java) } returns response

        val result = klient.getBucJsonAsNavIdent(P_BUC_99)
        assertEquals(json, result)
    }

    private fun createDummyServerRestExecption(httpstatus: HttpStatus, dummyBody: String)
            = HttpServerErrorException.create (httpstatus, httpstatus.name, HttpHeaders(), dummyBody.toByteArray(), Charset.defaultCharset())

    private fun createDummyClientRestExecption(httpstatus: HttpStatus, dummyBody: String)
            = HttpClientErrorException.create (httpstatus, httpstatus.name, HttpHeaders(), dummyBody.toByteArray(), Charset.defaultCharset())

    @Test
    fun `Calling EuxService feiler med BAD_REQUEST fra kall til getBuc`() {
        val bucid = "123213123"

        every {mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws HttpClientErrorException(HttpStatus.BAD_REQUEST)
        assertThrows<GenericUnprocessableEntity> {
            klient.getBucJsonAsNavIdent(bucid)
        }
        verify(exactly = 3){ mockEuxrestTemplate.exchange("/buc/$bucid", HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `Calling EuxService feiler med NOT FOUND fra kall til getBuc`() {
        every { mockEuxrestTemplate.exchange( any<String>(), eq(HttpMethod.GET), null, eq(String::class.java)) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)
        val bucid = "123213123"
        assertThrows<ResponseStatusException> {
            klient.getBucJsonAsNavIdent(bucid)
        }
        verify(exactly = 3){ mockEuxrestTemplate.exchange("/buc/$bucid", HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `Calling EuxService feiler med en UNAUTHORIZED Exception fra kall til hentbuc`() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED)

        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.getBucJsonAsNavIdent(P_BUC_99)
        }
    }

    @Test
    fun `Calling EuxService feiler med en FORBIDDEN Exception fra kall til hentbuc`() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyClientRestExecption(HttpStatus.FORBIDDEN, "Gateway body dummy timeout")
        assertThrows<ForbiddenException> {
            klient.getBucJsonAsNavIdent(P_BUC_99)
        }
    }

    @Test
    fun `Calling EuxService feiler med en NOT FOUND Exception fra kall til hentbuc`() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyClientRestExecption(HttpStatus.NOT_FOUND, "Gateway body dummy timeout")

        assertThrows<IkkeFunnetException> {
            klient.getBucJsonAsNavIdent(P_BUC_99)
        }
    }

    @Test
    fun `Calling EuxService feiler med en UNPROCESSABLE ENTITY Exception fra kall til hentbuc`() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyClientRestExecption(HttpStatus.UNPROCESSABLE_ENTITY, "unprocesable dummy timeout")

        assertThrows<GenericUnprocessableEntity> {
            klient.getBucJsonAsNavIdent(P_BUC_99)
        }
    }

    @Test
    fun `Calling EuxService kaster en GATEWAY_TIMEOUT Exception ved kall til hentbuc`() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyServerRestExecption(HttpStatus.GATEWAY_TIMEOUT, "Gateway body dummy timeout")
        assertThrows<GatewayTimeoutException> {
            klient.getBucJsonAsNavIdent(P_BUC_99)
        }
    }

    @Test
    fun `Euxservice kaster en IO_EXCEPTION ved kall til getBuc`() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws RuntimeException(HttpStatus.I_AM_A_TEAPOT.name)

        assertThrows<ServerException> {
            klient.getBucJsonAsNavIdent(P_BUC_99)
        }
    }

    @Test
    fun `getBuc mock response HttpStatus NOT_FOUND excpecting IkkeFunnetException`() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyClientRestExecption(HttpStatus.NOT_FOUND,"Dummy body for Not Found exception")

        assertThrows<IkkeFunnetException> {
            klient.getBucJsonAsNavIdent(P_BUC_99)
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
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyServerRestExecption(HttpStatus.INTERNAL_SERVER_ERROR,"Serverfeil, I/O-feil")

        assertThrows<EuxRinaServerException> {
            klient.getRinasaker("12345678900", null)
        }
    }

    @Test
    fun callingEuxServiceListOfRinasaker_ClientError() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyClientRestExecption(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED")

        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.getRinasaker("12345678900", null)
        }
    }

    @Test
    fun checkCallRinaSakerErOk() {
        val datajson = """
            [
              {
                "id": "9002480",
                "traits": {
                  "birthday": "1973-05-12",
                  "localPin": "120573",
                  "surname": "xxx",
                  "caseId": "9002480",
                  "name": "xxx",
                  "flowType": "P_BUC_03",
                  "status": "open"
                },
                "properties": {
                  "importance": "1",
                  "criticality": "1"
                },
                "processDefinitionId": "P_BUC_03",
                "applicationRoleId": "PO",
                "status": "open"
              }
            ]
        """.trimIndent()

        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(datajson)

        val data = klient.getRinasaker(euxCaseId = "123123")

        assertEquals("9002480", data.first().traits?.caseId)
        assertEquals(P_BUC_03.name, data.first().traits?.flowType)

    }


    @Test
    fun callingEuxServiceListOfRinasaker_ServerError() {
        every { mockEuxrestTemplate.exchange( any<String>(), HttpMethod.GET, null, String::class.java) } throws createDummyServerRestExecption(HttpStatus.BAD_GATEWAY, "Dummybody")

        assertThrows<GenericUnprocessableEntity> {
            klient.getRinasaker("12345678900", null)
        }
    }

    @Test
    fun callingEuxServiceCreateBuc_Ok() {
        val mockBuc = "12345678909999"
        val response: ResponseEntity<String> = ResponseEntity("12345678909999", HttpStatus.OK)

        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.POST,null, String::class.java)} returns response

        val result = klient.createBuc(P_BUC_01.name)
        assertEquals(mockBuc, result)
    }

    @Test
    fun callingEuxServiceCreateBuc_IOError() {
        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.POST,null, String::class.java)} throws  ResourceAccessException("I/O error")

        assertThrows<ServerException> {
            klient.createBuc(P_BUC_01.name)
        }
    }

    @Test
    fun callingEuxServiceCreateBuc_ClientError() {
        val clientError = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Error in Token", HttpHeaders(), "Error in Token".toByteArray(), Charset.defaultCharset())
        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.POST,null, String::class.java)} throws  clientError

        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.createBuc(P_BUC_01.name)
        }

    }

    @Test
    fun callingEuxServiceCreateBuc_ServerError() {

        val serverError = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Error in Gate", HttpHeaders(), "Error in Gate".toByteArray(), Charset.defaultCharset())

        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.POST,null, String::class.java)} throws  serverError

        assertThrows<EuxRinaServerException> {
            klient.createBuc(P_BUC_01.name)
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
        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.PUT,null, String::class.java)} throws  clientError

        assertThrows<RinaIkkeAutorisertBrukerException> {
            klient.putBucMottakere("126552", listOf("NO:NAVT07"))
        }
    }

    @Test
    fun `call putBucMottaker feiler ved INTERNAL_SERVER_ERROR forventer UgyldigCaseIdException`() {
        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.PUT,null, String::class.java)} throws createDummyServerRestExecption(HttpStatus.INTERNAL_SERVER_ERROR,"Dummy Internal Server Error body")

        assertThrows<EuxRinaServerException> {
            klient.putBucMottakere("122732", listOf(NAVT02))
        }
    }

    @Test
    fun putBucDeltager_ResourceAccessError() {
        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.PUT,null, String::class.java)} throws ResourceAccessException("Other unknown Error")

        assertThrows<ServerException> {
            klient.putBucMottakere("122732", listOf(NAVT02))
        }
    }

    @Test
    fun putBucDeltager_RuntimeExceptionError() {
        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.PUT,null, String::class.java)} throws ResourceAccessException("Error")

        assertThrows<RuntimeException> {
            klient.putBucMottakere("122732", listOf(NAVT02))
        }
    }

    @Test
    fun callingPutBucDeltager_OK() {

        val theResponse = ResponseEntity.ok().body("")
        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.PUT,null, String::class.java)} returns theResponse

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
        val instiutionsMegaJson = javaClass.getResource("/json/institusjoner/deltakere_p_buc_01_all.json").readText()
        val response: ResponseEntity<String> = ResponseEntity(instiutionsMegaJson, HttpStatus.OK)

        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.GET,null, String::class.java)} returns response

        val expected = 239
        val actual = klient.getInstitutions(P_BUC_01.name)

        assertEquals(expected, actual.size)

        val actual2 = klient.getInstitutions(P_BUC_01.name)
        assertEquals(expected, actual2.size)

    }

    @Test
    fun `tester om institusjon er gyldig i en P_BUC_03`() {
        val instiutionsMegaJson = javaClass.getResource("/json/institusjoner/deltakere_p_buc_01_all.json").readText()
        val response: ResponseEntity<String> = ResponseEntity(instiutionsMegaJson, HttpStatus.OK)

        every {  mockEuxrestTemplate.exchange(any<String>(),HttpMethod.GET,null, String::class.java)} returns response

        val actual = klient.getInstitutions(P_BUC_03.name)
        assertEquals(215, actual.size)

        val result = actual.filter { it.institution == "PL:PL390050ER" }.map { it }
        assertEquals(0, result.size)

   }

    @Test
    fun `Calling EuxKlient  feiler med kontakt fra eux med kall til getSedOnBucByDocumentId`() {
        every { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null,String::class.java) } throws createDummyServerRestExecption(HttpStatus.BAD_GATEWAY, "Dummybody")


        assertThrows<GenericUnprocessableEntity> {
            klient.getSedOnBucByDocumentIdAsJson("12345678900", P_BUC_99)
        }
    }

    @Test
    fun `Calling EuxKlient  feiler med motta navsed fra eux med kall til getSedOnBucByDocumentId`() {
        val errorresponse = ResponseEntity<String?>(HttpStatus.UNAUTHORIZED)

        every { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null, String::class.java) } returns errorresponse
        assertThrows<SedDokumentIkkeLestException> {
            klient.getSedOnBucByDocumentIdAsJson("12345678900", P_BUC_99)
        }
    }

    @Test
    fun `EuxKlient forventer korrekt svar tilbake fra et kall til opprettSedOnBuc`() {
        val response: ResponseEntity<String> = ResponseEntity("323413415dfvsdfgq343145sdfsdfg34135", HttpStatus.OK)

        every { mockEuxrestTemplate.postForEntity("/buc/123456/sed?ventePaAksjon=false", any(),String::class.java) } returns response
        val result = klient.opprettSed(
                SED(P2000).toJsonSkipEmpty(),
                "123456",
                MetricsHelper.ForTest().init("dummy"),
                "Feil ved opprettSed")

        assertEquals("123456", result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til opprettSedOnBuc`() {
        every { mockEuxrestTemplate.postForEntity( eq("/buc/1231233/sed?ventePaAksjon=false"), any(), String::class.java) } throws createDummyClientRestExecption(HttpStatus.BAD_REQUEST, "Dummy clent error")
        assertThrows<GenericUnprocessableEntity> {
            klient.opprettSed(
                SED(P2200).toJsonSkipEmpty(),
                "1231233",
                MetricsHelper.ForTest().init("dummy"),
                "Feil ved opprettSed"
            )
        }
    }

    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til opprettSedOnBuc forventer GatewayTimeoutException`() {
        every { mockEuxrestTemplate.postForEntity( eq("/buc/213123/sed?ventePaAksjon=false"), any(), String::class.java) } throws createDummyServerRestExecption(HttpStatus.GATEWAY_TIMEOUT, "Dummy body")
        assertThrows<GatewayTimeoutException> {
            klient.opprettSed(
                SED(P2000).toJsonSkipEmpty(),
                "213123",
                MetricsHelper.ForTest().init("dummy"),
                "Feil ved opprettSed"
            )
        }
    }

    @Test
    fun `gitt en mock rest-template, så forventes en korrekt formatert response fra opprettSvarSed`() {
        val response: ResponseEntity<String> = ResponseEntity("323413415dfvsdfgq343145sdfsdfg34135", HttpStatus.OK)
        every { mockEuxrestTemplate.postForEntity(any<String>(), any(), String::class.java) } returns response
        val result = klient.opprettSvarSed(
            SED(P2000).toJsonSkipEmpty(),
            "123456",
            "11111",
            "Feil ved opprettSed",
            MetricsHelper.ForTest().init("dummy")
        )

        assertEquals("123456", result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc, ved feil skal det prøves noen ganger også returneres en liste over sedid`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()

        val mockEuxRinaid = "123456"
        val mockResponse = ResponseEntity.ok().body(gyldigBuc)

        every { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null, String::class.java) } throws
                HttpClientErrorException(HttpStatus.UNAUTHORIZED, "This did not work 1") andThenThrows
                HttpClientErrorException(HttpStatus.UNAUTHORIZED, "This did not work 2") andThen
                mockResponse
        val actual = klient.getBucJsonAsNavIdent(mockEuxRinaid)

        assertNotNull(actual)
    }

    @Test
    fun `gitt at eux kaster forbidden skal det avsluttes kall med en gang`() {
        every { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null, String::class.java) } throws
                HttpClientErrorException(HttpStatus.FORBIDDEN, "This did not work only once")

        assertThrows<ForbiddenException> {
            klient.getBucJsonAsNavIdent("123456")
        }
         verify(exactly = 1) { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null, String::class.java)  }
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc, ved feil skal det prøves noen ganger så exception til slutt`() {
        val mockEuxRinaid = "123456"

        every { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null, String::class.java) } throws
                HttpClientErrorException(HttpStatus.BAD_GATEWAY, "This did not work 1") andThenThrows
                HttpClientErrorException(HttpStatus.BAD_GATEWAY, "This did not work 2") andThenThrows
                HttpClientErrorException(HttpStatus.BAD_GATEWAY, "This did not work 3")
        assertThrows<GenericUnprocessableEntity> {
            klient.getBucJsonAsNavIdent(mockEuxRinaid)
        }
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid skal det returneres en liste av Buc deltakere`() {
        val mockEuxRinaid = "123456"

        val mockResponse = listOf(
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc"))
        )

        every { mockEuxrestTemplate.exchange(any<String>(), HttpMethod.GET, null, String::class.java) } returns ResponseEntity.ok().body(mockResponse.toJson())

        val deltakere = klient.getBucDeltakere(mockEuxRinaid)
        assertEquals(2, deltakere.size)
    }
}
