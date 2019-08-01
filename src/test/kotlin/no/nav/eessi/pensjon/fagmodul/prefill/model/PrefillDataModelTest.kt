package no.nav.eessi.pensjon.fagmodul.prefill.model

import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class PrefillDataModelTest {

    private lateinit var prefill: PrefillDataModel

    @Before
    fun setup() {
        prefill = PrefillDataModel()
    }

    @Test
    fun `check for valid claimant fail`() {
        assertFalse(prefill.erGyldigEtterlatt())
    }

    @Test
    fun `check for valid claimant pin and aktorid is blank fail`() {
        prefill.avdod = ""
        assertFalse(prefill.erGyldigEtterlatt())
    }

    @Test
    fun `check for valid claimant deceased pin and aktorid is filled`() {
        prefill.avdod = "23123"
        assertTrue(prefill.erGyldigEtterlatt())
    }

    @Test
    fun `check for valid claimant deceased parents filled`() {
        prefill.avdodFar = "2312354"
        prefill.avdodMor = "2312376"
        assertTrue(prefill.erForeldreLos())
    }

    @Test
    fun `validate and check model build`() {

        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        prefill.apply {
                rinaSubject = "Pensjon"
                sed =  SED("vedtak")
                penSaksnummer = "12345"
                buc = "P_BUC_06"
                aktoerID = "567890"
                personNr = "123456789"
                institution = items
        }
        assertNotNull(prefill)
        assertEquals("vedtak", prefill.getSEDid())
        assertEquals(SED::class, prefill.sed::class)
        assertEquals("12345", prefill.penSaksnummer)
        assertEquals("567890", prefill.aktoerID)
        assertEquals("123456789", prefill.personNr)
    }


    @Test
    fun `create and test valid pinid for aktoerid`() {
        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        prefill.apply {
            rinaSubject= "Pensjon"
            sed = SED("vedtak")
            penSaksnummer = "12345"
            buc = "P_BUC_06"
            aktoerID = "32"
            personNr = "1234000001"
            institution = items
        }
        assertNotNull(prefill)
        assertNotNull(prefill.personNr)
        assertEquals("1234000001", prefill.personNr)
    }

}