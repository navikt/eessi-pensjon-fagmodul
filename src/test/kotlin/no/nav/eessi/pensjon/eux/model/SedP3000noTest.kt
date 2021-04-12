package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SedP3000noTest {

    @Test
    fun `create SED P3000_NO from json datafile`() {
        val p3000json = getTestJsonFile("P3000_NO-NAV.json")
        val p3000sed = SED.fromJson(p3000json)

        p3000sed.toJson()

        assertEquals("6511", p3000sed.pensjon?.landspesifikk?.norge?.ufore?.barnInfo!!.get(0).etternavn)
        assertEquals("CZK", p3000sed.pensjon?.landspesifikk?.norge?.alderspensjon?.ektefelleInfo?.pensjonsmottaker!!.first().institusjonsopphold?.belop?.last()!!.valuta)
    }
}
