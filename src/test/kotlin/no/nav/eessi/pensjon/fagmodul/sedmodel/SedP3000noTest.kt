package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Test

class SedP3000noTest {

    @Test
    fun `create SED P3000_NO from json datafile`() {
        val p3000json = getTestJsonFile("P3000_NO-NAV.json")
        val p3000sed = SED.fromJson(p3000json)

        p3000sed.toJson()
    }
}
