package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP2200Test {

    lateinit var p2200json: String
    lateinit var p2200sed: SED

    @BeforeEach
    fun bringItOn() {
        p2200json = getTestJsonFile("P2000-NAV.json")
        p2200sed = SED.fromJson(p2200json)
    }

    @Test
    fun `create SED P2200 from mockData`() {

        val p2200 =  SedMock().genererP2000Mock()
        assertNotNull(p2200)

        val p2200json = mapAnyToJson(p2200, true)
        assertNotNull(p2200json)

        val p2200back = mapJsonToAny(p2200json, typeRefs<SED>())
        assertEquals(p2200, p2200back)

    }

    @Test
    fun `create SED P2200 from json datafile`() {

        val p2200sed = mapJsonToAny(p2200json, typeRefs<SED>(), true)
        assertNotNull(p2200sed)
        assertEquals(SED::class.java, p2200sed::class.java)

    }

    @Test
    fun `create SED P2200 from json to nav-sed back to json validate`() {
        assertNotNull(p2200sed)
        val json = mapAnyToJson(p2200sed, true)
        JSONAssert.assertEquals(p2200json, json, false)

    }

}
