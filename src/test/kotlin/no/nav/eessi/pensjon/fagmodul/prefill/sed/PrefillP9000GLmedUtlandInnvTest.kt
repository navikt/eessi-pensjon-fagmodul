package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP9000GLmedUtlandInnvTest {

    private val personFnr = generateRandomFnr(65)
    private val avdodPersonFnr = generateRandomFnr(75)

    private val pesysSaksnummer = "22875355"
    lateinit var prefillData: PrefillDataModel
    lateinit var prefillNav: PrefillPDLNav
    lateinit var prefillSEDService: PrefillSEDService
    private lateinit var personDataCollection: PersonDataCollection

    @BeforeEach
    fun setup() {
        personDataCollection = PersonPDLMock.createAvdodFamilie(personFnr, avdodPersonFnr)

        prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P9000, personFnr, penSaksnummer = pesysSaksnummer, avdod = PersonId(avdodPersonFnr, "112233445566"))

        val pensjonInformasjonService = PrefillTestHelper.lesPensjonsdataFraFil("KravAlderEllerUfore_AP_UTLAND.xml")

        prefillSEDService = PrefillSEDService(pensjonInformasjonService, EessiInformasjon(), prefillNav)

    }

    @Test
    fun `forventet korrekt utfylt P9000 med mockdata fra testfiler`() {
        val p9000 = prefillSEDService.prefill(prefillData, personDataCollection)

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

