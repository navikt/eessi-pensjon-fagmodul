package no.nav.eessi.eessifagmodul.models

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import org.junit.Before
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SedP2200Test : AbstractSedTest() {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP2200Test::class.java) }

    lateinit var p2200json: String
    lateinit var p2200sed: SED

    @Test
    fun `create SED P2200 from mockData`() {

        val p2200 =  SedMock().genererP2000Mock()
        assertNotNull(p2200)

        val p2200json = mapAnyToJson(p2200, true)
        assertNotNull(p2200json)

        val p2200back = mapJsonToAny(p2200json, typeRefs<SED>())
        assertEquals(p2200, p2200back)

    }

    @Before
    fun bringItOn() {
        p2200json = getTestJsonFile("P2000-NAV.json")
        p2200sed = getSEDfromTestfile(p2200json)
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