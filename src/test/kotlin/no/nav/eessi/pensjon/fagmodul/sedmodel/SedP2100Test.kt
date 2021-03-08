package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP2100Test {

    @Test
    fun `create SED P2100 from json datafile`() {

        val p2100json = getTestJsonFile("P2100-PinDK-NAV.json")
        val p2100sed = SED.fromJson(p2100json)

        val json = p2100sed.toJson()
        JSONAssert.assertEquals(p2100json, json, false)
    }
}
