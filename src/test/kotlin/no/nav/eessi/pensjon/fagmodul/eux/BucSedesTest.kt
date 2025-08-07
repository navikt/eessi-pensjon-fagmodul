package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BucSedesTest {


    @Test
    fun `Buc Serdes`() {
        val serialized = javaClass.getResource("/json/buc/Buc.json")!!.readText()

        val buc = mapJsonToAny<Buc>(serialized, false)
        assertEquals("1443996", buc.id)
        assertEquals("P_BUC_01", buc.processDefinitionName)
        assertEquals("f67d8aea3a75469e82b85ff5c80eb81a", buc.internationalId)

    }
}