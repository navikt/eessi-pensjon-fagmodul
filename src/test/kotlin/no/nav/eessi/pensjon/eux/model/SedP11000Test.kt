package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Test

class SedP11000Test {

    @Test
    fun `compare SED P11000`() {
        val sedJson = getTestJsonFile("P11000_Fixed-NAV.json")
        SED.fromJson(sedJson)
    }


}