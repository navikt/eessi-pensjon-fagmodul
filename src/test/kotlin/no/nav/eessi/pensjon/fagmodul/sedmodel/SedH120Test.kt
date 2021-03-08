package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedH120Test {

    @Test
    fun `compare SED H120 from json datafile`() {
        val h120json = getTestJsonFile("horisontal/H120-NAV.json")
        val h120sed = SED.fromJson(h120json)
        val startskap = h120sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(3, startskap?.size)

        JSONAssert.assertEquals(h120json, h120sed.toJsonSkipEmpty(), false)
    }
}
