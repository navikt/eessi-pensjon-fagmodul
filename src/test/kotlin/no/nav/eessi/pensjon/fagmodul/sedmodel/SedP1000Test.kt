package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP1000Test {

    @Test
    fun `compare SED P1000`() {
        val sedJson = getTestJsonFile("P1000-NAV.json")
        val p1000sed = SED.fromJson(sedJson)

        Assertions.assertEquals("6.3 merknader", p1000sed.nav?.barnoppdragelse?.merknakder)
        Assertions.assertEquals("1", p1000sed.nav?.barnoppdragelse?.art442ECverdi)
        Assertions.assertEquals("06", p1000sed.nav?.barnoppdragelse?.relasjonperson?.verdi)

        JSONAssert.assertEquals(sedJson, p1000sed.toJsonSkipEmpty(), false)
    }


}