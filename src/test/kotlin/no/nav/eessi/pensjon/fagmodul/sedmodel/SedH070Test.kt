package no.nav.eessi.pensjon.fagmodul.sedmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedH070Test {

    @Test
    fun `compare SED H070 from json datafile`() {
        val h070json = getTestJsonFile("horisontal/H070-NAV.json")
        val h070sed = SED.fromJson(h070json)

        val startskap = h070sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(1, startskap?.size)

        val doedsfall = h070sed.nav?.bruker?.doedsfall

        assertEquals("1988-02-11", doedsfall?.doedsdato)

        assertEquals(2, doedsfall?.dokumentervedlagt?.annet?.size)
        assertEquals(3, doedsfall?.dokumentervedlagt?.type?.size)
    }
}
