package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.PinOgKrav
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentIkkeOpprettetException
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.MangelfulleInndataException
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class SedControllerTest {

    @Spy
    lateinit var mockEuxService: EuxService

    @Spy
    lateinit var auditLogger: AuditLogger

    @Mock
    lateinit var mockAktoerIdHelper: AktoerregisterService

    @Mock
    lateinit var mockPrefillSEDService: PrefillSEDService

    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        val prefillService = PrefillService(mockPrefillSEDService)
        prefillService.initMetrics()
        this.sedController = SedController(mockEuxService,
                prefillService,
                mockAktoerIdHelper,
                auditLogger)
        sedController.initMetrics()
    }

    @Test
    fun `create frontend request`() {
        val json = "{\"institutions\":[{\"country\":\"NO\",\"institution\":\"DUMMY\"}],\"buc\":\"P_BUC_06\",\"sed\":\"P6000\",\"sakId\":\"123456\",\"aktoerId\":\"0105094340092\"}"

        //map json request back to FrontendRequest obj
        val map = jacksonObjectMapper()
        val req = map.readValue(json, ApiRequest::class.java)


        assertEquals("P_BUC_06", req.buc)
        assertEquals("DUMMY", req.institutions!![0].institution)
        assertEquals("123456", req?.sakId)
    }

    @Test
    fun `confirm document`() {
        val mockData = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                vedtakId = "1234567",
                institutions = listOf(InstitusjonItem("NO", "DUMMY")),
                euxCaseId = "1234567890",
                sed = "P6000",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )
        whenever(mockAktoerIdHelper.hentPinForAktoer(any())).thenReturn("12345")

        val utfyllMock = ApiRequest.buildPrefillDataModelConfirm(mockData, mockAktoerIdHelper.hentPinForAktoer(mockData.aktoerId), null)

        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))

        whenever(mockPrefillSEDService.prefill(any())).thenReturn(utfyllMock.sed)

        val response = sedController.confirmDocument(mockData, "noFilter")
        assertNotNull(response)

        val sed = SED.fromJson(response)

        assertEquals("P6000", sed.sed)
        assertEquals("Dummy", sed.nav?.bruker?.person?.fornavn)
        assertEquals("Dummy", sed.nav?.bruker?.person?.etternavn)
    }


    @Test
    fun `check rest api path correct`() {
        val path = "/sed/get/{rinanr}/{documentid}"
        val uriParams = mapOf("rinanr" to "123456789", "documentid" to "DOC1223213234234")
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        assertEquals("/sed/get/123456789/DOC1223213234234", builder.path)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_01 returns 1 sed`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/P_BUC_01_4.2_tom.json"))), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("P2000")))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(1, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 4 seds`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc_P_BUC_06_4.2_tom.json"))), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("P5000", "P6000", "P7000", "P10000")))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(4, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 3 seds if a sed already exists`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-P_BUC_06_4.2_P5000.json"))), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("P10000", "P6000", "P7000")))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(3, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_01 returns lots of seds`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-22909_v4.1.json"))), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("H020", "H070", "H120", "P10000", "P3000_NO", "P4000", "P5000", "P6000", "P7000", "P8000" )))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(10, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_06 returns 0 seds if a sed is sent`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-P_BUC_06-P5000_Sendt.json"))), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf<String>()))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(0, list.size)
    }

     @Test
    fun getYtelseKravtypeOk() {
        val mockKrav = PinOgKrav(fnr = "13212312", krav = Krav(dato = "2019-02-01", type = "01"))

        doReturn(mockKrav).whenever(mockEuxService).
                hentFnrOgYtelseKravtype(
                        any(),
                        any()
                )

        val mockResult =  sedController.getPinOgYtelseKravtype("12123", "3123123")

        assertEquals("13212312", mockResult.fnr)
        assertEquals("01", mockResult.krav?.type)
        assertEquals("2019-02-01", mockResult.krav?.dato)
    }

    @Test
    fun `call addInstutionAndDocument  mock adding two institusjon when X005 exists already`() {
        val euxCaseId = "1234567890"

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any())

        val mockBuc = Mockito.mock(Buc::class.java)

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(null).whenever(mockBuc).participants

        val currentX005 = DocumentsItem(type = "X005")

        doReturn(listOf(currentX005)).whenever(mockBuc).documents

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), mockAktoerIdHelper.hentPinForAktoer(apiRequestWith(euxCaseId).aktoerId), null)

        whenever(mockPrefillSEDService.prefill(any())).thenReturn(dummyPrefillData.sed)

        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxService).opprettSedOnBuc(any(),eq(euxCaseId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )


        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxService, times(newParticipants.size + 1)).opprettSedOnBuc(any(), eq(euxCaseId))
    }

    @Test
    fun `call addInstutionAndDocument  ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1234567890"

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any())

        val mockBuc = Mockito.mock(Buc::class.java)
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(listOf(ParticipantsItem())).whenever(mockBuc).participants

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), mockAktoerIdHelper.hentPinForAktoer(apiRequestWith(euxCaseId).aktoerId), null)

        whenever(mockPrefillSEDService.prefill(any())).thenReturn(dummyPrefillData.sed)

        doReturn(BucSedResponse(euxCaseId, "1")).whenever(mockEuxService).opprettSedOnBuc(any(),eq(euxCaseId))

        val noNewParticipants = listOf<InstitusjonItem>()
        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, noNewParticipants))

        verify(mockEuxService, times(noNewParticipants.size + 1)).opprettSedOnBuc(any(), eq(euxCaseId))
    }

    @Test
    fun `call addInstutionAndDocument  to nye deltakere, men ingen X005`() {
        val euxCaseId = "1234567890"

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any())

        val mockBuc = Mockito.mock(Buc::class.java)

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(listOf<ParticipantsItem>()).whenever(mockBuc).participants

        doReturn(listOf<DocumentsItem>()).whenever(mockBuc).documents

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), mockAktoerIdHelper.hentPinForAktoer(apiRequestWith(euxCaseId).aktoerId), null)

        whenever(mockPrefillSEDService.prefill(any())).thenReturn(dummyPrefillData.sed)

        doNothing().whenever(mockEuxService).addInstitution(any(), any())

        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxService).opprettSedOnBuc(any(), eq(euxCaseId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxService, times(1)).addInstitution(any(), any())
        verify(mockEuxService, times(1)).opprettSedOnBuc(any(), eq(euxCaseId))
    }

    @Test
    fun `call addInstutionAndDocument  Exception eller feiler ved oppretting av SED naar X005 ikke finnes`() {
        val euxCaseId = "1234567890"

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any<String>())

        val mockBuc = Mockito.mock(Buc::class.java)
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(listOf<ParticipantsItem>()).whenever(mockBuc).participants

        val currentX005 = DocumentsItem()

        doReturn(listOf(currentX005)).whenever(mockBuc).documents

        doNothing().whenever(mockEuxService).addInstitution(any(), any())

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), mockAktoerIdHelper.hentPinForAktoer(apiRequestWith(euxCaseId).aktoerId), null)

        whenever(mockPrefillSEDService.prefill(any())).thenReturn(dummyPrefillData.sed)

        doThrow(SedDokumentIkkeOpprettetException("Expected!")).whenever(mockEuxService).opprettSedOnBuc(any(), eq(euxCaseId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        assertThrows<SedDokumentIkkeOpprettetException> {
            sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))
        }
    }


    @Test
    fun `call getAvdodAktoerId  expect valid aktoerId when avdodfnr excist and sed is P2100`() {
        val apireq = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                sed = "P2100",
                buc = "P_BUC_02",
                aktoerId = "0105094340092",
                avdodfnr = "12345566"

        )
        doReturn("1122334455").whenever(mockAktoerIdHelper).hentAktoerForPin (any<String>())

        val result = sedController.getAvdodAktoerId(request = apireq)
        assertEquals("1122334455", result)
    }

    @Test
    fun `call getAvdodAktoerId  expect error when avdodfnr is missing and sed is P2100`() {
        val apireq = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                sed = "P2100",
                buc = "P_BUC_02",
                aktoerId = "0105094340092"
        )
        assertThrows<MangelfulleInndataException> {
            sedController.getAvdodAktoerId(request = apireq)
        }
    }

    @Test
    fun `call getAvdodAktoerId  expect null value when sed is P2100`() {
        val apireq = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                sed = "P2000",
                buc = "P_BUC_01",
                aktoerId = "0105094340092",
                avdodfnr = "12345566"
        )
        val result = sedController.getAvdodAktoerId(request = apireq)
        assertEquals(null, result)
    }


    private fun apiRequestWith(euxCaseId: String, institutions: List<InstitusjonItem> = listOf()): ApiRequest {
        return ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                euxCaseId = euxCaseId,
                vedtakId = "1234567",
                institutions = institutions,
                sed = "P6000",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )
    }
}
