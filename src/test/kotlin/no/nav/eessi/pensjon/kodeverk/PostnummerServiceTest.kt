package no.nav.eessi.pensjon.kodeverk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PostnummerServiceTest {

    private lateinit var service: PostnummerService

    @BeforeEach
    fun setup() {
        service = PostnummerService()
    }

    @ParameterizedTest(name = "Henter postkode: {0}, for poststed: {1}")
    @MethodSource("poststedMedpostKode")
    fun `hente postnr med gyldig sted `(postKode: String, poststed: String) {

        val sted = service.finnPoststed(postKode)
        assertNotNull(sted)
        assertEquals(poststed, sted)
    }

    @Test
    fun `hente postnr med ugyldig sted`() {
        val sted = service.finnPoststed("1439")
        assertNull(sted)
    }

    private companion object {
        @JvmStatic
        fun poststedMedpostKode() = Stream.of(
            Arguments.of("1430", "ÅS"),
            Arguments.of("1424", "SKI"),
            Arguments.of("9930", "NEIDEN"),
            Arguments.of("4198", "FOLDØY"),
        )
    }
}
