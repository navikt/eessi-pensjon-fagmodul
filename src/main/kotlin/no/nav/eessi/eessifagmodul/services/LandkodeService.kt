package no.nav.eessi.eessifagmodul.services

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*


@Service
class LandkodeService {

    private val logger: Logger by lazy { LoggerFactory.getLogger(LandkodeService::class.java) }
    private val landKodeTable: MutableMap<String?, Landkode>

    init {
        landKodeTable = HashMap()
        val `in` = this.javaClass.getResourceAsStream(FILENAME)
        val br = BufferedReader(InputStreamReader(`in`, "UTF-8"))
        var line: String? = ""
        val csvSplitBy = ";"

        while (line != null) {
            line = br.readLine()
            if (line != null) {
                val landArray = line.split(csvSplitBy.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (landArray[0].length != 2 || landArray[1].length != 3 || landArray[2].length < 2) {
                    continue
                }
                val data = Landkode(landArray[0], landArray[1], landArray[2], landArray[3])
                landKodeTable[data.alpha3] = data
                landKodeTable[data.alpha2] = data
            }
        }
        logger.info("Har importert kodeverk fra $FILENAME")
    }

    fun finnLandkode3(alpha2: String): String? {
        return if (landKodeTable[alpha2] == null) {
            logger.error("Finner ikke landkode for $alpha2, sjekk om ny landkoder.txt må lastes ned.")
            null
        } else {
            landKodeTable[alpha2]?.alpha3
        }
    }

    fun hentLandkode2(): List<String> {
        val listsort = hentLandkoder()
        val list: MutableList<String> = mutableListOf()
        listsort.forEach {
            list.add(it.alpha2!!)
        }
        return list
    }

    private fun hentLandkoder(): List<Landkode> {
        val landlist = mutableListOf<Landkode>()
        landKodeTable.keys.forEach {
            if (it?.length == 2) {
                landlist.add(landKodeTable[it]!!)
            }
        }
        return landlist.asSequence().sortedBy { (_, _, _, sorting) -> sorting }.toList()
    }

    fun hentAlleLandkoder(): String {
        val listsort = hentLandkoder()
        return mapAnyToJson(listsort)
    }

    fun finnLandkode2(alpha3: String): String? {
        return if (landKodeTable[alpha3] == null) {
            logger.error("Finner ikke landkode for $alpha3, sjekk om ny landkoder.txt må lastes ned.")
            null
        } else {
            landKodeTable[alpha3]?.alpha2
        }
    }

    private companion object {
        private const val FILENAME = "/kodeverk/landkoder.txt"
    }

    private data class Landkode(
            val alpha2: String? = null,
            val alpha3: String? = null,
            val land: String? = null,
            val sorting: String? = null
    )


}