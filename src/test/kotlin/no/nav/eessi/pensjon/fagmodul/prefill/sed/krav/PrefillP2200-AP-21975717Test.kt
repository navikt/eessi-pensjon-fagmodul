package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.sed.AbstractPrefillIntegrationTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.utils.mapAnyToJson
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(MockitoJUnitRunner::class)
class `PrefillP2200-AP-21975717Test` : AbstractPrefillIntegrationTestHelper() {

    private val fakeFnr = PersonDataFromTPS.generateRandomFnr(68)

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2200", "P2000_21975717_AP_UTLAND.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2200(sakHelper, kravHistorikkHelper)
    }

    override fun createSaksnummer(): String {
        return "14915730"
    }

    override fun createPayload(prefillData: PrefillDataModel) {
        prefillData.personNr = fakeFnr
        prefillData.partSedAsJson["PersonInfo"] = createPersonInfoPayLoad()
        prefillData.partSedAsJson["P4000"] = createPersonTrygdetidHistorikk()
    }

    override fun createPersonInfoPayLoad(): String {
        return readJsonResponse("other/person_informasjon_selvb.json")
    }

    override fun createPersonTrygdetidHistorikk(): String {
        return readJsonResponse("other/p4000_trygdetid_part.json")
    }

    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", fakeFnr, PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", PersonDataFromTPS.generateRandomFnr(70), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        )
    }

    @Test
    fun `sjekk av kravsøknad alderpensjon P2000`() {
        pendata = sakHelper.getPensjoninformasjonFraSak(prefillData)

        assertNotNull(pendata)

        val list = sakHelper.getPensjonSakTypeList(pendata)
        assertEquals(2, list.size)
    }

    @Test
    fun `forventet korrekt utfylt P2200 uforerpensjon med mockdata fra testfiler`() {
        val p2200 = prefill.prefill(prefillData)

        assertEquals(null, p2200.nav?.barn)

        assertEquals("", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.yrke)
        assertEquals("2018-11-12", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtstartdato)
        assertEquals("2018-11-14", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtpensjoneringsdato)
        assertEquals("07", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.type)

        assertEquals("foo", p2200.nav?.bruker?.bank?.navn)
        assertEquals("bar", p2200.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", p2200.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals("HASNAWI-MASK", p2200.nav?.bruker?.person?.fornavn)
        assertEquals("OKOULOV", p2200.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p2200.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(68, navfnr1.getAge())

        assertNotNull(p2200.nav?.bruker?.person?.pin)
        val pinlist = p2200.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(fakeFnr, pinitem?.identifikator)

        assertEquals("RANNAR-MASK", p2200.nav?.ektefelle?.person?.fornavn)
        assertEquals("MIZINTSEV", p2200.nav?.ektefelle?.person?.etternavn)

        val navfnr = NavFodselsnummer(p2200.nav?.ektefelle?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(70, navfnr.getAge())

    }

    @Test
    fun `testing av komplett P2200 med utskrift og testing av innsending`() {
        val P2200 = prefill.prefill(prefillData)
        val json = mapAnyToJson(createMockApiRequest("P2200", "P_BUC_01", P2200.toJson()))
        assertNotNull(json)
    }

    private fun createMockApiRequest(sedName: String, buc: String, payload: String): ApiRequest {
        val items = listOf(InstitusjonItem(country = "NO", institution = "NAVT003"))
        return ApiRequest(
                institutions = items,
                sed = sedName,
                sakId = "14915730",
                euxCaseId = null,
                aktoerId = "1000060964183",
                buc = buc,
                subjectArea = "Pensjon",
                payload = payload,
                mockSED = true
        )
    }

}

