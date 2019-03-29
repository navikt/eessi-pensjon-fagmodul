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
import kotlin.test.fail

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
    fun `call prefillAndAddSedOnExistingCase| forventer euxCaseId og documentID, tilbake vellykket`() {
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
    fun `call prefillAndAddSedOnExistingCase| Exception eller feil`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)
        resultData.euxCaseID = "12131234"
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettSedOnBuc(any(), any())).thenThrow(SedDokumentIkkeOpprettetException::class.java)

        prefillService.prefillAndAddSedOnExistingCase(dataModel)
    }

    @Test(expected = EuxGenericServerException::class)
    fun `call prefillAndAddSedOnExistingCase| Exception eller feil tilbake EUX-RINA er nede`() {
        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        resultData.euxCaseID = "12131234"
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettSedOnBuc(any(), any())).thenThrow(EuxGenericServerException::class.java)

        prefillService.prefillAndAddSedOnExistingCase(dataModel)
    }


    @Test
    fun `call prefillAndCreateSedOnNewCase| forventer euxCaseID og documentId tilbake OK`() {
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
    fun `call prefillAndCreateSedOnNewCase| Exception feiler`() {
        val dataModel = generatePrefillModel()
        dataModel.euxCaseID = "1234567890"

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettBucSed(any(), any(), any(), any())).thenThrow(RinaCasenrIkkeMottattException::class.java)

        prefillService.prefillAndCreateSedOnNewCase(resultData)
    }

    @Test(expected = EuxServerException::class)
    fun `call prefillAndCreateSedOnNewCase| Exception ved kall EUX er nede`() {
        val dataModel = generatePrefillModel()
        dataModel.euxCaseID = "1234567890"

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        whenever(mockEuxService.opprettBucSed(any(), any(), any(), any())).thenThrow(EuxServerException::class.java)

        prefillService.prefillAndCreateSedOnNewCase(resultData)
    }

    @Test(expected = SedValidatorException::class)
    fun `call prefillAndPreview| Exception ved validating SED`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000ForValidatorError(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        prefillService.prefillSed(resultData)
    }

    @Test
    fun `call prefillAndPreview| Exception ved validating SED etternavn`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000ForValidatorError(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        try {
            prefillService.prefillSed(resultData)
            fail("skal ikke komme hit!")
        } catch (sedv: SedValidatorException) {
            assertEquals("Etternavn mangler", sedv.message)
        }
    }

    @Test
    fun `call prefillAndPreview| Exception ved validating SED fornavn`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000ForValidatorError(dataModel)
        resultData.sed.nav?.bruker = Bruker(person = Person(etternavn = "BAMSELUR"))

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        try {
            prefillService.prefillSed(resultData)
            fail("skal ikke komme hit!")
        } catch (sedv: SedValidatorException) {
            assertEquals("Fornavn mangler", sedv.message)
        }
    }

    @Test
    fun `call prefillAndPreview| Exception ved validating SED fdato`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000ForValidatorError(dataModel)
        resultData.sed.nav?.bruker = Bruker(person = Person(etternavn = "BAMSELUR", fornavn = "DUMMY"))

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        try {
            prefillService.prefillSed(resultData)
            fail("skal ikke komme hit!")
        } catch (sedv: SedValidatorException) {
            assertEquals("Fødseldsdato mangler", sedv.message)
        }
    }

    @Test
    fun `call prefillAndPreview| Exception ved validating SED kjonn`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000ForValidatorError(dataModel)
        resultData.sed.nav?.bruker = Bruker(person = Person(etternavn = "BAMSELUR", fornavn = "DUMMY", kjoenn = "M"))

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        try {
            prefillService.prefillSed(resultData)
            fail("skal ikke komme hit!")
        } catch (sedv: SedValidatorException) {
            assertEquals("Fødseldsdato mangler", sedv.message)
        }
    }

    @Test
    fun `call prefillAndPreview| Exception ved validating SED kravDato`() {
        val dataModel = generatePrefillModel()

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000ForValidatorError(dataModel)
        resultData.sed.nav?.bruker = Bruker(person = Person(etternavn = "BAMSELUR", fornavn = "DUMMY", kjoenn = "M", foedselsdato = "1955-05-05"))

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        try {
            prefillService.prefillSed(resultData)
            fail("skal ikke komme hit!")
        } catch (sedv: SedValidatorException) {
            assertEquals("Kravdato mangler", sedv.message)
        }
    }


    fun generateMockP2000(prefillModel: PrefillDataModel): SED {
        val mocksed = prefillModel.sed
        val mockp2000 = SedMock().genererP2000Mock()
        mocksed.nav = mockp2000.nav
        mocksed.nav?.krav = Krav("1960-06-12")
        mocksed.pensjon = mockp2000.pensjon
        return mocksed
    }

    fun generateMockP2000ForValidatorError(prefillModel: PrefillDataModel): SED {
        val mocksed = prefillModel.sed
        mocksed.nav = Nav()
        mocksed.pensjon = Pensjon()
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