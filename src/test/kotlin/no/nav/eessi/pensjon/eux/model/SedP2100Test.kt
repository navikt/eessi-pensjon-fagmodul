package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Test

class SedP2100Test {

    @Test
    fun `create SED P2100 from json datafile`() {

        val p2100json = getTestJsonFile("P2100-PinDK-NAV.json")
        SED.fromJson(p2100json)
    }
}
