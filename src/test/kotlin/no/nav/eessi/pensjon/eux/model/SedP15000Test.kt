package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedP15000Test {

    @Test
    fun `compare SED P15000 to P15000 from json datafile`() {

        val p15000json = getTestJsonFile("P15000-NAV.json")
        val p15000sed = SED.fromJson(p15000json)

        p15000sed.toJson()

        //hovedperson
        assertEquals("Mandag", p15000sed.nav?.bruker?.person?.fornavn)
        assertEquals(null, p15000sed.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("21811", p15000sed.nav?.bruker?.person?.foedested?.by)
        assertEquals("2019-02-01", p15000sed.nav?.krav?.dato)
        assertEquals("01", p15000sed.nav?.krav?.type)
    }
}
