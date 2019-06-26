package no.nav.eessi.eessifagmodul.utils

import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals


class JsonUtilsTest {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JsonUtilsTest::class.java) }

    @Test
    fun testJsonmapingList() {
        val json = "[\n" +
                "    \"01065201794___varsler___23916815___2019-02-28T13:41:41.714.json\",\n" +
                "    \"01065201794___varsler___23916815___2019-03-01T10:16:47.519.json\",\n" +
                "    \"01065201794___varsler___23916815___2019-03-01T10:39:29.285.json\",\n" +
                "    \"01065201794___varsler___23916815___2019-03-01T12:43:35.886.json\",\n" +
                "    \"01065201794___varsler___23916815___2019-03-01T13:26:23.529.json\",\n" +
                "    \"01065201794___varsler___23916815___2019-03-01T17:01:46.49.json\",\n" +
                "    \"01065201794___varsler___23916815___2019-03-06T12:54:51.011.json\",\n" +
                "    \"01065201794___varsler___23916815___2019-03-06T13:04:58.642.json\",\n" +
                "    \"01065201794___varsler___23917355___2019-03-06T12:06:58.642.json\",\n" +
                "    \"01065201794___varsler___23917355___2019-03-06T12:04:58.642.json\"\n" +
                "]"
        val varsler = mapJsonToAny(json, typeRefs<List<String>>())


        val aktivesakid = hentSisteSakIdFraVarsel(varsler)

        assertEquals("23916815", aktivesakid)

    }

    fun hentSisteSakIdFraVarsel(list: List<String>): String {
        data class Varsel(
                val fnr: String,
                val type: String,
                val sakId: String,
                val timestamp: LocalDateTime
        )

        val varsellist = mutableListOf<Varsel>()
        list.forEach { varsler ->
            val data = varsler.dropLast(5).split("___")
            val varsel = Varsel(
                    fnr = data.get(0),
                    type = data.get(1),
                    sakId = data.get(2),
                    timestamp = LocalDateTime.parse(data.get(3), DateTimeFormatter.ISO_DATE_TIME)
            )
            varsellist.add(varsel)
        }
        return varsellist.asSequence().sortedBy { (_, _, _, sorting) -> sorting }.toList().last().sakId
    }

    @Test
    fun testMapDatatoMap() {

        val testData = InstitusjonItem(
                institution = "Nav",
                country = "No"
        )

        val result = datatClazzToMap(testData)

        assertEquals(3, result.size)
        assertEquals("No", result["country"])

    }

}