package no.nav.eessi.eessifagmodul.pesys.mockup

import no.nav.eessi.eessifagmodul.models.*
import org.slf4j.LoggerFactory

class MockSED001 {

    private val logger = LoggerFactory.getLogger(MockSED001::class.java)

    fun mockP2000(): SED {
        val p2000 = SED("P2000")
        p2000.nav = Nav(
                bruker = Bruker(
                        person = Person(
                                pin = listOf(PinItem(
                                        sektor = "Pensjon",
                                        identifikator = "410155012341",
                                        land = "SE"
                                )),
                                statsborgerskap = listOf(
                                        StatsborgerskapItem(
                                                land = "SE"
                                        )
                                )
                        )
                ),
                krav = Krav(dato = "2019-03-11")
        )
        return p2000
    }

    fun mockP3000NO(): SED {
        val p3000no = SED("P3000_NO")
        p3000no.pensjon = Pensjon(
                landspesifikk = Landspesifikk(
                        norge = Norge(
                                alderspensjon = Alderspensjon(
                                        pensjonsgrad = "03"
                                )
                        )
                )

        )
        return p3000no
    }

    fun mockP4000(): SED {
        try {
            val p4000file = this.javaClass.getResource("/mockPesys/P4000-NAV.json").readText()

            //val json = mapAnyToJson(p4000sed, true)
            val p4000 = SED.fromJson(p4000file)

            logger.info("p4000 mocket og legges til")

            return p4000
        } catch (ex: Exception) {
            logger.error(ex.message)
            return SED.create("P4000")
        }

    }

    fun mockP5000(): SED {
        //hvor kommer denne seden ifra? orginal.. p5000

        val p5000 = SED("P5000")

        p5000.pensjon = Pensjon(

        )

        return p5000
    }


}
