package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP12000Test {

    @Test
    fun `compare SED P12000`() {
        val sedJson = getTestJsonFile("P12000-NAV.json")
        val p12000sed = SED.fromJson(sedJson)

        Assertions.assertEquals(null, p12000sed.pensjon?.pensjoninfotillegg?.opphoraarsak)
        Assertions.assertEquals(null, p12000sed.pensjon?.pensjoninfotillegg?.endret?.get(0)?.belopforendring)

        Assertions.assertEquals("3200", p12000sed.pensjon?.pensjoninfo?.get(0)?.betalingsdetaljer?.arbeidstotal)
        Assertions.assertEquals("25301", p12000sed.pensjon?.pensjoninfo?.get(0)?.betalingsdetaljer?.belop)
        Assertions.assertEquals("SEK", p12000sed.pensjon?.pensjoninfo?.get(0)?.betalingsdetaljer?.valuta)

        Assertions.assertEquals("1", p12000sed.pensjon?.anmodning13000verdi)

        JSONAssert.assertEquals(sedJson, p12000sed.toJsonSkipEmpty(), false)
    }


}