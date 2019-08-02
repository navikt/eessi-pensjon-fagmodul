package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.mockPrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.pensjonsDataFraPEN
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.readJsonResponse
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrefillP2000KravhistorieUtenvirkningstidspunktTest {

    private val personFnr = PersonDataFromTPS.generateRandomFnr(67)

    lateinit var prefillData: PrefillDataModel
    lateinit var sakHelper: SakHelper
    var kravHistorikkHelper = KravHistorikkHelper()
    lateinit var prefill: Prefill<SED>

    @Before
    fun setup() {
        val pensionDataFromPEN = pensjonsDataFraPEN("P2000-AP-KUNUTL-IKKEVIRKNINGTID.xml")
        val prefillPersonDataFromTPS = mockPrefillPersonDataFromTPS(setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", personFnr, PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", PersonDataFromTPS.generateRandomFnr(69), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        ))
        prefillData = PrefillDataModel().apply {
            rinaSubject = "Pensjon"
            sed = SED("P2000")
            penSaksnummer = "21920707"
            vedtakId = "12312312"
            buc = "P_BUC_99"
            aktoerID = "123456789"
            personNr = personFnr
            institution = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
            partSedAsJson = mutableMapOf(
                    "PersonInfo" to readJsonResponse("other/person_informasjon_selvb.json"),
                    "P4000" to readJsonResponse("other/p4000_trygdetid_part.json"))
        }
        val prefillNav = PrefillNav(prefillPersonDataFromTPS, institutionid = "NO:noinst002", institutionnavn = "NOINST002, NO INST002, NO")
        sakHelper = SakHelper(prefillNav, prefillPersonDataFromTPS, pensionDataFromPEN, kravHistorikkHelper)
        prefill = PrefillP2000(sakHelper, kravHistorikkHelper)
    }

    @Test
    fun `Sjekk av kravsøknad alderpensjon P2000`() {
        val pendata = sakHelper.getPensjoninformasjonFraSak(prefillData)

        assertNotNull(pendata)
        val pensak = sakHelper.getPensjonSak(prefillData, pendata)
        assertNotNull(pensak)

        assertNotNull(pendata.brukersSakerListe)
        assertEquals("ALDER", pensak.sakType)
    }

    @Test
    fun `Utfylling alderpensjon uten kravhistorikk Kunutland uten virkningstidspunkt`() {
        val P2000 = prefill.prefill(prefillData)

        val P2000pensjon = SED("P2000")
        P2000pensjon.pensjon = P2000.pensjon
        P2000pensjon.nav = Nav(
                krav = P2000.nav?.krav
        )

        val sed = P2000pensjon

        val navfnr = NavFodselsnummer(sed.pensjon?.ytelser?.get(0)?.pin?.identifikator!!)
        assertEquals(67, navfnr.getAge())

    }

}
