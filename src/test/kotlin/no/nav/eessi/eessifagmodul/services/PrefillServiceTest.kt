package no.nav.eessi.eessifagmodul.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillSED
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(MockitoJUnitRunner::class)
class PrefillServiceTest {

    @Mock
    lateinit var mockEuxService: EuxService

    @Mock
    lateinit var mockPrefillSED: PrefillSED

    private lateinit var prefillService: PrefillService

    @Before
    fun `startup initilize testing`() {
        prefillService = PrefillService(mockEuxService, mockPrefillSED)

    }

    @Test
    fun `forventer et euxCaseId eller rinasakid og documentID, tilbake på et vellykket kall til prefillAndAddSedOnExistingCase`() {
        val mockBucResponse = BucSedResponse("1234567", "2a427c10325c4b5eaf3c27ba5e8f1877")

        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)
        resultData.euxCaseID = "12131234"

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettSedOnBuc(any(), any())).thenReturn(mockBucResponse)

        val result = prefillService.prefillAndAddSedOnExistingCase(dataModel)

        assertNotNull(result)
        assertEquals("1234567", result.caseId)
        assertEquals("2a427c10325c4b5eaf3c27ba5e8f1877", result.documentId)

    }

    @Test(expected = SedDokumentIkkeOpprettetException::class)
    fun `forventer en Exception eller feil tilbake på et feil kall til prefillAndAddSedOnExistingCase`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)
        resultData.euxCaseID = "12131234"
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettSedOnBuc(any(), any())).thenThrow(SedDokumentIkkeOpprettetException::class.java)

        prefillService.prefillAndAddSedOnExistingCase(dataModel)
    }

    @Test(expected = EuxGenericServerException::class)
    fun `forventer en Exception eller feil tilbake på prefillAndAddSedOnExistingCase når eux er nede`() {
        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        resultData.euxCaseID = "12131234"
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettSedOnBuc(any(), any())).thenThrow(EuxGenericServerException::class.java)

        prefillService.prefillAndAddSedOnExistingCase(dataModel)
    }


    @Test
    fun `forventer euxCaseID eller RinaId og documentId tilbake ved vellykket kall til prefillAndCreateSedOnNewCase`() {
        val dataModel = generatePrefillModel()
        val bucResponse = BucSedResponse("1234567890", "1231231-123123-123123")

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettBucSed(any(), any(), any(), any())).thenReturn(bucResponse)

        val result = prefillService.prefillAndCreateSedOnNewCase(resultData)
        assertEquals("1234567890", result.caseId)
        assertEquals("1231231-123123-123123", result.documentId)
    }

    @Test(expected = RinaCasenrIkkeMottattException::class)
    fun `forventer Exception tilbake ved kall til prefillAndCreateSedOnNewCase som feiler`() {
        val dataModel = generatePrefillModel()
        dataModel.euxCaseID = "1234567890"

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettBucSed(any(), any(), any(), any())).thenThrow(RinaCasenrIkkeMottattException::class.java)

        prefillService.prefillAndCreateSedOnNewCase(resultData)
    }

    @Test(expected = EuxServerException::class)
    fun `forventer Exception ved kall til prefillAndCreateSedOnNewCase når eux er nede`() {
        val dataModel = generatePrefillModel()
        dataModel.euxCaseID = "1234567890"

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettBucSed(any(), any(), any(), any())).thenThrow(EuxServerException::class.java)

        prefillService.prefillAndCreateSedOnNewCase(resultData)
    }

    fun generateMockP2000(prefillModel: PrefillDataModel): SED {
        val mocksed = prefillModel.sed
        val mockp2000 = SedMock().genererP2000Mock()
        mocksed.nav = mockp2000.nav
        mocksed.pensjon = mockp2000.pensjon
        return mocksed
    }

    fun generatePrefillModel(): PrefillDataModel {
        return PrefillDataModel().apply {
            euxCaseID = "1000"
            sed = SED.create("P2000")
            buc  = "P_BUC_01"
            institution = listOf(
                    InstitusjonItem(
                            country = "NO",
                            institution = "DUMMY"
                    )
            )
            penSaksnummer = "123456789999"
            personNr = "12345678901"
        }

    }

}