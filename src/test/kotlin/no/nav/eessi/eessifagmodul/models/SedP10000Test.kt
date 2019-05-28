package no.nav.eessi.eessifagmodul.models

import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class SedP10000Test : AbstractSedTest() {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP5000Test::class.java) }

    @Test
    fun `compare SED P10000 to P10000 from json datafile`() {

        val p10000json = getTestJsonFile("P10000-NAV.json")
        val p10000sed = getSEDfromTestfile(p10000json)

        val json = p10000sed.toJson()
        JSONAssert.assertEquals(p10000json, json, false)

        assertEquals("Additional information textfield 20.1", p10000sed.pensjon?.merinformasjon?.tilleggsinformasjon)

        assertEquals("2019-02-13",  p10000sed.pensjon?.merinformasjon?.livdoedinfo?.dodsdato)

        assertEquals("2010-11-18", p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.datotyper?.sluttdatorettytelse)
        assertEquals( "source of income textfield", p10000sed.pensjon?.merinformasjon?.infoinntekt?.get(0)?.inntektskilde)

        assertEquals("1998-09-11",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.ytelsesdatoer?.datotyper?.startdatoforstansytelse )
        assertEquals("2013-09-15",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.ytelsesdatoer?.datotyper?.datokravytelse)
        assertEquals("1997-09-11",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.ytelsesdatoer?.datotyper?.startdatoredusertytelse)
        assertEquals("1997-08-03",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.ytelsesdatoer?.datotyper?.sluttdatoutbetaling)

        assertEquals("1998-09-16",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.ytelsesdatoer?.datotyper?.sluttdatoforstansiytelser)
        assertEquals("1999-08-07",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.ytelsesdatoer?.datotyper?.startdatoutbetaling)
        assertEquals("2003-11-19",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.ytelsesdatoer?.datotyper?.startdatoretttilytelser)


        assertEquals("23000",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.beloep?.get(0)?.beloep)
        assertEquals("2004-11-16",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.beloep?.get(0)?.gjeldendesiden)
        assertEquals("ISK",p10000sed.pensjon?.merinformasjon?.ytelser?.get(0)?.beloep?.get(0)?.valuta)

        assertEquals("VAIKUNTHARAJAN-MASK",p10000sed.nav?.bruker?.person?.fornavn )
        assertEquals("samboer",p10000sed.nav?.bruker?.person?.sivilstand?.get(0)?.status)
        assertEquals("NSSI_TNT1, NATIONAL SOCIAL SECURITY INSTITUTE, BG",p10000sed.nav?.eessisak?.get(0)?.institusjonsnavn)
    }
}