package no.nav.eessi.eessifagmodul.controllers

import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.services.LandkodeService
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApIRequestTest {

    private val printout = false
    private val printsed = false

    private val logger: Logger by lazy { LoggerFactory.getLogger(LandkodeService::class.java) }


    private fun createMockApiRequest(sedName: String, buc: String, payload: String): SedController.ApiRequest {
        val items = listOf(InstitusjonItem(country = "NO", institution = "NAVT003"))
        return SedController.ApiRequest(
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

    private fun readJsonAndParseToSed(filename: String): String {
        val p2200path = Paths.get("src/test/resources/json/nav/$filename")
        val p2200file = String(Files.readAllBytes(p2200path))
        assertTrue(validateJson(p2200file))
        return p2200file
    }

    fun validateAndPrint(req: SedController.ApiRequest) {
        if (printsed) {
            val json = SED.fromJson(req.payload!!).toJson()
            logger.info(json)
        }
        if (printout) {
            val json = mapAnyToJson(req)
            assertNotNull(json)
            logger.info("\n\n\n $json \n\n\n")
        }
    }

    @Test
    fun `generate request mock payload of SED P2000`() {
        val payload = readJsonAndParseToSed("P2000-NAV.json")
        //val payload = readJsonAndParseToSed("P2000-NAV-mockAP.json")
        validateAndPrint(createMockApiRequest("P2000", "P_BUC_01", payload))
    }

    @Test
    fun `generate request mock payload of SED P2100`() {
        val payload = readJsonAndParseToSed("P2100-NAV-unfin.json")
        createMockApiRequest("P2100", "P_BUC_02", payload)
    }

    @Test
    fun `generate request mock payload of SED P2200`() {
        val payload = readJsonAndParseToSed("P2200-NAV.json")
        createMockApiRequest("P2200", "P_BUC_03", payload)
    }

    @Test
    fun `generate request mock payload of SED P4000`() {
        val payload = readJsonAndParseToSed("P4000-NAV.json")
        createMockApiRequest("P4000", "P_BUC_05", payload)
    }

    @Test
    fun `generate request mock payload of SED P5000`() {
        val payload = readJsonAndParseToSed("P5000-NAV.json")
        createMockApiRequest("P5000", "P_BUC_05", payload)
    }

    @Test
    fun `generate request mock payload of SED P6000`() {
        val payload = readJsonAndParseToSed("P6000-NAV.json")
        createMockApiRequest("vedtak", "P_BUC_06", payload)
    }
}