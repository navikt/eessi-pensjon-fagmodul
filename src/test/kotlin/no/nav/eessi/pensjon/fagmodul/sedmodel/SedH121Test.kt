package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedH121Test {

    @Test
    fun `compare SED H121 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121-NAV.json")
        val h121sed = SED.fromJson(h121json)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)

        val horisontal = h121sed.horisontal
        assertEquals("1233", horisontal?.anmodningmedisinskinformasjon?.svar?.undersoekelse?.estimat?.kostnader?.beloep)

        JSONAssert.assertEquals(h121json, h121sed.toJsonSkipEmpty(), false)

    }

    @Test
    fun `compare SED H121-2 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121_2-NAV.json")
        val h121sed = SED.fromJson(h121json)


        assertEquals("24234234234", h121sed.nav?.bruker?.person?.pin?.first()?.identifikator)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)

        val horisontal = h121sed.horisontal
        assertEquals("ble_ikke_utført_av_følgende_grunn", horisontal?.anmodningmedisinskinformasjon?.svar?.medisinsk?.undersoekelse?.type)

        assertEquals("annet", horisontal?.anmodningmedisinskinformasjon?.svar?.medisinsk?.undersoekelse?.ikkegjennomfoert?.grunn?.type)


        JSONAssert.assertEquals(h121json, h121sed.toJsonSkipEmpty(), false)

    }

    @Test
    fun `compare SED H121-3 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121_3-NAV.json")
        val h121sed = SED.fromJson(h121json)

        assertEquals("24234234234", h121sed.nav?.bruker?.person?.pin?.first()?.identifikator)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)

        val horisontal = h121sed.horisontal
        assertEquals("gsfdg fdsgdgsdfgsdfg", horisontal?.anmodningmedisinskinformasjon?.svar?.dokumentasjonikkevedlagt?.grunn )
        assertEquals("ja", horisontal?.anmodningmedisinskinformasjon?.svar?.erdokumentasjonsvedlagt)

        JSONAssert.assertEquals(h121json, h121sed.toJsonSkipEmpty(), false)

    }

    @Test
    fun `compare SED H121-4 from json datafile`() {

        val h121json = getTestJsonFile("horisontal/H121_4-NAV.json")
        val h121sed = SED.fromJson(h121json)

        assertEquals("24234234234", h121sed.nav?.bruker?.person?.pin?.first()?.identifikator)

        val startskap = h121sed.nav?.bruker?.person?.statsborgerskap
        assertEquals(2, startskap?.size)

        val horisontal = h121sed.horisontal
        //assertEquals("null", horisontal?.anmodningmedisinskinformasjon?.svar?.dokumentasjonikkevedlagt?.grunn )

        val typelist = horisontal?.anmodningmedisinskinformasjon?.svar?.medisinsk?.informasjon?.type
        assertEquals(3, typelist?.size)

        assertEquals("asdd sadsd fsdfs", horisontal?.anmodningmedisinskinformasjon?.svar?.medisinsk?.informasjon?.annen)

        JSONAssert.assertEquals(h121json, h121sed.toJsonSkipEmpty(), false)

    }


}
