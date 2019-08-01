package no.nav.eessi.pensjon.fagmodul.pesys.mockup

import no.nav.eessi.pensjon.fagmodul.sedmodel.*
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

    fun mockP3000NO(pensjonsgrad: String? = null): SED {
        val p3000no = SED("P3000_NO")
        p3000no.pensjon = Pensjon(
                landspesifikk = Landspesifikk(
                        norge = Norge(
                                alderspensjon = Alderspensjon(
                                        pensjonsgrad = pensjonsgrad
                                )
                        )
                )

        )
        return p3000no
    }

    fun mockP4000(): SED {
        return try {
            val p4000file = this.javaClass.getResource("/mockPesys/P4000-NAV.json").readText()
            val p4000 = SED.fromJson(p4000file)

            logger.info("p4000 mocket og legges til")

            p4000
        } catch (ex: Exception) {
            logger.error(ex.message)
            return SED("P4000")
        }

    }
}
