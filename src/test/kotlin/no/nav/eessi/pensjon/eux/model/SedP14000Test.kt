package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Test

class SedP14000Test {

    @Test
    fun `create SED P14000 from json datafile`() {

        val p14000json = getTestJsonFile("P14000-NAV.json")
        val p14000sed = SED.fromJson(p14000json)

        p14000sed.toJson()
    }
}
