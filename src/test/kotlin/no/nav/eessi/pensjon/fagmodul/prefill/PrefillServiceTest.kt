package no.nav.eessi.pensjon.fagmodul.prefill

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.fagmodul.models.*
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSED
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals

@RunWith(MockitoJUnitRunner::class)
class PrefillServiceTest {

    @Mock
    lateinit var mockPrefillSED: PrefillSED

    private lateinit var prefillService: PrefillService

    @Before
    fun `startup initilize testing`() {
        prefillService = PrefillService(mockPrefillSED)
    }

    @Test
    fun `call prefillEnX005ForHverInstitusjon| mock adding institusjon `() {
        val euxCaseId = "12131234"

        val data = generatePrefillModel()
        data.euxCaseID = euxCaseId
        data.sed = generateMockP2000(data)

        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        whenever(mockPrefillSED.prefill(any())).thenReturn(data)

        val x005Liste = prefillService.prefillEnX005ForHverInstitusjon(mockInstitusjonList, data)

        assertEquals(x005Liste.size, 2)
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
        val x005 = SED("X005")
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
                                grunn = null
                        )
                )
        )
        x005Datamodel.sed = x005
        return x005Datamodel.sed
    }

    fun generatePrefillModel(): PrefillDataModel {
        return PrefillDataModel().apply {
            euxCaseID = "1000"
            sed = SED("P2000")
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
