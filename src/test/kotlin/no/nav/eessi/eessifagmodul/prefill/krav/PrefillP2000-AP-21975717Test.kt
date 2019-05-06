package no.nav.eessi.eessifagmodul.prefill.krav

import no.nav.eessi.eessifagmodul.controllers.SedController
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.Nav
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.PensjonsinformasjonHjelper
import no.nav.eessi.eessifagmodul.prefill.Prefill
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillNav
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPersonDataFromTPS
import no.nav.eessi.eessifagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.eessifagmodul.services.SedValidator
import no.nav.eessi.eessifagmodul.utils.NavFodselsnummer
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

//@RunWith(MockitoJUnitRunner::class)
class `PrefillP2000-AP-21975717Test` : AbstractMockKravPensionHelper() {

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2000", "P2000_21975717_AP_UTLAND.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2000(prefillNav, personTPS, pensionDataFromPEN)
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
        return readJsonResponse("person_informasjon_selvb.json")
    }

    override fun createPersonTrygdetidHistorikk(): String {
        return readJsonResponse("p4000_trygdetid_part.json")
    }

    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", getFakePersonFnr(), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", PersonDataFromTPS.generateRandomFnr(70), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        )
    }

    @Test
    fun `sjekk av kravsøknad alderpensjon P2000`() {
        pendata = kravdata.getPensjoninformasjonFraSak(prefillData)

        assertNotNull(pendata)

        val list = kravdata.getPensjonSakTypeList(pendata)
        list.forEach {
            println(it.name)
        }
        assertEquals(2, list.size)
    }

    @Test
    fun `forventet korrekt utfylt P2000 alderpensjon med kap4 og 9`() {
        val P2000 = prefill.prefill(prefillData)

        val P2000pensjon = SED.create("P2000")
        P2000pensjon.pensjon = P2000.pensjon
        P2000pensjon.nav = Nav(
                krav = P2000.nav?.krav
        )
        P2000pensjon.print()

        val sed = P2000pensjon
        assertNotNull(sed.nav?.krav)
        assertEquals("2015-06-16", sed.nav?.krav?.dato)


    }

    @Test
    fun `forventet korrekt utfylt P2000 alderpersjon med mockdata fra testfiler`() {
        val p2000 = prefill.prefill(prefillData)

        p2000.print()

        val validator = SedValidator()
        try {
            validator.validateP2000(p2000)
        } catch (ex: Exception) {
            println("Feilen er ${ex.message}")
            fail("Validatoren skal ikke komme hit!")
        }

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
        assertEquals("pensjon", pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(createFakePersonFnr(), pinitem?.identifikator)

        assertEquals("RANNAR-MASK", p2000.nav?.ektefelle?.person?.fornavn)
        assertEquals("MIZINTSEV", p2000.nav?.ektefelle?.person?.etternavn)

        val navfnr = NavFodselsnummer(p2000.nav?.ektefelle?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(70, navfnr.getAge())

        assertNotNull(p2000.nav?.krav)
        assertEquals("2015-06-16", p2000.nav?.krav?.dato)

    }

    @Test
    fun `testing av komplett P2000 med utskrift og testing av innsending`() {
        val P2000 = prefill.prefill(prefillData)

        P2000.print()

        validateAndPrint(createMockApiRequest("P2000", "P_BUC_01", P2000.toJson()))

    }

    private fun createMockApiRequest(sedName: String, buc: String, payload: String): SedController.ApiRequest {
        val items = listOf(InstitusjonItem(country = "NO", institution = "NAVT003"))
        return SedController.ApiRequest(
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

    fun validateAndPrint(req: SedController.ApiRequest, printout: Boolean = true) {
        if (printout) {
            val json = mapAnyToJson(req)
            assertNotNull(json)
            println("\n\n\n $json \n\n\n")
        }
    }

}

