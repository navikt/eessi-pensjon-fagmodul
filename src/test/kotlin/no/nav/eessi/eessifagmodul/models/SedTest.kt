package no.nav.eessi.eessifagmodul.models

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SedTest {

    val logger: Logger by lazy { LoggerFactory.getLogger(SedTest::class.java) }

    @Test
    fun createP6000sed() {
        val sed6000 = SedMock().genererP6000Mock()
        assertNotNull(sed6000)

        val json = sed6000.toJson()
        //map json back to vedtak obj
        val pensjondata = SED.fromJson(json)
        assertNotNull(pensjondata)
        assertEquals(sed6000, pensjondata)

        //map load vedtak-NAV refrence
        val path = Paths.get("src/test/resources/json/nav/P6000-NAV.json")
        val p6000file = String(Files.readAllBytes(path))
        assertNotNull(p6000file)
        validateJson(p6000file)

        //map vedtak-NAV back to vedtak object.
        val pensjondataFile = SED.fromJson(p6000file)
        assertNotNull(pensjondataFile)

    }

    @Test
    fun `create part json to object`() {
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
        val testPersjson = mapAnyToJson(sed, true)
        assertNotNull(testPersjson)

    }

}