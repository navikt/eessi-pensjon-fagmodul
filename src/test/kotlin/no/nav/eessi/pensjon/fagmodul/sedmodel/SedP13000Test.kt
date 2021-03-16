package no.nav.eessi.pensjon.fagmodul.sedmodel

import org.junit.jupiter.api.Test

class SedP13000Test {

    @Test
    fun `compare SED P13000`() {
        val sedJson = getTestJsonFile("P13000-NAV.json")
        SED.fromJson(sedJson)
    }
}