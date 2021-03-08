package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP2000Test {

    @Test
    fun `create SED P2000 from json datafile`() {

        val p2000json = getTestJsonFile("P2000-NAV.json")
        val p2000sed = SED.fromJson(p2000json)

        val json = p2000sed.toJson()
        JSONAssert.assertEquals(p2000json, json, false)
    }

    @Test
    fun `create SED P2000 new v4_1 from json datafile`() {

        val p2000json = getTestJsonFile("P2000-NAV-4.1-new.json")
        val p2000sed = SED.fromJson(p2000json)

        val json = p2000sed.toJson()
        JSONAssert.assertEquals(p2000json, json, false)

    }

}
