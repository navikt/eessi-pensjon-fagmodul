package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedH120Test {

    @Test
    fun `compare SED H120 from json datafile`() {
        val h120json = getTestJsonFile("horisontal/H120-NAV.json")
        val h120sed = SED.fromJson(h120json)
        val startskap = h120sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(3, startskap?.size)
    }
}
