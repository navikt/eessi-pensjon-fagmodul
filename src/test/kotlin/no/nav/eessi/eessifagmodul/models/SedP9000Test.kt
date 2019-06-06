package no.nav.eessi.eessifagmodul.models

import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class SedP9000Test : AbstractSedTest() {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP5000Test::class.java) }

    @Test
    fun `compare SED P9000 to P9000 from json datafile`() {

        val p9000json = getTestJsonFile("P9000-NAV.json")
        val p9000sed = getSEDfromTestfile(p9000json)

        val json = p9000sed.toJson()
        JSONAssert.assertEquals(p9000json, json, false)


        //hovedperson
        assertEquals("Raus 212", p9000sed.nav?.bruker?.person?.fornavn)
        assertEquals("NO2082760100435", p9000sed.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("levanger 21811", p9000sed.nav?.bruker?.person?.foedested?.by)


        //annenperson
        assertEquals("Rausa 322", p9000sed?.nav?.annenperson?.person?.fornavn)
        assertEquals("ingen@online.no", p9000sed?.nav?.annenperson?.person?.kontakt?.email?.first()?.adresse)
        assertEquals("0101010202022 327112", p9000sed.nav?.annenperson?.person?.pin?.first()?.identifikator)

        //tillegginfo
        //01 = hovedperson, 02 = annenperson
        assertEquals("01", p9000sed.pensjon?.tilleggsinformasjon?.p8000?.henvisningperson)
        assertEquals("ytterligere info 21.1.", p9000sed.pensjon?.tilleggsinformasjon?.yrkesaktivitet?.tilleggsopplysning)

        //
        assertEquals("09041984799 91", p9000sed.nav?.annenperson?.person?.pinannen?.identifikator)


    }
}