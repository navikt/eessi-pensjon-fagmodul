package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class SedP8000Test {

    @Test
    fun `create SED P8000 from json datafile`() {
        val p8000json = getTestJsonFile("P8000-NAV.json")
        val p8000sed = mapJsonToAny(p8000json, typeRefs<P8000>())

        assertEquals("02", p8000sed.p8000Pensjon?.anmodning?.referanseTilPerson)
    }
}
