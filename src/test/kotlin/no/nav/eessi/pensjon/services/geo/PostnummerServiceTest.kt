package no.nav.eessi.pensjon.services.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class PostnummerServiceTest {

    private lateinit var service: PostnummerService

    @BeforeEach
    fun setup() {
        service = PostnummerService()
    }

    @Test
    fun `hente postnr med gyldig sted`() {

        val sted = service.finnPoststed("1430")
        assertNotNull(sted)
        assertEquals("ÅS", sted)
    }

    @Test
    fun `hente postnr med ugyldig sted`() {
        val sted = service.finnPoststed("1439")
        assertNull(sted)
    }

    @Test
    fun `hente poststed for 1424`() {
        val sted = service.finnPoststed("1424")
        assertNotNull(sted)
        assertEquals("SKI", sted)
    }

    @Test
    fun `hente poststed for 9930`() {
        val sted = service.finnPoststed("9930")
        assertNotNull(sted)
        assertEquals("NEIDEN", sted)
    }

    @Test
    fun `hente poststed for 4198`() {
        val sted = service.finnPoststed("4198")
        assertNotNull(sted)
        assertEquals("FOLDØY", sted)
    }
}
