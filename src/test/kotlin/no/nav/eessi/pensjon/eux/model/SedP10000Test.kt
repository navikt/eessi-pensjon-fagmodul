package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedP10000Test {

    @Test
    fun `compare SED P10000 to P10000 from json datafile`() {

        val p10000json = getTestJsonFile("P10000-03Barn-NAV.json")
        val p10000sed = SED.fromJson(p10000json)

        assertEquals("VAIKUNTHARAJAN-MASK",p10000sed.nav?.bruker?.person?.fornavn )
        assertEquals("samboer",p10000sed.nav?.bruker?.person?.sivilstand?.get(0)?.status)
        assertEquals("NSSI_TNT1, NATIONAL SOCIAL SECURITY INSTITUTE, BG",p10000sed.nav?.eessisak?.get(0)?.institusjonsnavn)
    }
}
