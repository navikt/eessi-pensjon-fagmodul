package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.sed.AbstractPrefillIntegrationTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class PrefillP2000AlderPensjonUtlandForsteGangTest : AbstractPrefillIntegrationTestHelper() {

    //mock familie
    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", getFakePersonFnr(), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", PersonDataFromTPS.generateRandomFnr(69), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        )
    }

    //Generere fakePersonFnr nr
    override fun createFakePersonFnr(): String {
        if (personFnr.isNullOrBlank()) {
            personFnr = PersonDataFromTPS.generateRandomFnr(67)
        }
        return personFnr
    }

    override fun createSaksnummer(): String {
        return "22580170"
    }

    //Pesys Persjoninformasjon data
    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2000", "AP_FORSTEG_BH.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2000(sakHelper, kravHistorikkHelper)
    }

    //Mock persondata (P4000, persondata fra EP11)
    override fun createPayload(prefillData: PrefillDataModel) {
        prefillData.personNr = getFakePersonFnr()
        prefillData.partSedAsJson["PersonInfo"] = createPersonInfoPayLoad()
        prefillData.partSedAsJson["P4000"] = createPersonTrygdetidHistorikk()
    }

    override fun createPersonInfoPayLoad(): String {
        return readJsonResponse("other/person_informasjon_selvb.json")
    }

    override fun createPersonTrygdetidHistorikk(): String {
        return readJsonResponse("other/p4000_trygdetid_part.json")
    }

    @Test
    fun `Sjekk av kravsøknad alderpensjon P2000`() {
        pendata = sakHelper.getPensjoninformasjonFraSak(prefillData)

        assertNotNull(pendata)
        val pensak = sakHelper.getPensjonSak(prefillData, pendata)
        assertNotNull(pensak)

        assertNotNull(pendata.brukersSakerListe)
        assertEquals("ALDER", pensak.sakType)
    }

    @Test
    fun `Korrekt ttfylling alderpensjon uten kravhistorikk KunUtland uten virkningstidspunkt`() {
        val P2000 = prefill.prefill(prefillData)

        val P2000pensjon = SED(
                sed = "P2000",
                pensjon = P2000.pensjon,
                nav = Nav( krav = P2000.nav?.krav )
        )

        val sed = P2000pensjon

        val navfnr = NavFodselsnummer(sed.pensjon?.ytelser?.get(0)?.pin?.identifikator!!)
        assertEquals(67, navfnr.getAge())
        val yearnow = LocalDate.now().year
        val bdate = yearnow - navfnr.getAge()

        assertEquals("" + bdate, navfnr.get4DigitBirthYear())
        assertEquals("2018-05-31", sed.nav?.krav?.dato)
    }

}