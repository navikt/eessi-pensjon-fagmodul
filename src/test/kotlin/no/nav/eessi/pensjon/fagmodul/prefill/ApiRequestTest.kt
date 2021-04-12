package no.nav.eessi.pensjon.fagmodul.prefill

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.utils.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApiRequestTest {

    private fun createMockApiRequest(sedName: String, buc: String, payload: String?): ApiRequest {
        return ApiRequest(
                institutions = listOf(InstitusjonItem(country = "NO", institution = "NAVT003")),
                sed = sedName,
                sakId = "01234567890",
                euxCaseId = "99191999911",
                aktoerId = "1000060964183",
                buc = buc,
                subjectArea = "Pensjon",
                payload = payload
        )
    }

    private fun readJsonAndParseToSed(filename: String): String {
        val p2200file = javaClass.getResource("/json/nav/$filename").readText()
        assertTrue(validateJson(p2200file))
        return p2200file
    }

    @Test
    fun `generate request mock payload of SED P2000`() {
        val payload = readJsonAndParseToSed("P2000-NAV.json")
        val req = createMockApiRequest("P2000", "P_BUC_01", payload)
        val sedjson = SED.fromJson(req.payload!!).toJson()
        assertNotNull(sedjson)
        val json = mapAnyToJson(req)
        assertNotNull(json)
    }

    @Test
    fun `check og valider request fra ui med institusion uten buc`() {

        val req = "{\n" +
                "  \"sakId\" : \"01234567890\",\n" +
                "  \"vedtakId\" : null,\n" +
                "  \"kravId\" : null,\n" +
                "  \"aktoerId\" : \"1000060964183\",\n" +
                "  \"fnr\" : null,\n" +
                "  \"avdodfnr\" : null,\n" +
                "  \"payload\" : \"{}\",\n" +
                "  \"buc\" : \"P_BUC_01\",\n" +
                "  \"sed\" : \"P2000\",\n" +
                "  \"documentid\" : null,\n" +
                "  \"euxCaseId\" : \"99191999911\",\n" +
                "  \"institutions\" : [ {\n" +
                "    \"country\" : \"NO\",\n" +
                "    \"institution\" : \"NAVT003\",\n" +
                "    \"name\" : null\n" +
                "  } ],\n" +
                "  \"subjectArea\" : \"Pensjon\",\n" +
                "  \"skipSEDkey\" : null,\n" +
                "  \"mockSED\" : true\n" +
                "}"
        val datamodel = ApiRequest.buildPrefillDataModelOnExisting( mapJsonToAny(req, typeRefs<ApiRequest>()), "", "")
        assertNotNull(datamodel)
        assertEquals(SedType.P2000, datamodel.sedType)
        assertEquals("P_BUC_01", datamodel.buc)
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

    @Test
    fun `confirm document when sed is not valid`() {
        val mockData = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                institutions = listOf(InstitusjonItem("NO", "DUMMY")),
                sed = "Q3300",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )
        assertThrows<UgyldigInndataException> {
            ApiRequest.buildPrefillDataModelOnExisting(mockData, "12345", null)
        }
    }

    @Test
    fun `confirm document sed is null`() {
        val mockData = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                institutions = listOf(InstitusjonItem("NO", "DUMMY")),
                sed = null,
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )
        assertThrows<MangelfulleInndataException> {
            ApiRequest.buildPrefillDataModelOnExisting(mockData, "12345", null)
        }
    }

    @Test
    fun `check on minimum valid request to model`() {
        val mockData = ApiRequest(
                sakId = "12234",
                sed = "P6000",
                buc = "P_BUC_01",
                euxCaseId = "1231",
                aktoerId = "0105094340092",
                institutions = emptyList()
        )

        val model = ApiRequest.buildPrefillDataModelOnExisting(mockData, "12345", null)

        assertEquals("12345", model.bruker.norskIdent)
        assertEquals("12234", model.penSaksnummer)
        assertEquals("0105094340092", model.bruker.aktorId)
        assertEquals(SedType.P6000, model.sedType)
    }

    @Test
    fun `check on minimum valid request to model on P2100`() {
        val mockData = ApiRequest(
                sakId = "12234",
                sed = "P2100",
                buc = "P_BUC_02",
                aktoerId = "0105094340092",
                avdodfnr = "010244212312",
                euxCaseId = "1234",
                institutions = emptyList()
        )

        val model = ApiRequest.buildPrefillDataModelOnExisting(mockData, "12345", "2223312")

        assertEquals("12345", model.bruker.norskIdent)
        assertEquals("12234", model.penSaksnummer)
        assertEquals("0105094340092", model.bruker.aktorId)
        assertEquals(SedType.P2100, model.sedType)
        assertEquals("2223312", model.avdod?.aktorId)
        assertEquals("010244212312", model.avdod?.norskIdent)

    }

    @Test
    fun `check valid request to model on P_BUC_02 P5000`() {
        val mockData = ApiRequest(
                sakId = "12234",
                euxCaseId = "2345",
                sed = "P5000",
                buc = "P_BUC_02",
                aktoerId = "0105094340092",
                avdodfnr = null,
                institutions = emptyList<InstitusjonItem>(),
                subject = ApiSubject(gjenlevende = SubjectFnr("23123"), avdod = SubjectFnr("576567567567"))
        )

        val model = ApiRequest.buildPrefillDataModelOnExisting(mockData, "23123", "113123123123")

        assertEquals("23123", model.bruker.norskIdent)
        assertEquals("12234", model.penSaksnummer)
        assertEquals("0105094340092", model.bruker.aktorId)
        assertEquals(SedType.P5000, model.sedType)
        assertEquals("113123123123", model.avdod?.aktorId)
        assertEquals("576567567567", model.avdod?.norskIdent)

    }


    @Test
    fun `request to model without avdod on P_BUC_02 P5000 should throw execptin`() {
        val mockData = ApiRequest(
                sakId = "12234",
                sed = "P5000",
                buc = "P_BUC_02",
                aktoerId = "0105094340092",
                avdodfnr = null,
                subject = null
        )
        assertThrows<MangelfulleInndataException> {
            ApiRequest.buildPrefillDataModelOnExisting(mockData, "23123", null)
        }

    }


    @Test
    fun `check on aktoerId is null`() {
        val mockData = ApiRequest(
                sakId = "1213123123",
                sed = "P6000",
                aktoerId = null
        )
        assertThrows<MangelfulleInndataException> {
            ApiRequest.buildPrefillDataModelOnExisting(mockData, "12345", null)
        }
    }

    @Test
    fun `check on values for toAduit`() {
        val request = ApiRequest(
            sakId = "01234567890",
            vedtakId = "123",
            avdodfnr = "456",
            buc = "P_BUC_01",
            sed = "P2000",
            euxCaseId = "99191999911"
        )

        assertEquals(
            "sakId: 01234567890 vedtakId: 123 avdodfnr: 456 buc: P_BUC_01 sed: P2000 euxCaseId: 99191999911",
            request.toAudit()
        )
    }

}
