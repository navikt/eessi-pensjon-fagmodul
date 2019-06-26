package no.nav.eessi.eessifagmodul.models

import no.nav.eessi.eessifagmodul.utils.validateJson
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertTrue

abstract class AbstractSedTest {

    fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/json/nav/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }

    fun getSEDfromTestfile(json: String): SED {
        return SED.fromJson(json)
    }

    fun getSED(filename: String): SED {
        return getSEDfromTestfile(getTestJsonFile(filename))
    }

}