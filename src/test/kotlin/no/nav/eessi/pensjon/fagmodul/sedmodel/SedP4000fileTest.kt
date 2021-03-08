package no.nav.eessi.pensjon.fagmodul.sedmodel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP4000fileTest {

    @Test
    fun `validate P4000 to json and back`() {
        val p4000json = getTestJsonFile("P4000-NAV.json")
        val p4000sed = SED.fromJson(p4000json)

        val json = mapAnyToJson(p4000sed, true)
        val pensjondata = mapJsonToAny(json, typeRefs<SED>())
        assertNotNull(pensjondata)
        JSONAssert.assertEquals(p4000json, json, false)

    }

    @Test
    fun `valider P4000 til json og tilbake`() {
        val p4000json = getTestJsonFile("other/P4000-from-frontend.json")

        val map = mapJsonToAny(p4000json, typeRefs<Map<String, Any>>())
        val periodeInfoJson = mapAnyToJson(map["periodeInfo"] ?: "{}")

        val sed = SED(SEDType.P4000)
        sed.trygdetid = mapJsonToAny( periodeInfoJson, typeRefs())

        assertEquals("work period 1 workName", sed.trygdetid?.ansattSelvstendigPerioder?.first()?.navnFirma)
        assertEquals("Ole", sed.trygdetid?.barnepassPerioder?.first()?.informasjonBarn?.fornavn)
        assertEquals("daily period 1 payingInstitution", sed.trygdetid?.arbeidsledigPerioder?.first()?.navnPaaInstitusjon)
        assertEquals("EE", sed.trygdetid?.boPerioder?.first()?.land)
        assertEquals("GG", sed.trygdetid?.opplaeringPerioder?.first()?.land)
        assertEquals("learn period 1 learnInstitution", sed.trygdetid?.opplaeringPerioder?.first()?.navnPaaInstitusjon)
    }

    @Test
    fun `valider P4000 til json med JsonNode og tilbake`() {
        val p4000json = getTestJsonFile("other/P4000-from-frontend.json")

        val mapper = jacksonObjectMapper()
        val personDataNode = mapper.readTree(p4000json)
        val personDataJson =  personDataNode["periodeInfo"].toString()

        val sed = SED(SEDType.P4000)
        sed.trygdetid = mapJsonToAny(personDataJson, typeRefs())

        val trygdetidJson = sed.trygdetid?.toJson()

       JSONAssert.assertEquals(personDataJson, trygdetidJson, false)
    }

}


