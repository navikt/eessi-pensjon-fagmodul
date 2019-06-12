package no.nav.eessi.eessifagmodul.models

import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class SedX005Test : AbstractSedTest() {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP5000Test::class.java) }

    @Test
    fun `compare SED X005 to X005 from json datafile`() {

        val x005json = getTestJsonFile("PrefillX005-NAV.json")
        val x005sed = getSEDfromTestfile(x005json)

        val json = x005sed.toJson()
        JSONAssert.assertEquals(x005json, json, false)


        val jsonsmall = x005sed.toJsonSkipEmpty()
        println(jsonsmall)


    }
}