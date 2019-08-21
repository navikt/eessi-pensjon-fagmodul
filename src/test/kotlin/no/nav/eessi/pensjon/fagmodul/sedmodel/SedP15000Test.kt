package no.nav.eessi.pensjon.fagmodul.sedmodel

import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.junit.Assert.assertEquals

class SedP15000Test {

    @Test
    fun `compare SED P15000 to P15000 from json datafile`() {

        val p15000json = getTestJsonFile("P15000-NAV.json")
        val p15000sed = getSEDfromTestfile(p15000json)

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
}
