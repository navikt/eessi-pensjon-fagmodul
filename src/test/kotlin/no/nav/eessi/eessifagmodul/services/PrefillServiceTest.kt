package no.nav.eessi.eessifagmodul.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillSED
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.eessi.eessifagmodul.services.eux.BucUtils
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ParticipantsItem
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
    fun `call prefillAndAddInstitusionAndSedOnExistingCase| ingen nyDeltaker kun hovedsed vellykket`() {
        val euxCaseId = "12131234"
        val docId = "2a427c10325c4b5eaf3c27ba5e8f1877"

        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()

        dataModel.euxCaseID = euxCaseId
        resultData.sed = generateMockP2000(dataModel)
        resultData.euxCaseID = euxCaseId

        //mock bucResponse
        val mockBucResponse = BucSedResponse(euxCaseId, docId)

        //mock prefill utfylling av sed
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        //mock shortdoc svar
        val mockShortDoc = ShortDocumentItem(id = docId, type = "P2000", status = "Nadada")

        //mock bucUtils
        val mockbuc = Mockito.mock(BucUtils::class.java)

        //mock find shortdoc from id
        whenever(mockbuc.findDocument(docId)).thenReturn(mockShortDoc)

        //mock bucutls return mocked bucdata
        whenever(mockEuxService.getBucUtils(euxCaseId)).thenReturn(mockbuc)

        //mock opprett SED on buc return mockBuc response
        whenever(mockEuxService.opprettSedOnBuc(resultData.sed, euxCaseId)).thenReturn(mockBucResponse)

        //run impl.
        val result = prefillService.prefillAndAddInstitusionAndSedOnExistingCase(dataModel)

        //assert result
        assertNotNull(result)
        assertEquals(docId, result.id)
    }

    @Test
    fun callPrefillAndAddInstitusionAndSedOnExistingCaseMocking_add_institutions_and_sed_to_buc() {
        val euxCaseId = "12131234"
        val docId = "2a427c10325c4b5eaf3c27ba5e8f1877"

        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()

        dataModel.euxCaseID = euxCaseId
        resultData.sed = generateMockP2000(dataModel)
        resultData.euxCaseID = euxCaseId

        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        dataModel.institution = mockInstitusjonList

        //Mocking buc without X005
        val filepath = "src/test/resources/json/buc/buc-175254_noX005_v4.1.json"
        val bucjson = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(bucjson))
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        //mock euxService getBucUtils
        whenever(mockEuxService.getBucUtils(euxCaseId)).thenReturn(bucUtils)

        //mock bucResponse
        val mockBucResponse = BucSedResponse(euxCaseId, docId)
        //mock opprett SED on buc return mockBuc response
        whenever(mockEuxService.opprettSedOnBuc(resultData.sed, euxCaseId)).thenReturn(mockBucResponse)
        //mock prefill utfylling av sed
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        val result = prefillService.prefillAndAddInstitusionAndSedOnExistingCase(dataModel)

        //assert result
        assertNotNull(result)
        assertEquals(docId, result.id)
    }

    //fun `call prefillAndAddInstitusionAndSedOnExistingCase| ingen nyDeltaker kun hovedsed vellykket`() {
    @Test
    fun callPrefillAndAddInstitusionAndSedOnExistingCase_Add_X005_to_buc_ok() {
        val euxCaseId = "12131234"
        val docId = "2a427c10325c4b5eaf3c27ba5e8f1877"

        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()

        dataModel.euxCaseID = euxCaseId
        resultData.sed = generateMockP2000(dataModel)
        resultData.euxCaseID = euxCaseId

        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        dataModel.institution = mockInstitusjonList

        //Mocking buc without X005
        val filepath = "src/test/resources/json/buc/buc-175380_x005_v4.1.json"
        val bucjson = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(bucjson))
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        //mock euxService getBucUtils
        whenever(mockEuxService.getBucUtils(euxCaseId)).thenReturn(bucUtils)

        //mock bucResponse
        val mockBucResponse = BucSedResponse(euxCaseId, docId)
        //mock opprett SED on buc return mockBuc response
        whenever(mockEuxService.opprettSedOnBuc(resultData.sed, euxCaseId)).thenReturn(mockBucResponse)
        //mock prefill utfylling av sed
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        val result = prefillService.prefillAndAddInstitusionAndSedOnExistingCase(dataModel)

        //assert result
        assertNotNull(result)
        assertEquals(docId, result.id)
    }


    @Test
    fun `call addX005| mock adding two institusjon X005sed to buc result true`() {
        val euxCaseId = "12131234"

        val dataModel = generatePrefillModel()
        dataModel.euxCaseID = euxCaseId

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)
        val mockInstitusjonList = listOf(
            InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        //mock bucResponse
        val mockBucResponse = BucSedResponse(euxCaseId, "2a427c10325c4b5eaf3c27ba5e8f1877")
        //mock prefill utfylling av sed
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        //mock opprett SED on buc return mockBuc response
        whenever(mockEuxService.opprettSedOnBuc(resultData.sed, euxCaseId)).thenReturn(mockBucResponse)

        val result = prefillService.addX005(dataModel,mockInstitusjonList)
        assertEquals(true, result)
    }

    @Test
    fun `call addX005| mock adding institusjon X005sed throws Exception result false`() {
        val euxCaseId = "12131234"

        val dataModel = generatePrefillModel()
        dataModel.euxCaseID = euxCaseId

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)
        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        //mock prefill utfylling av sed
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        //mock opprett SED on buc return mockBuc response
        whenever(mockEuxService.opprettSedOnBuc(resultData.sed, euxCaseId))
                .thenThrow(EuxGenericServerException("Error error error"))

        val result = prefillService.addX005(dataModel,mockInstitusjonList)
        assertEquals(false, result)
    }

    @Test
    fun `call addInstitution| adding two institusjon normal way`() {
        val euxCaseId = "12131234"
        val dataModel = generatePrefillModel()
        dataModel.euxCaseID = euxCaseId

        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)
        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        //mock bucUtils
        val mockbuc = Mockito.mock(BucUtils::class.java)

        whenever(prefillService.addInstitutionsOrCreateX005(dataModel, mockbuc)).thenReturn(mockInstitusjonList)

        whenever(mockbuc.findFirstDocumentItemByType("X005")).thenReturn(null)

        whenever(mockEuxService.addDeltagerInstitutions(any(), any())).thenReturn(true)

        prefillService.addInstitution(mockbuc, dataModel)

    }

    @Test
    fun `call getFirstInstitution | two elements first return ok`() {
        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        val result = prefillService.getFirstInstitution(mockInstitusjonList)
        assertEquals(mockInstitusjonList[0], result)
    }

    @Test(expected = IkkeGyldigKallException::class)
    fun `call getFirstInstitution | no elements`() {
        val mockInstitusjonList = listOf<InstitusjonItem>()
        prefillService.getFirstInstitution(mockInstitusjonList)
    }

    @Test(expected = SedDokumentIkkeOpprettetException::class)
    fun `call prefillAndAddInstitusionAndSedOnExistingCase| Exception eller feil`() {
        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()
        resultData.sed = generateMockP2000(dataModel)

        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)
        //mock bucutls return mocked bucdata
        val mockbuc = Mockito.mock(BucUtils::class.java)

        whenever(mockEuxService.getBucUtils(dataModel.euxCaseID)).thenReturn(mockbuc)

        whenever(mockEuxService.opprettSedOnBuc(any(), any())).thenThrow(SedDokumentIkkeOpprettetException::class.java)
        prefillService.prefillAndAddInstitusionAndSedOnExistingCase(dataModel)
    }




    @Test
    fun `call prefillAndAddSedOnExistingCase| forventer euxCaseId og documentID, tilbake vellykket`() {

        val euxCaseId = "12131234"
        val docId = "2a427c10325c4b5eaf3c27ba5e8f1877"

        val dataModel = generatePrefillModel()
        val resultData = generatePrefillModel()

        resultData.sed = generateMockP2000(dataModel)
        resultData.euxCaseID = euxCaseId

        //mock bucResponse
        val mockBucResponse = BucSedResponse(euxCaseId, docId)

        //mock prefill utfylling av sed
        whenever(mockPrefillSED.prefill(any())).thenReturn(resultData)

        //mock shortdoc svar
        val mockShortDoc = ShortDocumentItem(id = docId, type = "P2000", status = "Nadada")

        //mock bucUtils
        val mockbuc = Mockito.mock(BucUtils::class.java)

        //mock find shortdoc from id
        whenever(mockbuc.findDocument(docId)).thenReturn(mockShortDoc)

        //mock antal participant
        //whenever(mockbuc.getParticipants()).thenReturn(listOf(ParticipantsItem()))

        //mock bucutls return mocked bucdata
        whenever(mockEuxService.getBucUtils(euxCaseId)).thenReturn(mockbuc)

        //mock opprett SED on buc return mockBuc response
        whenever(mockEuxService.opprettSedOnBuc(resultData.sed, euxCaseId)).thenReturn(mockBucResponse)

        //mock leggetil detalger
        //whenever(mockEuxService.addDeltagerInstitutions(any(), any())).thenReturn(true)

        //run impl.
        val result = prefillService.prefillAndAddSedOnExistingCase(dataModel)

        //assert result
        assertNotNull(result)
        assertEquals(docId, result.id)
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

    fun generateMockX005(prefillModel: PrefillDataModel): SED {
        val mockP2000 = generateMockP2000(prefillModel)
        val person = mockP2000.nav?.bruker?.person

        val x005Datamodel = PrefillDataModel.fromJson(prefillModel.clone())
        val x005 = SED.create("X005")
        x005Datamodel.sed = x005
        x005.nav = Nav(
                sak = Navsak(
                        kontekst = Kontekst(
                                bruker = Bruker(
                                        person = Person(
                                                fornavn = person?.fornavn,
                                                etternavn = person?.etternavn,
                                                foedselsdato = person?.foedselsdato
                                        )
                                )
                        ),
                        leggtilinstitusjon = Leggtilinstitusjon(
                                institusjon = InstitusjonX005(
                                        id = "",
                                        navn = ""
                                ),
                                grunn = ""
                        )
                )
        )
        x005Datamodel.sed = x005
        return x005Datamodel.sed
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

    fun generatePrefillModel(bucType: String, caseID: String, navSed: SED): PrefillDataModel {
        return PrefillDataModel().apply {
            euxCaseID = caseID
            sed = navSed
            buc  = bucType
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