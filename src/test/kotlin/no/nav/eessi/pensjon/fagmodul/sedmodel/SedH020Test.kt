package no.nav.eessi.pensjon.fagmodul.sedmodel

import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedH020Test {

    @Test
    fun `compare SED H020 from json datafile`() {
        val h020json = getTestJsonFile("horisontal/H020-NAV.json")
        val h020sed = getHSEDfromTestfile(h020json)

        JSONAssert.assertEquals(h020json, h020sed.toString(), false)
    }
}
