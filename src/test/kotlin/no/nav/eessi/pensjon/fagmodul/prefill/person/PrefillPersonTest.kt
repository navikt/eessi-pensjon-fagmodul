package no.nav.eessi.pensjon.fagmodul.prefill.person

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.NavMock
import no.nav.eessi.pensjon.fagmodul.sedmodel.PensjonMock
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillDefaultSED
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillFactory
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSED
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(Parameterized::class)
class PrefillPersonTest(val index: Int, val sedid: String) {

    @Mock
    private lateinit var mockPreutfyllingNav: PrefillNav

    @Mock
    private lateinit var mockPreutfyllingPensjon: PrefillPensjon

    @Mock
    lateinit var preutfylling: PrefillPerson

    @Mock
    private lateinit var mockPrefillFactory: PrefillFactory

    private lateinit var mockPrefillSED: PrefillSED

    private lateinit var prefillDataMock: PrefillDataModel

    private lateinit var prefillDefaultSED: PrefillDefaultSED

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        prefillDataMock = PrefillDataModel()
        preutfylling = PrefillPerson(prefillNav = mockPreutfyllingNav, prefilliPensjon = mockPreutfyllingPensjon)

        prefillDefaultSED = PrefillDefaultSED(preutfylling)

        mockPrefillSED = PrefillSED(mockPrefillFactory)
    }

    companion object {
        @Parameters
        @JvmStatic
        fun `collection data`(): Collection<Array<Any>> {
            return listOf(
                    arrayOf(10, "P2000"),
                    arrayOf(20, "P2100"),
                    arrayOf(30, "P2200"),
                    arrayOf(40, "P3000"),
                    arrayOf(60, "P5000"),
                    arrayOf(70, "P6000"),
                    arrayOf(80, "P7000")
            )
        }
    }

    @Test
    fun `create mock on prefill SED`() {
        val mockPinResponse = "12345"

        val navresponse = NavMock().genererNavMock()

        whenever(mockPreutfyllingNav.prefill(any())).thenReturn(navresponse)

        val pensjonresponse = PensjonMock().genererMockData()
        whenever(mockPreutfyllingPensjon.prefill(any())).thenReturn(pensjonresponse)


        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        prefillDataMock.apply {
                rinaSubject = "Pensjon"
                sed = SED(sedid)
                penSaksnummer = "12345"
                buc = "P_BUC_06"
                aktoerID = "1234"
                personNr = "12345"
                institution = items
        }
        whenever(mockPrefillFactory.createPrefillClass(prefillDataMock)).thenReturn(prefillDefaultSED)

        val responseData = mockPrefillSED.prefill(prefillDataMock)
        assertNotNull(responseData)
        val responseSED = responseData.sed

        assertNotNull(responseSED)
        assertNotNull(responseSED.nav)
        assertNotNull(responseSED.nav?.bruker)
        assertNotNull(responseSED.nav?.bruker?.person)

        assertEquals("Konsoll", responseSED.nav?.bruker?.person?.etternavn)
        assertEquals("Gul", responseSED.nav?.bruker?.person?.fornavn)
        assertEquals("1967-12-01", responseSED.nav?.bruker?.person?.foedselsdato)
        assertEquals("asfsdf", responseSED.nav?.bruker?.mor?.person?.fornavn)

        val pin = responseSED.nav?.bruker?.person?.pin
        assertEquals("weqrwerwqe", pin!![0].identifikator)
        assertEquals("sdfsdfsdfsdf sdfsdfsdf", responseSED.nav?.bruker?.bank?.navn)

        assertEquals(sedid, responseSED.sed)
        assertNotNull(prefillDataMock)
        assertEquals(mockPinResponse, prefillDataMock.personNr)

    }

}
