package no.nav.eessi.eessifagmodul.models

import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SedP14000Test : AbstractSedTest() {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP14000Test::class.java) }

    @Test
    fun `create SED P14000 from json datafile`() {

        val p14000json = getTestJsonFile("P14000-NAV.json")
        val p14000sed = getSEDfromTestfile(p14000json)

        val json = p14000sed.toJson()
        JSONAssert.assertEquals(p14000json, json, false)
    }
}
