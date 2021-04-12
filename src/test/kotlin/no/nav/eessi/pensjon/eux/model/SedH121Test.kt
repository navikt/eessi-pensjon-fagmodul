package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedH121Test {

    @Test
    fun `compare SED H121 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121-NAV.json")
        val h121sed = SED.fromJson(h121json)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)
    }

    @Test
    fun `compare SED H121-2 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121_2-NAV.json")
        val h121sed = SED.fromJson(h121json)


        assertEquals("24234234234", h121sed.nav?.bruker?.person?.pin?.first()?.identifikator)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)
    }

    @Test
    fun `compare SED H121-3 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121_3-NAV.json")
        val h121sed = SED.fromJson(h121json)

        assertEquals("24234234234", h121sed.nav?.bruker?.person?.pin?.first()?.identifikator)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)
    }

    @Test
    fun `compare SED H121-4 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121_4-NAV.json")
        val h121sed = SED.fromJson(h121json)

        assertEquals("24234234234", h121sed.nav?.bruker?.person?.pin?.first()?.identifikator)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)
    }
}
