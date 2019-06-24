package no.nav.eessi.eessifagmodul.models

import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals


class SedP7000Test : AbstractSedTest() {
    val logger: Logger by lazy { LoggerFactory.getLogger(SedP7000Test::class.java) }

    @Test
    fun `create SED P7000 from json datafile`() {

        val p7000json = getTestJsonFile("P7000-NAV.json")
        val p7000sed = getSEDfromTestfile(p7000json)

        val json = p7000sed.toJson()
        JSONAssert.assertEquals(p7000json, json, false)

        assertEquals("daglig4166", p7000sed.pensjon?.ytelser?.get(0)?.beloep?.get(0)?.annenbetalingshyppighetytelse)
        assertEquals("5.1.5.2.1. Adressat for revurderingen Repetetive", p7000sed.pensjon?.samletVedtak?.avslag?.get(0)?.adresse)

        assertEquals("1942-12-19", p7000sed.pensjon?.ytelser?.get(0)?.startdatoutbetaling)
        assertEquals("4163", p7000sed.pensjon?.vedtak?.get(0)?.beregning?.get(0)?.beloepBrutto?.beloep)
    }

    @Test
    fun `create SED P7000_2 from json datafile`() {

        val p7000json = getTestJsonFile("P7000_2-NAV_v4_1.json")
        val p7000sed = getSEDfromTestfile(p7000json)

        logger.info(p7000json)
        logger.info(p7000sed.toString())

        val json = p7000sed.toJson()

        JSONAssert.assertEquals(p7000json, json, false)

    }
}