package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.sak.V1Sak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrefillP8000APUtlandInnvTest {
    private val personFnr = generateRandomFnr(68)
    private val ekteFnr = generateRandomFnr(70)
    private val pesysSaksnummer = "14398627"
    lateinit var prefillData: PrefillDataModel
    lateinit var prefill: PrefillP8000
    lateinit var prefillNav: PrefillPDLNav
    lateinit var persondataCollection: PersonDataCollection

    @BeforeEach
    fun setup() {
        persondataCollection = PersonPDLMock.createEnkelFamilie(personFnr, ekteFnr)

        prefillNav = PrefillPDLNav(
                prefillAdresse = mock(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        val prefillSed = PrefillSed(prefillNav)
        prefill = PrefillP8000(prefillSed)
        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P8000, personFnr, penSaksnummer = pesysSaksnummer)

    }

    @Test
    fun `forventet korrekt utfylt P8000 alderperson med mockdata fra testfiler`() {

        val sak = V1Sak()
        sak.sakType = EPSaktype.GJENLEV.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()

        val p8000 = prefill.prefill(prefillData, persondataCollection, sak)

        assertEquals("ODIN ETTØYE", p8000.nav?.bruker?.person?.fornavn)
        assertEquals("BALDER", p8000.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p8000.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(68, navfnr1.getAge())

        assertNotNull(p8000.nav?.bruker?.person?.pin)
        val pinlist = p8000.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals(personFnr, pinitem?.identifikator)

        assertNull(p8000.nav?.annenperson)
        assertNull(p8000.pensjon)

    }
}

