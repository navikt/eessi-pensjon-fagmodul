package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.setupPersondataFraTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP9000GLmedUtlandInnvTest {

    private val personFnr = generateRandomFnr(65)
    private val avdodPersonFnr = generateRandomFnr(75)
    private val pesysSaksnummer = "22875355"
    lateinit var prefillData: PrefillDataModel
    lateinit var prefillNav: PrefillNav
    lateinit var prefillSEDService: PrefillSEDService

    @Mock
    lateinit var aktorRegisterService: AktoerregisterService



    @BeforeEach
    fun setup() {
        val persondataFraTPS = setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-30000.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-31000.json", avdodPersonFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON)
        ))

        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        prefillData = PrefillDataModelMother.initialPrefillDataModel("P9000", personFnr, penSaksnummer = pesysSaksnummer, avdod = PersonId(avdodPersonFnr, "112233445566"))

        val pensjonInformasjonService = PrefillTestHelper.lesPensjonsdataFraFil("KravAlderEllerUfore_AP_UTLAND.xml")

        prefillSEDService = PrefillSEDService(prefillNav, persondataFraTPS, EessiInformasjon(), pensjonInformasjonService, aktorRegisterService)

    }

    @Test
    fun `forventet korrekt utfylt P9000 med mockdata fra testfiler`() {
        val p9000 = prefillSEDService.prefill(prefillData)

        println("-----------------------------------------------------------")
        println(p9000?.nav?.bruker?.person?.toJsonSkipEmpty())
        println(p9000?.pensjon?.gjenlevende?.person?.toJsonSkipEmpty())
        println("-----------------------------------------------------------")

        assertEquals("BAMSE LUR", p9000.nav?.bruker?.person?.fornavn)
        assertEquals("MOMBALO", p9000.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p9000.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(75, navfnr1.getAge())
        assertEquals("M", p9000.nav?.bruker?.person?.kjoenn)

        assertNotNull(p9000.nav?.bruker?.person?.pin)
        val pinlist = p9000.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals(avdodPersonFnr, pinitem?.identifikator)

        assertEquals("01", p9000.nav?.annenperson?.person?.rolle)
        assertEquals("BAMSE ULUR", p9000.nav?.annenperson?.person?.fornavn)
        assertEquals("DOLLY", p9000.nav?.annenperson?.person?.etternavn)
        val navfnr2 = NavFodselsnummer(p9000.nav?.annenperson?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(65, navfnr2.getAge())
        assertEquals("K", p9000.nav?.annenperson?.person?.kjoenn)

        assertNotNull(p9000.pensjon)
        assertNotNull(p9000.pensjon?.gjenlevende)

    }

}

