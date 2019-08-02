package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail


class PrefillP2000UtenKravhistorieTest : AbstractPrefillIntegrationTestHelper() {

    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-20000.json", getFakePersonFnr(), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-21000.json", PersonDataFromTPS.generateRandomFnr(43), PersonDataFromTPS.MockTPS.TPSType.BARN),
                PersonDataFromTPS.MockTPS("Person-22000.json", PersonDataFromTPS.generateRandomFnr(17), PersonDataFromTPS.MockTPS.TPSType.BARN)
        )
    }

    override fun createFakePersonFnr(): String {
        return PersonDataFromTPS.generateRandomFnr(67)
    }

    override fun createSaksnummer(): String {
        return "14069110"
    }

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2000", "P2000-AP-14069110.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2000(sakHelper, kravHistorikkHelper)
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
        pendata = sakHelper.getPensjoninformasjonFraSak(prefillData)
        assertNotNull(pendata)

        val pensak = sakHelper.getPensjonSak(prefillData, pendata)
        assertNotNull(pensak)

        assertNotNull(pendata.brukersSakerListe)
        assertEquals("ALDER", pensak.sakType)

    }

    @Test
    fun `Testing av komplett utfylling kravsøknad alderpensjon ENKW med 2 barn P2000`() {
        val p2000 = prefill.prefill(prefillData)

        try {
            prefill.validate(p2000)
            fail("TODO why is this expected?")
        } catch (ex: Exception){
            // TODO why is this expected?
            assertEquals("Kravdato mangler", ex.message)
        }

        assertEquals(2, p2000.nav?.barn?.size)

        assertEquals("BOUWMANS", p2000.nav?.barn?.get(0)?.person?.etternavn)
        assertEquals("TOPPI DOTTO", p2000.nav?.barn?.get(0)?.person?.fornavn)

        val navfnr1 = NavFodselsnummer(p2000.nav?.barn?.get(0)?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(43, navfnr1.getAge())

        assertEquals("01", p2000.nav?.barn?.get(0)?.person?.sivilstand?.get(0)?.status)

        assertEquals("BOUWMANS", p2000.nav?.barn?.get(1)?.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", p2000.nav?.barn?.get(1)?.person?.fornavn)

        val navfnr2 = NavFodselsnummer(p2000.nav?.barn?.get(1)?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(17, navfnr2.getAge())

        assertNotNull(p2000.nav?.bruker?.person?.pin)
        val pinlist = p2000.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(createFakePersonFnr(), pinitem?.identifikator)

        assertEquals("01", p2000.nav?.barn?.get(1)?.person?.sivilstand?.get(0)?.status)

        assertEquals("", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.yrke)
        assertEquals("2018-11-12", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtstartdato)
        assertEquals("2018-11-14", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtpensjoneringsdato)
        assertEquals("07", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.type)

        assertEquals("foo", p2000.nav?.bruker?.bank?.navn)
        assertEquals("bar", p2000.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", p2000.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals(1, p2000.pensjon?.ytelser?.size)
    }

    @Test
    fun `utfylling av barn`() {
        val P2000 = prefill.prefill(prefillData)
        assertEquals(2, P2000.nav?.barn?.size)

        assertEquals("BOUWMANS", P2000.nav?.barn?.get(0)?.person?.etternavn)
        assertEquals("TOPPI DOTTO", P2000.nav?.barn?.get(0)?.person?.fornavn)

        val navfnr1 = NavFodselsnummer(P2000.nav?.barn?.get(0)?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(43, navfnr1.getAge())

        assertEquals("01", P2000.nav?.barn?.get(0)?.person?.sivilstand?.get(0)?.status)

        assertEquals("BOUWMANS", P2000.nav?.barn?.get(1)?.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", P2000.nav?.barn?.get(1)?.person?.fornavn)

        val navfnr2 = NavFodselsnummer(P2000.nav?.barn?.get(1)?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(17, navfnr2.getAge())
        assertEquals("01", P2000.nav?.barn?.get(1)?.person?.sivilstand?.get(0)?.status)
    }

    @Test
    fun `utfulling og test på ektefelle samboer partner`() {
        val P2000 = prefill.prefill(prefillData)

        val result = P2000.nav?.ektefelle
        // TODO Why?
        assertNull(result)
    }

    @Test
    fun `Utfulling og test på verge vil allid være ull`() {
        val P2000 = prefill.prefill(prefillData)

        val result = P2000.nav?.verge
        assertNull(result)
    }

    @Test
    fun `Utfylling alderpensjon ENKKE med uten kravhistorikk (nær blank P2000)`() {
        val P2000 = prefill.prefill(prefillData)

        val navfnr = NavFodselsnummer(P2000.pensjon?.ytelser?.get(0)?.pin?.identifikator!!)
        assertEquals(67, navfnr.getAge())
    }
}