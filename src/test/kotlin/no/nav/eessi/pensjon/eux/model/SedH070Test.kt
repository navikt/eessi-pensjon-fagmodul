package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedH070Test {

    @Test
    fun `compare SED H070 from json datafile`() {
        val h070json = getTestJsonFile("horisontal/H070-NAV.json")
        val h070sed = SED.fromJson(h070json)

        val startskap = h070sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(1, startskap?.size)

    }
}
