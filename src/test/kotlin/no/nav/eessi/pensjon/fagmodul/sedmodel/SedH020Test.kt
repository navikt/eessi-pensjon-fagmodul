package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedH020Test {

    @Test
    fun `compare SED H020 from json datafile`() {
        val h020json = getTestJsonFile("horisontal/H020-A-NAV.json")
        val h020sed = SED.fromJson(h020json)

        assertNotNull(h020sed.nav?.bruker?.person?.pinland)
        assertEquals("01223123123", h020sed.nav?.bruker?.person?.pinland?.kompetenteuland)

        JSONAssert.assertEquals(h020json, h020sed.toJson(), false)
    }


}
