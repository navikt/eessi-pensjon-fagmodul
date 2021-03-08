package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert


class SedP7000Test {

    @Test
    fun `create SED P7000 from json datafile`() {

        val p7000json = getTestJsonFile("P7000-NAV.json")
        val p7000sed = SED.fromJson(p7000json)

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
        val p7000sed = SED.fromJson(p7000json)

        val json = p7000sed.toJson()

        JSONAssert.assertEquals(p7000json, json, false)
    }
}
