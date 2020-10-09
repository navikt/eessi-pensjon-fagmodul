package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.LagTPSPerson.Companion.lagTPSBruker
import no.nav.eessi.pensjon.fagmodul.prefill.LagTPSPerson.Companion.medAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.LagTPSPerson.Companion.medBarn
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.setupPersondataFraTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class PrefillP8000P_BUC_05Test {

    private val personFnr = generateRandomFnr(68)
    private val pesysSaksnummer = "14398627"

    lateinit var prefillData: PrefillDataModel
    lateinit var personV3Service: PersonV3Service
    lateinit var prefill: PrefillP8000
    lateinit var prefillNav: PrefillNav
    lateinit var personData: PersonData

    @BeforeEach
    fun setup() {
        personV3Service = setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-11000-GIFT.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-12000-EKTE.json", generateRandomFnr(70), MockTpsPersonServiceFactory.MockTPS.TPSType.EKTE)
        ))

        val person = personV3Service.hentBruker(personFnr)

        prefillNav = PrefillNav(
                prefillAdresse = mock(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        val prefillSed = PrefillSed(prefillNav, null)

        prefill = PrefillP8000(prefillSed)

        prefillData = PrefillDataModelMother.initialPrefillDataModel("P8000", personFnr, penSaksnummer = pesysSaksnummer)

        personData = PersonData(forsikretPerson = person!!, ekteTypeValue = "", ektefelleBruker = null, gjenlevendeEllerAvdod = person, barnBrukereFraTPS = listOf())
    }

    @Test
    fun `forventet korrekt utfylt P8000 alderperson med mockdata fra testfiler`() {
        val p8000 = prefill.prefill(prefillData, personData)

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

    @Test
    @Disabled
    fun `Forventer korrekt utfylt P8000 med adresse`() {
        val fnr = generateRandomFnr(68)
        val forsikretPerson = lagTPSBruker(fnr, "Christopher", "Robin")
                .medAdresse("Gate", null, null)

        personData = PersonData(forsikretPerson = forsikretPerson!!, ekteTypeValue = "", ektefelleBruker = null, gjenlevendeEllerAvdod = forsikretPerson, barnBrukereFraTPS = listOf())

        val sed = prefill.prefill(prefillData, personData)


        assertEquals("Christopher", sed?.nav?.bruker?.person?.fornavn)
        assertEquals("Gate", sed?.nav?.bruker?.adresse?.gate)

    }

}

