package no.nav.eessi.eessifagmodul.controllers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillSED
import no.nav.eessi.eessifagmodul.services.PrefillService
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(MockitoJUnitRunner.Silent::class)
class SedControllerTest {

    @Mock
    lateinit var mockEuxService: EuxService

    @Mock
    lateinit var mockAktoerregisterService: AktoerregisterService

    @Mock
    lateinit var mockPrefillService: PrefillService

    @Mock
    lateinit var mockPrefillSED: PrefillSED

    private lateinit var prefillDataMock: PrefillDataModel
    private lateinit var sedController: SedController

    @Before
    fun setUp() {
        prefillDataMock = PrefillDataModel()
        mockPrefillService = PrefillService(mockEuxService, mockPrefillSED)
        this.sedController = SedController(mockEuxService, mockPrefillService, mockAktoerregisterService)
    }

    @Test
    fun `create frontend request`() {
        val json = "{\"institutions\":[{\"country\":\"NO\",\"institution\":\"DUMMY\"}],\"buc\":\"P_BUC_06\",\"sed\":\"P6000\",\"sakId\":\"123456\",\"aktoerId\":\"0105094340092\"}"
        //map json request back to FrontendRequest obj
        val map = jacksonObjectMapper()
        val req = map.readValue(json, SedController.ApiRequest::class.java)
        assertEquals("P_BUC_06", req.buc)
        assertEquals("DUMMY", req.institutions!![0].institution)
        assertEquals("123456", req?.sakId)
    }

    @Test
    fun `calling createDocument | forventer BucSedResponse (euxCaseid, documentid) ved mockedResponse`() {
        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        val mockResponse = BucSedResponse("1234567890", "123123123-123123123-123131")

        val requestMock = SedController.ApiRequest(
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
        doReturn("12345").whenever(mockAktoerregisterService).hentGjeldendeNorskIdentForAktorId(
                ArgumentMatchers.anyString())

        val utfyllMock = sedController.buildPrefillDataModelOnNew(requestMock)

        assertNotNull(utfyllMock.personNr)
        assertEquals("12345", utfyllMock.personNr)

        //mock prefillSED
        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))
        doReturn(utfyllMock).whenever(mockPrefillSED).prefill(any())

        //mock opprett buc og sed til RINA
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

        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))

        val requestMock = SedController.ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                euxCaseId = "1234567890",
                vedtakId = "1234567",
                institutions = items,
                sed = "P6000",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )

        whenever(mockAktoerregisterService.hentGjeldendeNorskIdentForAktorId(ArgumentMatchers.anyString())).thenReturn("12345")
        val utfyllMock = sedController.buildPrefillDataModelOnExisting(requestMock)

        assertNotNull(utfyllMock.personNr)
        assertEquals("12345", utfyllMock.personNr)

        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))
        whenever(mockPrefillSED.prefill(any())).thenReturn(utfyllMock)
        whenever(mockEuxService.opprettSedOnBuc(any(), any())).thenReturn(bucresponse)

        val response = sedController.addDocument(requestMock)
        assertEquals("123444455", response.caseId)
        assertEquals("2a427c10325c4b5eaf3c27ba5e8f1877", response.documentId)
    }

    @Test
    fun `confirm document`() {
        val mockData = SedController.ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                vedtakId = "1234567",
                institutions = listOf(InstitusjonItem("NO", "DUMMY")),
                euxCaseId = "1234567890",
                sed = "P6000",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )
        whenever(mockAktoerregisterService.hentGjeldendeNorskIdentForAktorId(ArgumentMatchers.anyString())).thenReturn("12345")

        val utfyllMock = sedController.buildPrefillDataModelConfirm(mockData)

        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))
        whenever(mockPrefillSED.prefill(any())).thenReturn(utfyllMock)

        val response = sedController.confirmDocument(mockData)
        //val response = SED.fromJson(response2)

        assertNotNull(response)
        assertEquals("P6000", response.sed)
        assertEquals("Dummy", response.nav?.bruker?.person?.fornavn)
        assertEquals("Dummy", response.nav?.bruker?.person?.etternavn)
    }

    @Test(expected = SedDokumentIkkeGyldigException::class)
    fun `confirm document when sed is not valid`() {
        val mockData = SedController.ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                institutions = listOf(InstitusjonItem("NO", "DUMMY")),
                sed = "Q3300",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )
        sedController.buildPrefillDataModelConfirm(mockData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `confirm document sed is null`() {
        val mockData = SedController.ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                institutions = listOf(InstitusjonItem("NO", "DUMMY")),
                sed = null,
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )
        sedController.buildPrefillDataModelConfirm(mockData)
    }

    @Test
    fun `check on minimum valid request to model`() {
        val mockData = SedController.ApiRequest(
                sakId = "12234",
                sed = "P6000",
                aktoerId = "0105094340092"
        )

        whenever(mockAktoerregisterService.hentGjeldendeNorskIdentForAktorId(ArgumentMatchers.anyString())).thenReturn("12345")

        val model = sedController.buildPrefillDataModelConfirm(mockData)

        assertEquals("12345", model.personNr)
        assertEquals("12234", model.penSaksnummer)
        assertEquals("0105094340092", model.aktoerID)
        assertEquals("P6000", model.getSEDid())

        assertEquals(SED::class.java, model.sed::class.java)

    }

    @Test(expected = IllegalArgumentException::class)
    fun `check on aktoerId is null`() {
        val mockData = SedController.ApiRequest(
                sakId = "1213123123",
                sed = "P6000",
                aktoerId = null
        )
        sedController.buildPrefillDataModelConfirm(mockData)
    }

    @Test
    fun `check rest api path correct`() {
        val path = "/sed/get/{rinanr}/{documentid}"
        val uriParams = mapOf("rinanr" to "123456789", "documentid" to "DOC1223213234234")
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        assertEquals("/sed/get/123456789/DOC1223213234234", builder.path)
    }

}