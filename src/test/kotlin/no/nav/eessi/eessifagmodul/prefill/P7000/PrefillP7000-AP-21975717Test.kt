package no.nav.eessi.eessifagmodul.prefill.P7000

import no.nav.eessi.eessifagmodul.eux.SedController
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.*
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillNav
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPersonDataFromTPS
import no.nav.eessi.eessifagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class `PrefillP7000-AP-21975717Test` : AbstractPrefillIntegrationTestHelper() {

    val logger: Logger by lazy { LoggerFactory.getLogger(`PrefillP7000-AP-21975717Test`::class.java) }

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P7000", "P2000_21975717_AP_UTLAND.xml")
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP7000(prefillNav)
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
            personFnr = PersonDataFromTPS.generateRandomFnr(101)
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
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", getFakePersonFnr(), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", PersonDataFromTPS.generateRandomFnr(70), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        )
    }

    @Test
    fun `forventet korrekt utfylt P7000 Melding om vedtakssammendrag med MockData fra testfiler`() {
        val p7000 = prefill.prefill(prefillData)

        logger.info(p7000.toJsonSkipEmpty())

        assertEquals("OKOULOV", p7000.nav?.ektefelle?.person?.etternavn)
        assertEquals("M", p7000.pensjon?.bruker?.person?.kjoenn)
    }

    @Test
    fun `forventet P7000 er lik sedfil med MockData fra testfiler`() {
        val filepath = "src/test/resources/json/nav/P7000_OK_NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val p7000 = prefill.prefill(prefillData)
        //setter pin id lik det json fila er ellers vil denne test feile!
        p7000.nav?.bruker?.person?.pin?.get(0)?.identifikator = "01051843352"

        val sed = p7000.toJsonSkipEmpty()

        assertTrue(validateJson(sed))
       JSONAssert.assertEquals(json, sed, true)

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
            logger.info("\n\n\n $json \n\n\n")
        }
    }

}

