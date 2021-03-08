package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP3000noTest {

    @Test
    fun `create SED P3000_NO from json datafile`() {
        val p3000json = getTestJsonFile("P3000_NO-NAV.json")
        val p3000sed = SED.fromJson(p3000json)

        val json = p3000sed.toJson()
        JSONAssert.assertEquals(p3000json, json, false)

        assertEquals("6511", p3000sed.pensjon?.landspesifikk?.norge?.ufore?.barnInfo!!.get(0).etternavn)
        assertEquals("CZK", p3000sed.pensjon?.landspesifikk?.norge?.alderspensjon?.ektefelleInfo?.pensjonsmottaker!!.first().institusjonsopphold?.belop?.last()!!.valuta)
    }
}
