package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.*
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
import no.nav.eessi.pensjon.helper.AktoerIdHelper
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

    @Mock
    lateinit var mockAktoerIdHelper: AktoerIdHelper

    @Mock
    lateinit var mockPrefillSED: PrefillSED

    private lateinit var prefillDataMock: PrefillDataModel
    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        prefillDataMock = PrefillDataModel()
        this.sedController = SedController(mockEuxService, PrefillService(mockPrefillSED), mockAktoerIdHelper)
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
    fun `calling createDocument   forventer BucSedResponse (euxCaseid, documentid) ved mockedResponse`() {
        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        val mockResponse = BucSedResponse("1234567890", "123123123-123123123-123131")

        val requestMock = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                euxCaseId = mockResponse.caseId,
                vedtakId = "1234567",
                institutions = items,
                sed = "P6000",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )

        //må være først
        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any<String>())

        val utfyllMock = ApiRequest.buildPrefillDataModelOnNew(requestMock, mockAktoerIdHelper.hentPinForAktoer(requestMock.aktoerId))

        assertNotNull(utfyllMock.personNr)
        assertEquals("12345", utfyllMock.personNr)

        //mock prefillSED
        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))
        doReturn(utfyllMock).whenever(mockPrefillSED).prefill(any())

        //mock opprett type og sed til RINA
        doReturn(mockResponse).whenever(mockEuxService).opprettBucSed(
                any(),
                any(),
                any(),
                any()
        )

        val response = sedController.createDocument(requestMock)

        assertEquals("1234567890", response.caseId)
        assertEquals("123123123-123123123-123131", response.documentId)
    }

    @Test
    fun `forventer alt ok ved å legge til en ny SED på en ekisternede buc `() {
        val bucresponse = BucSedResponse("123444455", "2a427c10325c4b5eaf3c27ba5e8f1877")

        val items = listOf(InstitusjonItem(country = "NO", institution = "NO:DUMMY"))
        val requestMock = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                euxCaseId = "1234567890",
                vedtakId = "1234567",
                institutions = items,
                sed = "P6000",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )

        doReturn("12345").whenever(mockAktoerIdHelper).hentPinForAktoer(any<String>())
        val utfyllMock = ApiRequest.buildPrefillDataModelOnExisting(requestMock, mockAktoerIdHelper.hentPinForAktoer(requestMock.aktoerId), null)

        assertNotNull(utfyllMock.personNr)
        assertEquals("12345", utfyllMock.personNr)

        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))

        doReturn(utfyllMock).whenever(mockPrefillSED).prefill(any())
        doReturn(bucresponse).whenever(mockEuxService).opprettSedOnBuc(any(), any())

        val mockbuc = Mockito.mock(Buc::class.java)

        doReturn(listOf(DocumentsItem(id = "2a427c10325c4b5eaf3c27ba5e8f1877"))).whenever(mockbuc).documents

        val euxCaseId: String = "123444455"
        doReturn(mockbuc).whenever(mockEuxService).getBuc(euxCaseId)

        val response = sedController.addDocument(requestMock)
        assertEquals("2a427c10325c4b5eaf3c27ba5e8f1877", response.id)
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

        val response = sedController.confirmDocument(mockData)
        //val response = SED.fromJson(response2)

        assertNotNull(response)
        assertEquals("P6000", response.sed)
        assertEquals("Dummy", response.nav?.bruker?.person?.fornavn)
        assertEquals("Dummy", response.nav?.bruker?.person?.etternavn)
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
        val list  = listOf("X005","P2000","P4000","H02","X06","P9000", "")

        val result = list.filterPensionSedAndSort()

        assertEquals(3, result.size)
        assertEquals("[P2000, P4000, P9000]", result.toString())
    }

    @Test
    fun `Test liste med SED som skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H02","H070", "X06", "H121","P9000", "")

        val result = list.filterPensionSedAndSort()

        println(result.toString())

        assertEquals(5, result.size)
        assertEquals("[H070, H121, P2000, P4000, P9000]", result.toString())
    }


    @Test
    fun `Test av liste med SEDer der kun PensjonSEDer skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H02","X06","P9000", "")

        val result = list.filterPensionSedAndSort()

        assertEquals(3, result.size)
        assertEquals("[ \"P2000\", \"P4000\", \"P9000\" ]", mapAnyToJson(result))
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

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P5000", "P6000", "P7000", "P8000", "P9000")))
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

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf("P2000", "P2100", "P2200", "P5000", "P6000", "P7000", "P8000", "P9000", "P10000", "P14000", "P15000")))
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
                ActionsItem(name = "Create", documentType = "P3000_NO")
        )
        doReturn(mockCreateSedType).whenever(mockBuc).actions

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val expectedSedList = ResponseEntity.ok().body(mapAnyToJson( listOf("P2200", "P6000")))

        val generatedResponse = sedController.getSeds(buc, rinanr)

        assertEquals(expectedSedList, generatedResponse)

        val json = generatedResponse.body!!
        val validSedListforBuc = mapJsonToAny(json, typeRefs<List<String>>())
        assertEquals(2, validSedListforBuc.size)
    }

    @Test
    fun getYtelseKravtypeOk() {
        val mockKrav = PinOgKrav(fnr = "13212312", krav = Krav(dato = "2019-02-01", type = "01"))

        doReturn(mockKrav).whenever(mockEuxService).
                hentFnrOgYtelseKravtype(
                        any<String>(),
                        any<String>()
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
        verify(mockEuxService, never()).addDeltagerInstitutions(any(), any())
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
        verify(mockEuxService, never()).addDeltagerInstitutions(any(), any())
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

        doReturn(true).whenever(mockEuxService).putBucMottakere(eq(euxCaseId), any())

        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxService).opprettSedOnBuc(any(), eq(euxCaseId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxService).putBucMottakere(euxCaseId, newParticipants)
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
        doReturn(true).whenever(mockEuxService).putBucMottakere(eq(euxCaseId), any())

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


    @Test
    fun GittAtfDatoBlirFunnetForEnGittBucMedRinanrSaaReturnererViFdatoMedMeldingOgStatuskodeOK() {

        val buctype = "P_BUC_01"
        val euxCaseId = "123456"

        val sedPath = "src/test/resources/json/nav/P2000_NAV_SED_v4_1.json"
        val sedJson = String(Files.readAllBytes(Paths.get(sedPath)))

        val bucPath = "src/test/resources/json/buc/buc-158123_2_v4.1.json"
        val bucJson = String(Files.readAllBytes(Paths.get(bucPath)))
        val buc = mapJsonToAny(bucJson, typeRefs<Buc>())
        val sed = mapJsonToAny(sedJson, typeRefs<SED>())

        doReturn(buc).whenever(mockEuxService).getBuc(any<String>())
        doReturn(sed).whenever(mockEuxService).getSedOnBucByDocumentId(any<String>(), any<String>())

        var response = sedController.getFodselsdato(euxCaseId,buctype)

        assertEquals(response, "1948-06-28")
    }

    @Test
    fun GittAtfDatoBlirFunnetForEnGittBucMedRinanrSaaReturnererViFdatoMedMeldingOgStatuskodeFeil() {

        val buctype = "P_BUC_01"
        val euxCaseId = "123456"

        val bucPath = "src/test/resources/json/buc/buc-239200_buc02_v4.1.json"
        val bucJson = String(Files.readAllBytes(Paths.get(bucPath)))
        val buc = mapJsonToAny(bucJson, typeRefs<Buc>())

        doReturn(buc).whenever(mockEuxService).getBuc(any())

        assertThrows<NoSuchFieldException> {
            sedController.getFodselsdato(euxCaseId, buctype)
        }
    }

/*    @Test
    fun gittAtfDatoIKKEBlirFunnetForEnGittBucMedRinanrSaaReturnererViFdatoMedMeldingOgStatuskodeOK() {

        val rinanr = "12345"
        val buctype = null
        val sedPath = "src/test/resources/json/nav/P2000_NAV_SED_v4_1.json"

        val sedJson = String(Files.readAllBytes(Paths.get(sedPath)))

        val map = jacksonObjectMapper()
        val sed = map.readValue(sedJson, SED::class.java)

        assertTrue(validateJson(sedJson))


        doReturn(null).whenever(mockEuxService).getFDatoFromSed(rinanr, buctype)

        var responseEntity = sedController.getFodselsdato(rinanr,buctype)


        assertEquals(responseEntity.statusCode, HttpStatus.OK)
        assertEquals(responseEntity.body, "1989-01-28")

    }*/
}
