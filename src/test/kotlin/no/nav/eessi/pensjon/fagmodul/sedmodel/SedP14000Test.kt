package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP14000Test {

    @Test
    fun `create SED P14000 from json datafile`() {

        val p14000json = getTestJsonFile("P14000-NAV.json")
        val p14000sed = SED.fromJson(p14000json)

        assertEquals("342", p14000sed.nav?.bruker?.endringer?.personpinendringer?.gammelt)
        assertEquals("341", p14000sed.nav?.bruker?.endringer?.personpinendringer?.nytt)

        val json = p14000sed.toJson()
        JSONAssert.assertEquals(p14000json, json, false)
    }
}
