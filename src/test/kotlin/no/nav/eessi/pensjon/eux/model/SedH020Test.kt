package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SedH020Test {

    @Test
    fun `compare SED H020 from json datafile`() {
        val h020json = getTestJsonFile("horisontal/H020-A-NAV.json")
        val h020sed = SED.fromJson(h020json)

        assertNotNull(h020sed.nav?.bruker?.person?.pinland)
        assertEquals("01223123123", h020sed.nav?.bruker?.person?.pinland?.kompetenteuland)
    }


}
