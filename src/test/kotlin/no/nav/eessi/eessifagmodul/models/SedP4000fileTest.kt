package no.nav.eessi.eessifagmodul.models

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertNotNull

class SedP4000fileTest : AbstractSedTest() {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP4000fileTest::class.java) }

    @Test
    fun `validate P4000 to json and back`() {
        val p4000json = getTestJsonFile("P4000-NAV.json")
        val p4000sed = getSEDfromTestfile(p4000json)

        val json = mapAnyToJson(p4000sed, true)
        val pensjondata = mapJsonToAny(json, typeRefs<SED>())
        assertNotNull(pensjondata)
        JSONAssert.assertEquals(p4000json, json, false)
    }
}