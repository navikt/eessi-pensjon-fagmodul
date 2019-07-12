package no.nav.eessi.pensjon.fagmodul.prefill.krav

import no.nav.eessi.pensjon.fagmodul.services.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.Nav
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.fagmodul.models.SED
import no.nav.eessi.pensjon.fagmodul.prefill.AbstractPrefillIntegrationTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.nav.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.nav.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.NavFodselsnummer
import no.nav.eessi.pensjon.utils.mapAnyToJson
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PrefillP2000MedIngendata : AbstractPrefillIntegrationTestHelper() {

    val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2000MedIngendata::class.java) }

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2000", "P2000-TOMT-SVAR-PESYS.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2000(prefillNav, personTPS, pensionDataFromPEN)
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

    override fun createSaksnummer(): String {
        return "21644722"
    }

    override fun createPersonInfoPayLoad(): String {
        return readJsonResponse("other/person_informasjon_selvb.json")
    }

    override fun createPersonTrygdetidHistorikk(): String {
        return readJsonResponse("other/p4000_trygdetid_part.json")
    }

    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", getFakePersonFnr(), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", PersonDataFromTPS.generateRandomFnr(70), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        )
    }

    @Test(expected = PensjoninformasjonException::class)
    fun `sjekk av kravsøknad alderpensjon P2000`() {
        kravdata.getPensjoninformasjonFraSak(prefillData)
//        assertNotNull(pendata)
//        val list = kravdata.getPensjonSakTypeList(pendata)
//        assertEquals(1, list.size)
    }

    @Test
    fun `forventet korrekt utfylt P2000 alderpensjon med kap4 og 9`() {
        prefillData.penSaksnummer = "21644722"
        val P2000 = prefill.prefill(prefillData)

        val P2000pensjon = SED.create("P2000")
        P2000pensjon.pensjon = P2000.pensjon
        P2000pensjon.nav = Nav(
                krav = P2000.nav?.krav
        )
        logger.info(P2000pensjon.toString())

        val sed = P2000pensjon
        assertNotNull(sed.pensjon)
        assertNull(sed.nav?.krav)
        //assertNotNull(sed.nav?.krav)
        //assertEquals("2018-06-05", sed.nav?.krav?.dato)


    }

    @Test
    fun `forventet korrekt utfylt P2000 alderpersjon med mockdata fra testfiler`() {
        val p2000 = prefill.prefill(prefillData)

        logger.info(p2000.toString())

        try {
            prefill.validate(p2000)
        } catch (ex: Exception){
            logger.error("Feilen er ${ex.message}")
            assertEquals("Kravdato mangler", ex.message)
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
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(createFakePersonFnr(), pinitem?.identifikator)

        assertEquals("RANNAR-MASK", p2000.nav?.ektefelle?.person?.fornavn)
        assertEquals("MIZINTSEV", p2000.nav?.ektefelle?.person?.etternavn)

        val navfnr = NavFodselsnummer(p2000.nav?.ektefelle?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(70, navfnr.getAge())

    }

    @Test
    fun `testing av komplett P2000 med utskrift og testing av innsending`() {
        val P2000 = prefill.prefill(prefillData)

        logger.info(P2000.toString())

        validateAndPrint(createMockApiRequest("P2000", "P_BUC_01", P2000.toJson()))

    }

    private fun createMockApiRequest(sedName: String, buc: String, payload: String): ApiRequest {
        val items = listOf(InstitusjonItem(country = "NO", institution = "NAVT003"))
        return ApiRequest(
                institutions = items,
                sed = sedName,
                sakId = "01234567890",
                euxCaseId = "99191999911",
                aktoerId = "1000060964183",
                buc = buc,
                subjectArea = "Pensjon",
                payload = payload,
                mockSED = true
        )
    }

    fun validateAndPrint(req: ApiRequest, printout: Boolean = true) {
        if (printout) {
            val json = mapAnyToJson(req)
            assertNotNull(json)
            logger.info("\n\n\n $json \n\n\n")
        }
    }

}

