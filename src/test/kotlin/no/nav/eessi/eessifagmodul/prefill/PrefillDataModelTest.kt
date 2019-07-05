package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.person.aktoerregister.AktoerregisterService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class PrefillDataModelTest {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillDataModelTest::class.java) }

    @Mock
    private lateinit var mockAktoerregisterService: AktoerregisterService

    private lateinit var prefill: PrefillDataModel

    @Before
    fun setup() {
        logger.debug("Starting tests..")
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
                sed =  SED.create("vedtak")
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
            sed = SED.create("vedtak")
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