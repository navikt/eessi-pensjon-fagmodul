package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.sed.AbstractPrefillIntegrationTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.utils.mapAnyToJson
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class `PrefillP2000-AP-21975717Test` : AbstractPrefillIntegrationTestHelper() {

    private val giftFnr = PersonDataFromTPS.generateRandomFnr(68)
    private val ekteFnr = PersonDataFromTPS.generateRandomFnr(70)

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2000", "P2000_21975717_AP_UTLAND.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2000(kravdata)
    }

    override fun createSaksnummer(): String {
        return "21975717"
    }

    override fun createPayload(prefillData: PrefillDataModel) {
        prefillData.personNr = getFakePersonFnr()
        prefillData.partSedAsJson["PersonInfo"] = createPersonInfoPayLoad()
        prefillData.partSedAsJson["P4000"] = createPersonTrygdetidHistorikk()
    }

    override fun createFakePersonFnr(): String {
        if (personFnr.isNullOrBlank()) {
            personFnr = PersonDataFromTPS.generateRandomFnr(68)
        }
        return personFnr
    }

    override fun createPersonInfoPayLoad(): String {
        return readJsonResponse("other/person_informasjon_selvb.json")
    }

    override fun createPersonTrygdetidHistorikk(): String {
        return readJsonResponse("other/p4000_trygdetid_part.json")
    }

    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", giftFnr, PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", ekteFnr, PersonDataFromTPS.MockTPS.TPSType.EKTE)
        )
    }

    @Test
    fun `sjekk av kravsøknad alderpensjon P2000`() {
        pendata = kravdata.getPensjoninformasjonFraSak(prefillData)

        assertNotNull(pendata)

        val list = kravdata.getPensjonSakTypeList(pendata)

        assertEquals(2, list.size)
    }

    @Test
    fun `forventet korrekt utfylt P2000 alderpensjon med kap4 og 9`() {
        val P2000 = prefill.prefill(prefillData)

        val P2000pensjon = SED("P2000")
        P2000pensjon.pensjon = P2000.pensjon
        P2000pensjon.nav = Nav(
                krav = P2000.nav?.krav
        )
        val sed = P2000pensjon
        assertNotNull(sed.nav?.krav)
        assertEquals("2015-06-16", sed.nav?.krav?.dato)


    }

    @Test
    fun `forventet korrekt utfylt P2000 alderpersjon med mockdata fra testfiler`() {
        val p2000 = prefill.prefill(prefillData)

        prefill.validate(p2000)

        assertEquals(null, p2000.nav?.barn)

        assertEquals("", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.yrke)
        assertEquals("2018-11-12", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtstartdato)
        assertEquals("2018-11-14", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtpensjoneringsdato)
        assertEquals("07", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.type)

        assertEquals("foo", p2000.nav?.bruker?.bank?.navn)
        assertEquals("bar", p2000.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", p2000.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals("HASNAWI-MASK", p2000.nav?.bruker?.person?.fornavn)
        assertEquals("OKOULOV", p2000.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p2000.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(68, navfnr1.getAge())

        assertNotNull(p2000.nav?.bruker?.person?.pin)
        val pinlist = p2000.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(createFakePersonFnr(), pinitem?.identifikator)

        assertEquals("RANNAR-MASK", p2000.nav?.ektefelle?.person?.fornavn)
        assertEquals("MIZINTSEV", p2000.nav?.ektefelle?.person?.etternavn)

        assertEquals(ekteFnr, p2000.nav?.ektefelle?.person?.pin?.get(0)?.identifikator)
        assertEquals(giftFnr, p2000.nav?.bruker?.person?.pin?.get(0)?.identifikator)

        assertEquals("NO", p2000.nav?.ektefelle?.person?.pin?.get(0)?.land)

        val navfnr = NavFodselsnummer(p2000.nav?.ektefelle?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(70, navfnr.getAge())

        assertNotNull(p2000.nav?.krav)
        assertEquals("2015-06-16", p2000.nav?.krav?.dato)

    }

    @Test
    fun `testing av komplett P2000 med utskrift og testing av innsending`() {
        val P2000 = prefill.prefill(prefillData)
        prefill.validate(P2000)

        val json = mapAnyToJson(createMockApiRequest("P2000", "P_BUC_01", P2000.toJson()))
        assertNotNull(json)
    }

    private fun createMockApiRequest(sedName: String, buc: String, payload: String): ApiRequest {
        val items = listOf(InstitusjonItem(country = "NO", institution = "NAVT003"))
        return ApiRequest(
                institutions = items,
                sed = sedName,
                sakId = "21975717",
                euxCaseId = null,
                aktoerId = "1000060964183",
                buc = buc,
                subjectArea = "Pensjon",
                payload = payload,
                mockSED = true
        )
    }
}

