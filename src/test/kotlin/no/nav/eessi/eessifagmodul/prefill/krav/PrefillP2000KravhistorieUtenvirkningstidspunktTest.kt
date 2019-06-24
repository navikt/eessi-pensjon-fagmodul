package no.nav.eessi.eessifagmodul.prefill.krav

import no.nav.eessi.eessifagmodul.models.Nav
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.AbstractPrefillIntegrationTestHelper
import no.nav.eessi.eessifagmodul.prefill.PensjonsinformasjonHjelper
import no.nav.eessi.eessifagmodul.prefill.Prefill
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillNav
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPersonDataFromTPS
import no.nav.eessi.eessifagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.eessifagmodul.utils.NavFodselsnummer
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class PrefillP2000KravhistorieUtenvirkningstidspunktTest : AbstractPrefillIntegrationTestHelper() {

    val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2000KravhistorieUtenvirkningstidspunktTest::class.java) }

    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", getFakePersonFnr(), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", PersonDataFromTPS.generateRandomFnr(69), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        )
    }

    override fun createFakePersonFnr(): String {
        if (personFnr.isNullOrBlank()) {
            personFnr = PersonDataFromTPS.generateRandomFnr(67)
        }
        return personFnr
        //return PersonDataFromTPS.generateRandomFnr(67)
    }

    override fun createSaksnummer(): String {
        return "21920707"
    }

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2000", "P2000-AP-KUNUTL-IKKEVIRKNINGTID.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2000(prefillNav, personTPS, pensionDataFromPEN)
    }

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
        pendata = kravdata.getPensjoninformasjonFraSak(prefillData)

        assertNotNull(pendata)
        val pensak = kravdata.getPensjonSak(prefillData, pendata)
        assertNotNull(pensak)

        assertNotNull(pendata.brukersSakerListe)
        assertEquals("ALDER", pensak.sakType)
    }

    @Test
    fun `Utfylling alderpensjon uten kravhistorikk Kunutland uten virkningstidspunkt`() {
        val P2000 = prefill.prefill(prefillData)

        val P2000pensjon = SED.create("P2000")
        P2000pensjon.pensjon = P2000.pensjon
        P2000pensjon.nav = Nav(
                krav = P2000.nav?.krav
        )
        logger.info(P2000pensjon.toString())

        val sed = P2000pensjon

        val navfnr = NavFodselsnummer(sed.pensjon?.ytelser?.get(0)?.pin?.identifikator!!)
        assertEquals(67, navfnr.getAge())

    }

}