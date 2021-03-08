package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.JsonIllegalArgumentException
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert

class SedP15000Test {

    @Test
    fun `compare SED P15000 to P15000 from json datafile`() {

        val p15000json = getTestJsonFile("P15000-NAV.json")
        val p15000sed = SED.fromJson(p15000json)

        val json = p15000sed.toJson()
        JSONAssert.assertEquals(p15000json, json, false)

        //hovedperson
        assertEquals("Mandag", p15000sed.nav?.bruker?.person?.fornavn)
        assertEquals(null, p15000sed.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("21811", p15000sed.nav?.bruker?.person?.foedested?.by)
        assertEquals("ingenerhjemme@online.no", p15000sed.nav?.bruker?.person?.kontakt?.email?.first()?.adresse)

        //
        assertEquals("2019-02-01", p15000sed.nav?.krav?.dato)
        assertEquals("01", p15000sed.nav?.krav?.type)
    }

    @Test
    fun `P15000 med element Sector feiler ved innlesing`() {
        val p15000json = getTestJsonFile("P15000-SectorFeiler-NAV.json")
        assertThrows<JsonIllegalArgumentException> {
            SED.fromJson(p15000json)
        }
    }
}
