package no.nav.eessi.eessifagmodul.models

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SedP6000Test {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP6000Test::class.java) }

    @Test
    fun createP6000sed() {
        val sed6000 = SedMock().genererP6000Mock()
        assertNotNull(sed6000)


        val json = sed6000.toJson()
        assertNotNull(json)

        val pensjondata = SED.fromJson(json)
        assertNotNull(pensjondata)
        assertEquals(sed6000, pensjondata)

        val path = Paths.get("src/test/resources/json/nav/P6000-NAV.json")
        val p6000file = String(Files.readAllBytes(path))
        assertNotNull(p6000file)
        validateJson(p6000file)

        //map vedtak-NAV back to vedtak object.
        val pensjondataFile = mapJsonToAny(p6000file, typeRefs<SED>())

        assertNotNull(pensjondataFile)
        val jsonnav = mapAnyToJson(pensjondataFile, true)
        JSONAssert.assertEquals(p6000file, jsonnav, false)
    }

    @Test
    fun `create part json and validate to object`() {
        val sed6000 = SedMock().genererP6000Mock()
        assertNotNull(sed6000)

        val bruker = sed6000.nav!!.bruker!!
        val brukerback = mapJsonToAny(mapAnyToJson(bruker), typeRefs<Bruker>())
        assertNotNull(brukerback)
        assertEquals(bruker, brukerback)

        val sed = SED.create("vedtak")
        val navmock = NavMock().genererNavMock()
        sed.nav = Nav(bruker = navmock.bruker)
        val penmock = PensjonMock().genererMockData()
        sed.pensjon = Pensjon(gjenlevende = penmock.gjenlevende)
    }
}