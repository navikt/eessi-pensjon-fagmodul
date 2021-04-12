package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedX005Test {

    @Test
    fun `compare SED X005 to X005 from json datafile`() {

        val x005json = getTestJsonFile("X005-NAV.json")
        val x005sed = SED.fromJson(x005json)

        assertEquals("Duck", x005sed.nav?.sak?.kontekst?.bruker?.person?.etternavn)
        assertEquals("Dummy", x005sed.nav?.sak?.kontekst?.bruker?.person?.fornavn)
        assertEquals("1958-02-01", x005sed.nav?.sak?.kontekst?.bruker?.person?.foedselsdato)

        assertEquals("NO:NAVT002", x005sed.nav?.sak?.leggtilinstitusjon?.institusjon?.id)
        assertEquals("NAVT002", x005sed.nav?.sak?.leggtilinstitusjon?.institusjon?.navn)

        x005sed.toJsonSkipEmpty()

        val xprefill005json = getTestJsonFile("PrefillX005-NAV.json")
        val xprefill005sed = SED.fromJson(xprefill005json)

        assertEquals("POTET", xprefill005sed.nav?.sak?.kontekst?.bruker?.person?.etternavn)
        assertEquals("KRIMINELL", xprefill005sed.nav?.sak?.kontekst?.bruker?.person?.fornavn)
        assertEquals("1944-12-25", xprefill005sed.nav?.sak?.kontekst?.bruker?.person?.foedselsdato)

        assertEquals("NO:NAVT007", xprefill005sed.nav?.sak?.leggtilinstitusjon?.institusjon?.id)
        assertEquals("NAVT007", xprefill005sed.nav?.sak?.leggtilinstitusjon?.institusjon?.navn)
    }
}
