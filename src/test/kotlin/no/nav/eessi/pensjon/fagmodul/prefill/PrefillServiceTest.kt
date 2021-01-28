package no.nav.eessi.pensjon.fagmodul.prefill

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
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
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert

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
    fun `call prefillEnX005ForHverInstitusjon sjekk paa antall`() {
        val data = generatePrefillModel()

        val mockInstitusjonList = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        val x005sed = generateMockX005(data)
        doReturn(x005sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))
        val x005Liste = prefillService.prefillEnX005ForHverInstitusjon(mockInstitusjonList, data)
        assertEquals(x005Liste.size, 2)
    }

    @Test
    fun `call prefillEnX005ForHverInstitusjon mock adding institusjon `() {
        val data = generatePrefillModel()

        val de = InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")

        val sedtype = SEDType.X005.name
        val instX005 = InstitusjonX005(
            id = de.checkAndConvertInstituion(),
            navn = de.name ?: de.checkAndConvertInstituion()
        )

        val datax005 = data.copy(avdod = null, sedType = sedtype, sed = SED(sedtype), institution = emptyList(), institusjonX005 = instX005)
        val x005sed = generateMockX005(datax005)

        doReturn(x005sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))

        val mockInstitusjonList = listOf(de)
        val x005Liste = prefillService.prefillEnX005ForHverInstitusjon(mockInstitusjonList, data)

        assertEquals(x005Liste.size, 1)
        val result = x005Liste[0]

        val valid = """
            {
              "sed" : "X005",
              "sedGVer" : "4",
              "sedVer" : "1",
              "nav" : {
                "sak" : {
                  "kontekst" : {
                    "bruker" : {
                      "person" : {
                        "etternavn" : "Konsoll",
                        "fornavn" : "Gul",
                        "foedselsdato" : "1967-12-01"
                      }
                    }
                  },
                  "leggtilinstitusjon" : {
                    "institusjon" : {
                      "id" : "DE:Tyskland",
                      "navn" : "Tyskland test"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        JSONAssert.assertEquals(result.toJsonSkipEmpty(), valid, true)

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
                                institusjon = prefillModel.institusjonX005,
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
