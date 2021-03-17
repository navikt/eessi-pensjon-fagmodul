package no.nav.eessi.pensjon.fagmodul.sedmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class SedP8000Test {

    @Test
    fun `create SED P8000 from json datafile`() {
        val p8000json = getTestJsonFile("P8000-NAV.json")
        val p8000sed = SED.fromJson(p8000json)

        assertEquals("02", p8000sed.pensjon?.anmodning?.referanseTilPerson)
    }
}
