package no.nav.eessi.pensjon.fagmodul.prefill

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.InstitusjonX005
import no.nav.eessi.pensjon.fagmodul.sedmodel.Kontekst
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Leggtilinstitusjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Navsak
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.sedmodel.SedMock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillServiceTest {

    @Mock
    lateinit var mockPrefillSEDService: PrefillSEDService

    private lateinit var prefillService: PrefillService

    @BeforeEach
    fun `startup initilize testing`() {
        prefillService = PrefillService(mockPrefillSEDService)
    }

    @Test
    fun `call prefillEnX005ForHverInstitusjon| mock adding institusjon `() {
        val euxCaseId = "12131234"

        val data = generatePrefillModel()
        val sed = generateMockP2000(data)

        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        whenever(mockPrefillSEDService.prefill(any())).thenReturn(data.sed)
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

        //val x005Datamodel = PrefillDataModel.fromJson(prefillModel.clone())
        val x005 = SED("X005")
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
        return x005
    }

    fun generatePrefillModel(): PrefillDataModel {
        return PrefillDataModel(
                penSaksnummer = "123456789999",
                bruker = PersonId("12345678901", "dummy"),
                avdod = null,
                euxCaseID = "1000",
                sedType = "P2000",
                sed = SED("P2000"),
                buc  = "P_BUC_01",
                institution = listOf(
                InstitusjonItem(
                        country = "NO",
                        institution = "DUMMY"))
                )
    }
}
