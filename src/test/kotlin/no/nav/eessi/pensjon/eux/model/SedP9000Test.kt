package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedP9000Test {

    @Test
    fun `compare SED P9000 to P9000 from json datafile`() {

        val p9000json = getTestJsonFile("P9000-NAV.json")
        val p9000sed = SED.fromJson(p9000json)

        //hovedperson
        assertEquals("Raus 212", p9000sed.nav?.bruker?.person?.fornavn)
        assertEquals("NO2082760100435", p9000sed.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("levanger 21811", p9000sed.nav?.bruker?.person?.foedested?.by)


        //annenperson
        assertEquals("Rausa 322", p9000sed.nav?.annenperson?.person?.fornavn)
        assertEquals("0101010202022 327112", p9000sed.nav?.annenperson?.person?.pin?.first()?.identifikator)
    }
}
