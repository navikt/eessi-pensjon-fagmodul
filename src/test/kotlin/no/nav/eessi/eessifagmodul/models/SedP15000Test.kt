package no.nav.eessi.eessifagmodul.models

import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class SedP15000Test : AbstractSedTest() {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP5000Test::class.java) }

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