package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.fagmodul.eux.EuxKlient
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.PinOgKrav
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentIkkeOpprettetException
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ActionsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.MangelfulleInndataException
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSED
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

@ExtendWith(MockitoExtension::class)
class SedControllerTest {

    @Spy
    lateinit var mockEuxService: EuxService

    @Spy
    lateinit var mockEuxKlient: EuxKlient

    @Spy
    lateinit var auditLogger: AuditLogger

    @Mock
    lateinit var mockAktoerIdHelper: AktoerregisterService

    @Mock
    lateinit var mockPrefillSED: PrefillSED

    private lateinit var prefillDataMock: PrefillDataModel
    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        prefillDataMock = PrefillDataModel()
        this.sedController = SedController(mockEuxService,
                mockEuxKlient,
                PrefillService(mockPrefillSED),
                mockAktoerIdHelper,
                auditLogger)
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
        whenever(mockAktoerIdHelper.hentPinForAktoer(any<String>())).thenReturn("12345")

        val utfyllMock = ApiRequest.buildPrefillDataModelConfirm(mockData, mockAktoerIdHelper.hentPinForAktoer(mockData.aktoerId), null)

        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))
        whenever(mockPrefillSED.prefill(any())).thenReturn(utfyllMock)

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
    fun `Test liste med SED kun PensjonSED skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H021","X06","P9000", "")

        val result = sedController.sortAndFilterSeds(list)

        assertEquals(4, result.size)
        assertEquals("[H021, P2000, P4000, P9000]", result.toString())
    }

    @Test
    fun `Test liste med SED som skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H020","H070", "X06", "H121","P9000", "XYZ")

        val result = sedController.sortAndFilterSeds(list)

        assertEquals(6, result.size)
        assertEquals("[H020, H070, H121, P2000, P4000, P9000]", result.toString())
    }


    @Test
    fun `Test av liste med SEDer der kun PensjonSEDer skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H020","X06","P9000", "")

        val result = sedController.sortAndFilterSeds(list)

        assertEquals(4, result.size)
        assertEquals("[ \"H020\", \"P2000\", \"P4000\", \"P9000\" ]", mapAnyToJson(result))
    }

    @Test
    fun `Calling euxController getSeds on BUC01 returns SEDs for a given BUC`() {
        val buc = "P_BUC_01"
        val rinanr = null

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P2000")))
        val generatedResponse = sedController.getSeds(buc, rinanr)
        assertEquals(expectedResponse, generatedResponse)
    }

    @Test
    fun `Calling euxController getSeds on BUC02 returns SEDs for a given BUC`() {
        val buc = "P_BUC_02"
        val rinanr = null

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P2100")))
        val generatedResponse = sedController.getSeds(buc, rinanr)
        assertEquals(expectedResponse, generatedResponse)
    }

    @Test
    fun `Calling euxController getSeds on BUC03 returns SEDs for a given BUC`() {
        val buc = "P_BUC_03"
        val rinanr = null

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P2200")))
        val generatedResponse = sedController.getSeds(buc, rinanr)
        assertEquals(expectedResponse, generatedResponse)
    }


    @Test
    fun `Calling euxController getSeds on BUC05 returns SEDs for a given BUC`() {
        val buc = "P_BUC_05"
        val rinanr = null

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P8000")))
        val generatedResponse = sedController.getSeds(buc, rinanr)
        assertEquals(expectedResponse, generatedResponse)
    }

    @Test
    fun `Calling euxController getSeds on BUC06 returns SEDs for a given BUC`() {
        val buc = "P_BUC_06"
        val rinanr = null

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P5000", "P6000", "P7000", "P10000")))
        val generatedResponse = sedController.getSeds(buc, rinanr)
        assertEquals(expectedResponse, generatedResponse)
    }

    @Test
    fun `Calling euxController getSeds on BUC10 returns SEDs for a given BUC`() {
        val buc = "P_BUC_10"
        val rinanr = null

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P15000")))
        val generatedResponse = sedController.getSeds(buc, rinanr)
        assertEquals(expectedResponse, generatedResponse)
    }

    @Test
    fun `Calling euxController getSeds on blank returns all SEDs`() {
        val buc = null
        val rinanr = null

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P2000", "P2100", "P2200", "P8000", "P5000", "P6000", "P7000", "P10000", "P14000", "P15000")))
        val generatedResponse = sedController.getSeds(buc, rinanr)
        assertEquals(expectedResponse, generatedResponse)
    }

    @Test
    fun euxController_getSeds_returnsSEDOnsgivenBUC() {
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        val mockBuc = Mockito.mock(Buc::class.java)

        val mockCreateSedType = listOf(
                ActionsItem(name = "Create", documentType = "P6000"),
                ActionsItem(name = "Create", documentType = "X6000"),
                ActionsItem(name = "Create", documentType = "X005"),
                ActionsItem(name = "Create", documentType = "P2200"),
                ActionsItem(name = "Create", documentType = "P3000_SE"),
                ActionsItem(name = "Create", documentType = "P3000_AT")
        )
        doReturn(mockCreateSedType).whenever(mockBuc).actions

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val expectedSedList = ResponseEntity.ok().body(mapAnyToJson( listOf("P2200", "P3000_AT", "P3000_SE", "P6000")))

        val generatedResponse = sedController.getSeds(buc, rinanr)

        assertEquals(expectedSedList, generatedResponse)

        val json = generatedResponse.body!!
        val validSedListforBuc = mapJsonToAny(json, typeRefs<List<String>>())
        assertEquals(4, validSedListforBuc.size)
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

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any<String>())

        val mockBuc = Mockito.mock(Buc::class.java)

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(null).whenever(mockBuc).participants

        val currentX005 = DocumentsItem(type = "X005")

        doReturn(listOf(currentX005)).whenever(mockBuc).documents

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), mockAktoerIdHelper.hentPinForAktoer(apiRequestWith(euxCaseId).aktoerId), null)

        doReturn(dummyPrefillData).whenever(mockPrefillSED).prefill(any())

        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxService).opprettSedOnBuc(any(),eq(euxCaseId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxService, times(newParticipants.size + 1)).opprettSedOnBuc(any(), eq(euxCaseId))
        verify(mockEuxKlient, never()).addDeltagerInstitutions(any(), any())
    }

    @Test
    fun `call addInstutionAndDocument  ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1234567890"

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any<String>())

        val mockBuc = Mockito.mock(Buc::class.java)
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(listOf(ParticipantsItem())).whenever(mockBuc).participants

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), mockAktoerIdHelper.hentPinForAktoer(apiRequestWith(euxCaseId).aktoerId), null)
        doReturn(dummyPrefillData).whenever(mockPrefillSED).prefill(any())

        doReturn(BucSedResponse(euxCaseId, "1")).whenever(mockEuxService).opprettSedOnBuc(any(),eq(euxCaseId))

        val noNewParticipants = listOf<InstitusjonItem>()
        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, noNewParticipants))

        verify(mockEuxService, times(noNewParticipants.size + 1)).opprettSedOnBuc(any(), eq(euxCaseId))
        verify(mockEuxKlient, never()).addDeltagerInstitutions(any(), any())
    }

    @Test
    fun `call addInstutionAndDocument  to nye deltakere, men ingen X005`() {
        val euxCaseId = "1234567890"

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any<String>())

        val mockBuc = Mockito.mock(Buc::class.java)

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(listOf<ParticipantsItem>()).whenever(mockBuc).participants

        doReturn(listOf<DocumentsItem>()).whenever(mockBuc).documents

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), mockAktoerIdHelper.hentPinForAktoer(apiRequestWith(euxCaseId).aktoerId), null)

        doReturn(dummyPrefillData).whenever(mockPrefillSED).prefill(any())

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
        doReturn(dummyPrefillData).whenever(mockPrefillSED).prefill(any())

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
