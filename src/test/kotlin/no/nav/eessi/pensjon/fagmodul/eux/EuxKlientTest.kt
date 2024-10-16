package no.nav.eessi.pensjon.fagmodul.eux


import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.*
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.P2000
import no.nav.eessi.pensjon.eux.model.SedType.P2200
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxTestUtils.Companion.dummyRequirement
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.hamcrest.core.StringContains.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.retry.annotation.EnableRetry
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException


private const val P_BUC_99 = "P_BUC_99"
private const val NAVT02 = "NO:NAVT02"

@SpringJUnitConfig(classes = [
    TestEuxClientRetryConfig::class,
    EuxKlientRetryLogger::class,
    EuxKlientAsSystemUser::class,
    EuxKlientTest.Config::class]
)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@EnableRetry
class EuxKlientTest {

    @Autowired
    lateinit var euxRestTemplate: RestTemplate

    @Autowired
    lateinit var euxSystemRestTemplate: RestTemplate

    lateinit var server: MockRestServiceServer

    lateinit var euxKlient: EuxKlientAsSystemUser

    @BeforeEach
    fun setup() {
        server = MockRestServiceServer.bindTo(euxRestTemplate).build()
        euxKlient = EuxKlientAsSystemUser(euxRestTemplate, euxSystemRestTemplate, 100L)
    }

    @TestConfiguration
    class Config {
        @Bean
        fun euxRestTemplate(): RestTemplate {
            return RestTemplateBuilder()
                .errorHandler(EuxErrorHandler())
                .additionalInterceptors(IOExceptionRetryInterceptor())
                .build()
        }
        @Bean
        fun euxSystemRestTemplate(): RestTemplate = mockk()
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
    fun `Calling EuxService forventer korrekt svar tilbake fra et kall til hentbuc`() {
        val mockBuck = mockBucNoRecevieDate().toJson()
        server.expect(requestTo(containsString("/buc"))).andRespond( withSuccess(mockBuck, MediaType.APPLICATION_JSON))

        val result = euxKlient.getBucJsonAsNavIdent("22909" )
        assertEquals(mockBuck, result)
    }

    @Test
    fun `Calling EuxService feiler med BAD_REQUEST fra kall til getBuc`() {
        val bucid = "123213123"
        server.expect(requestTo(containsString("/buc/$bucid"))).andRespond( withStatus(HttpStatus.BAD_REQUEST))


        assertThrows<GenericUnprocessableEntity> {
            euxKlient.getBucJsonAsNavIdent(bucid )
        }
    }

    @Test
    fun `Calling EuxService feiler med NOT FOUND fra kall til getBuc`() {
        val bucid = "123213123"
        server.expect(requestTo(containsString("/buc/$bucid"))).andRespond( withStatus(HttpStatus.NOT_FOUND))

        assertThrows<ResponseStatusException> {
            euxKlient.getBucJsonAsNavIdent(bucid )
        }
    }

    @Test
    fun `Calling EuxService feiler med en UNAUTHORIZED Exception fra kall til hentbuc`() {
        server.expect(requestTo(containsString("/buc/P_BUC_99"))).andRespond( withStatus(HttpStatus.UNAUTHORIZED))

        val exception = assertThrows<RinaIkkeAutorisertBrukerException> {
            euxKlient.getBucJsonAsNavIdent(P_BUC_99 )
        }
        assertEquals("Authorization token required for Rina.", exception.reason)
    }

    @Test
    fun `Calling EuxService feiler med en FORBIDDEN Exception fra kall til hentbuc`() {
        server.expect(requestTo(containsString("/buc/P_BUC_99"))).andRespond( withStatus(HttpStatus.FORBIDDEN))

        val exception = assertThrows<ForbiddenException> {
            euxKlient.getBucJsonAsNavIdent(P_BUC_99 )
        }
        assertEquals("Forbidden, Ikke tilgang", exception.reason)
    }

    @Test
    fun `Calling EuxService feiler med en NOT FOUND Exception fra kall til hentbuc`() {
        server.expect(requestTo(containsString("/buc/P_BUC_99"))).andRespond( withStatus(HttpStatus.NOT_FOUND))

        val exception = assertThrows<IkkeFunnetException> {
            euxKlient.getBucJsonAsNavIdent(P_BUC_99 )
        }
        assertEquals("Ikke funnet", exception.reason)
    }

    @Test
    fun `Calling EuxService feiler med en UNPROCESSABLE ENTITY Exception fra kall til hentbuc`() {
        server.expect(requestTo(containsString("/buc/P_BUC_99"))).andRespond( withStatus(HttpStatus.UNPROCESSABLE_ENTITY))

        val exception = assertThrows<GenericUnprocessableEntity> {
            euxKlient.getBucJsonAsNavIdent(P_BUC_99 )
        }
        assertEquals("En feil har oppstått", exception.reason)
    }

    @Test
    fun `Calling EuxService kaster en GATEWAY_TIMEOUT Exception ved kall til hentbuc`() {
        server.expect(requestTo(containsString("/buc/P_BUC_99"))).andRespond( withStatus(HttpStatus.GATEWAY_TIMEOUT))

        val exception = assertThrows<GatewayTimeoutException> {
            euxKlient.getBucJsonAsNavIdent(P_BUC_99 )
        }
        assertEquals("Venting på respons fra Rina resulterte i en timeout", exception.reason)
    }

    @Test
    fun `Euxservice kaster en IO_EXCEPTION ved kall til getBuc`() {
        server.expect(requestTo(containsString("/buc/P_BUC_99"))).andRespond(withStatus(HttpStatus.I_AM_A_TEAPOT))

        val exception = assertThrows<GenericUnprocessableEntity> {
            euxKlient.getBucJsonAsNavIdent(P_BUC_99 )
        }
        assertEquals("En feil har oppstått", exception.reason)
    }

    @Test
    fun `getBuc mock response HttpStatus NOT_FOUND excpecting IkkeFunnetException`() {
        server.expect(requestTo(containsString("/buc/"))).andRespond(withStatus(HttpStatus.NOT_FOUND))

        val exception = assertThrows<IkkeFunnetException> {
            euxKlient.getBucJsonAsNavIdent(P_BUC_99 )
        }
        assertEquals("Ikke funnet", exception.reason)
    }

    @Test
    fun testMapsParams() {
        val uriParams1 = mapOf("RinaSakId" to "121312", "DokuemntId" to null).filter { it.value != null }
        assertEquals(1, uriParams1.size)
        val uriParams2 = mapOf("RinaSakId" to "121312", "DokuemntId" to "98d6879827594d1db425dbdfef399ea8")
        assertEquals(2, uriParams2.size)
    }

    @Test
    fun callingEuxServiceListOfRinasaker_ClientError() {
        server.expect(requestTo(containsString("/rinasaker"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = assertThrows<RinaIkkeAutorisertBrukerException> {
            euxKlient.getRinasaker("12345678900", null)
        }
        assertEquals("Authorization token required for Rina.", exception.reason)
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

        server.expect(requestTo(containsString("/rinasaker"))).andRespond(withSuccess(datajson, MediaType.APPLICATION_JSON))


        val data = euxKlient.getRinasaker(euxCaseId = "123123")

        assertEquals("9002480", data.first().traits?.caseId)
        assertEquals(P_BUC_03.name, data.first().traits?.flowType)

    }


    @Test
    fun callingEuxServiceListOfRinasaker_ServerError() {
        server.expect(requestTo(containsString("/rinasaker"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        assertThrows<GenericUnprocessableEntity> {
            euxKlient.getRinasaker("12345678900", null)
        }
    }

    @Test
    fun callingEuxServiceCreateBuc_Ok() {
        val mockBuc = "12345678909999"
        server.expect(requestTo(containsString("/buc?BuCType=P_BUC_01&KorrelasjonsId="))).andRespond(withSuccess("12345678909999", MediaType.APPLICATION_JSON))

        val result = euxKlient.createBuc(P_BUC_01.name)
        assertEquals(mockBuc, result)
    }

    @Test
    fun callingEuxServiceCreateBuc_ClientError() {
        server.expect(requestTo(containsString("/buc"))).andRespond( withStatus(HttpStatus.UNAUTHORIZED))

        assertThrows<RinaIkkeAutorisertBrukerException> {
            euxKlient.createBuc(P_BUC_01.name)
        }

    }

    @Test
    fun callingEuxServiceCreateBuc_ServerError() {
        server.expect(requestTo(containsString("/buc?BuCType=P_BUC_01"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThrows<EuxRinaServerException> {
            euxKlient.createBuc(P_BUC_01.name)
        }
    }

    @Test
    fun callingEuxServicePutBucDeltager_WrongParticipantInput() {
        assertThrows<IllegalArgumentException> {
            euxKlient.putBucMottakere("126552", (listOf("NAVT")))
        }
    }

    @Test
    fun `call putBucMottakere feiler med UNAUTHORIZED forventer RinaIkkeAutorisertBrukerException`() {
        val euxCaseId = "126552"
        server.expect(requestTo(containsString("/buc/$euxCaseId/mottakere"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        assertThrows<RinaIkkeAutorisertBrukerException> {
            euxKlient.putBucMottakere(euxCaseId, listOf("NO:NAVT07"))
        }
    }

    @Test
    fun `call putBucMottaker feiler ved INTERNAL_SERVER_ERROR forventer UgyldigCaseIdException`() {
        server.expect(requestTo(containsString("/buc/122732/mottakere"))).andRespond( withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThrows<EuxRinaServerException> {
            euxKlient.putBucMottakere("122732", listOf(NAVT02))
        }
    }

    @Test
    fun putBucDeltager_RuntimeExceptionError() {
        server.expect(requestTo(containsString("/buc"))).andRespond { throw ResourceAccessException("Error") }

        assertThrows<RuntimeException> {
            euxKlient.putBucMottakere("122732", listOf(NAVT02))
        }
    }

    @Test
    fun callingPutBucDeltager_OK() {
        server.expect(requestTo(containsString("/buc/122732/mottakere"))).andRespond(withSuccess())

        val result = euxKlient.putBucMottakere("122732", listOf("NO:NAVT05"))
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
        val url = builder.toUriString() + euxKlient.convertListInstitusjonItemToString(deltaker)
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
        server.expect(ExpectedCount.times(2), requestTo(containsString("/institusjoner?BuCType=P_BUC_01"))).andRespond(withSuccess(instiutionsMegaJson, MediaType.APPLICATION_JSON))

        val expected = 248
        val actual = euxKlient.getInstitutions(P_BUC_01.name)

        assertEquals(expected, actual.size)

        val actual2 = euxKlient.getInstitutions(P_BUC_01.name)
        assertEquals(expected, actual2.size)

    }

    @Test
    fun `tester om institusjon er gyldig i en P_BUC_03`() {
        val instiutionsMegaJson = javaClass.getResource("/json/institusjoner/deltakere_p_buc_01_all.json").readText()
        server.expect(ExpectedCount.times(1), requestTo(containsString("/institusjoner?BuCType=P_BUC_03"))).andRespond(withSuccess(instiutionsMegaJson, MediaType.APPLICATION_JSON))

        val actual = euxKlient.getInstitutions(P_BUC_03.name)
        assertEquals(248, actual.size)
    }

    @Test
    fun `Calling EuxKlient forsøker 3 ganger før den feiler med kontakt fra eux med kall til getSedOnBucByDocumentId`() {
        server.expect(ExpectedCount.times(3), requestTo(containsString("/buc/12345678900/sed/"))).andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
        )
        assertThrows<GenericUnprocessableEntity> {
            euxKlient.getSedOnBucByDocumentIdNotAsSystemUser("12345678900", P_BUC_99)
        }
    }

    @Test
    fun `Calling EuxKlient forsøker 3 ganger før den feiler med motta navsed fra eux med kall til getSedOnBucByDocumentId`() {
        val euxCaseId = "12345678900"
        server.expect(ExpectedCount.times(3),requestTo(containsString("/buc/$euxCaseId/sed/"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThrows<RinaIkkeAutorisertBrukerException> {
            euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(euxCaseId, P_BUC_99)
        }
    }

    @Test
    fun `EuxKlient forventer korrekt svar tilbake fra et kall til opprettSedOnBuc`() {
        val euxCaseId = "123456"

        this.server.expect(requestTo(containsString("/buc/123456/sed?ventePaAksjon=false"))).andRespond(
            withSuccess("323413415dfvsdfgq343145sdfsdfg34135", MediaType.APPLICATION_JSON))

        val result = euxKlient.opprettSed( SED(P2000).toJsonSkipEmpty(), euxCaseId)

        assertEquals(euxCaseId, result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    @Test
    fun `Calling EuxService  feiler med svar tilbake fra et kall til opprettSedOnBuc`() {
        server.expect(requestTo(containsString("/buc/1231233/sed?ventePaAksjon=false"))).andRespond(withStatus(HttpStatus.BAD_REQUEST))
        assertThrows<GenericUnprocessableEntity> {
            euxKlient.opprettSed(SED(P2200).toJsonSkipEmpty(), "1231233")
        }
    }

    @Test
    fun `Calling EuxService  feiler med kontakt fra eux med kall til opprettSedOnBuc forventer GatewayTimeoutException`() {
        this.server.expect(requestTo(containsString("/buc/213123/sed?ventePaAksjon=false"))).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT))
        assertThrows<GatewayTimeoutException> {
            euxKlient.opprettSed(SED(P2000).toJsonSkipEmpty(), "213123")
        }
    }

    @Test
    fun `gitt en mock rest-template, så forventes en korrekt formatert response fra opprettSvarSed`() {
        val euxCaseId = "123456"
        val parentDocumentId = "11111"
        server.expect(requestTo("/buc/$euxCaseId/sed/$parentDocumentId/svar")).andRespond(withSuccess("323413415dfvsdfgq343145sdfsdfg34135", MediaType.APPLICATION_JSON))
        val result = euxKlient.opprettSvarSed(SED(P2000).toJsonSkipEmpty(), euxCaseId, parentDocumentId)

        assertEquals(euxCaseId, result.caseId)
        assertEquals("323413415dfvsdfgq343145sdfsdfg34135", result.documentId)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc, ved feil skal det prøves noen ganger også returneres en liste over sedid`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json")!!.readText()

        val euxCaseId = "279029"
        server.expect(requestTo("/buc/$euxCaseId")).andRespond { throw IOException("This did not work 1") }
        server.expect(requestTo("/buc/$euxCaseId")).andRespond { throw IOException("This did not work $it") }
        server.expect(requestTo("/buc/$euxCaseId")).andRespond(withSuccess(gyldigBuc, MediaType.APPLICATION_JSON))

        val actual = euxKlient.getBucJsonAsNavIdent(euxCaseId)
        assertEquals(euxCaseId, mapJsonToAny<Buc>(actual!!).id)
    }

    @Test
    fun `gitt at eux kaster forbidden skal det avsluttes kall med en gang`() {
        val euxCaseId = "123456"
        server.expect(requestTo("/buc/$euxCaseId")).andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertThrows<ForbiddenException> {
            euxKlient.getBucJsonAsNavIdent(euxCaseId)
        }
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid skal det returneres en liste av Buc deltakere`() {
        val mockEuxRinaid = "123456"

        val mockResponse = listOf(
            Participant(organisation = Organisation(countryCode = "DK", id = "DK006")),
            Participant(organisation = Organisation(countryCode = "PL", id = "PolishAcc"))
        )
        server.expect(requestTo("/buc/$mockEuxRinaid/bucdeltakere")).andRespond(withSuccess(mockResponse.toJson(), MediaType.APPLICATION_JSON))

        val deltakere = euxKlient.getBucDeltakere(mockEuxRinaid)
        assertEquals(2, deltakere.size)
    }

    @Test
    fun callingEuxServiceListOfRinasaker_IOError() {
        repeat(3){
            server.expect(requestTo(containsString("/rinasaker"))).andRespond { throw IOException("take $it") }
        }

        assertThrows<ResourceAccessException> {
            euxKlient.getRinasaker("12345678900", null)
        }
        server.verify()
    }

    @Test
    fun `gitt et kall til getRinaSaker som kaster en NOT_FOUND og ignoreres`() {
        repeat(3){
            server.expect(requestTo(containsString("/rinasaker"))).andRespond { throw HttpClientErrorException(HttpStatus.NOT_FOUND, "DUMT") }
        }

        assertThrows<HttpClientErrorException> {
            euxKlient.getRinasaker("12345678900", null)
            server.verify()
        }
    }

    fun mockBucNoRecevieDate(direction: String = "OUT") : Buc {
        return Buc(
            id = "1",
            processDefinitionName = P_BUC_01.name,
            documents = listOf(
                DocumentsItem(
                    id = "1",
                    direction = direction,
                    receiveDate = 1567178490000,
                    lastUpdate = 1567178490000,
                    type = SedType.P8000
                )
            )
        )
    }
}
