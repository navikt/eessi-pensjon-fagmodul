package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedH021Test {

    @Test
    fun `compare SED H021 from json datafile`() {
        val h021json = getTestJsonFile("horisontal/H021-A-NAV.json")
        val h021sed = SED.fromJson(h021json)

        Assertions.assertNotNull(h021sed.nav?.bruker?.person?.pinland)
        Assertions.assertEquals("213421412414214", h021sed.nav?.bruker?.person?.pinland?.kompetenteuland)

        JSONAssert.assertEquals(h021json, h021sed.toJson(), false)
    }

}
