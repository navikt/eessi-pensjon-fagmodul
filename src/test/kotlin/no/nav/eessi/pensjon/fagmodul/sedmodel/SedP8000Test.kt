package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert


class SedP8000Test {

    @Test
    fun `create SED P8000 from json datafile`() {
        val p8000json = getTestJsonFile("P8000-NAV.json")
        val p8000sed = SED.fromJson(p8000json)

        val json = p8000sed.toJson()
        JSONAssert.assertEquals(p8000json, json, false)

        assertEquals("Her kommer tekst ad Begrunnelse for kravet", p8000sed.pensjon?.anmodning?.informasjon?.begrunnelseKrav)

        assertEquals("Begrunnelse for kravet", p8000sed.pensjon?.anmodning?.begrunnKrav)
    }
}
