package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Paths

fun getTestJsonFile(filename: String): String {
    val filepath = "src/test/resources/json/nav/${filename}"
    val json = String(Files.readAllBytes(Paths.get(filepath)))
    assertTrue(validateJson(json))
    return json
}
