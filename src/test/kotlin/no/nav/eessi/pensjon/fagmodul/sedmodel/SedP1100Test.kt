package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP1100Test {

    @Test
    fun `compare SED P1100`() {
        val sedJson = getTestJsonFile("P1100-NAV.json")

        val p1100sed = SED.fromJson(sedJson)

        Assertions.assertEquals(null, p1100sed.nav?.barnoppdragelse?.merknakder)
        Assertions.assertEquals("1", p1100sed.nav?.barnoppdragelse?.svar?.aktivitetverdi)
        Assertions.assertEquals("1", p1100sed.nav?.barnoppdragelse?.svar?.nasjonalverdi)

        JSONAssert.assertEquals(sedJson, p1100sed.toJsonSkipEmpty(), false)
    }


}