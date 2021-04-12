package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Test

class SedP1100Test {

    @Test
    fun `compare SED P1100`() {
        val sedJson = getTestJsonFile("P1100-NAV.json")
        SED.fromJson(sedJson)
    }


}