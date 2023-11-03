package no.nav.eessi.pensjon.fagmodul.models

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.utils.eessiRequire
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException

class SedTypeTest {
    @Test
    fun `check for value on SedType`() {
        val px = "P3000_SE"
        val result = SedType.isValidSEDType(px)
        assertTrue(result)
    }

    @Test
    fun `check for uvalid SedType to prefill`() {
        val px = "P3000"
        val result = SedType.isValidSEDType(px)
        assertFalse(result)

        assertThrows<ResponseStatusException> {
            eessiRequire(SedType.isValidSEDType(px)) { "Kaste en tull melding under test"}
        }

    }

}
