package no.nav.eessi.pensjon.eux.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.eux.model.sed.P4000
import no.nav.eessi.pensjon.eux.model.sed.SED
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
    }

    @Test
    fun `valider P4000 til json og tilbake`() {
        val p4000json = getTestJsonFile("other/P4000-from-frontend.json")

        val map = mapJsonToAny(p4000json, typeRefs<Map<String, Any>>())
        val periodeInfoJson = mapAnyToJson(map["periodeInfo"] ?: "{}")

        val p4000 = P4000()
        p4000.trygdetid = mapJsonToAny( periodeInfoJson, typeRefs())

        assertEquals("work period 1 workName", p4000.trygdetid?.ansattSelvstendigPerioder?.first()?.navnFirma)
        assertEquals("Ole", p4000.trygdetid?.barnepassPerioder?.first()?.informasjonBarn?.fornavn)
        assertEquals("daily period 1 payingInstitution", p4000.trygdetid?.arbeidsledigPerioder?.first()?.navnPaaInstitusjon)
        assertEquals("EE", p4000.trygdetid?.boPerioder?.first()?.land)
        assertEquals("GG", p4000.trygdetid?.opplaeringPerioder?.first()?.land)
        assertEquals("learn period 1 learnInstitution", p4000.trygdetid?.opplaeringPerioder?.first()?.navnPaaInstitusjon)
    }

    @Test
    fun `valider P4000 til json med JsonNode og tilbake`() {
        val p4000json = getTestJsonFile("other/P4000-from-frontend.json")

        val mapper = jacksonObjectMapper()
        val personDataNode = mapper.readTree(p4000json)
        val personDataJson =  personDataNode["periodeInfo"].toString()

        val sed = P4000()
        sed.trygdetid = mapJsonToAny(personDataJson, typeRefs())

        val trygdetidJson = sed.trygdetid?.toJson()

       JSONAssert.assertEquals(personDataJson, trygdetidJson, false)
    }

}


