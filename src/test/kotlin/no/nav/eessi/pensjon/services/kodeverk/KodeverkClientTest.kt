package no.nav.eessi.pensjon.services.kodeverk

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Stream

class KodeverkClientTest {

    private val mockrestTemplate: RestTemplate = mockk()

    private lateinit var kodeverkClient: KodeverkClient

    @BeforeEach
    fun setup() {
        kodeverkClient = KodeverkClient(mockrestTemplate, "eessi-fagmodul")
        kodeverkClient.initMetrics()

        val mockResponseEntityISO3 = createResponseEntityFromJsonFile("src/test/resources/json/kodeverk/landkoderSammensattIso2.json")

        every { mockrestTemplate
            .exchange(
                eq("/api/v1/hierarki/LandkoderSammensattISO2/noder"),
                any(),
                any<HttpEntity<Unit>>(),
                eq(String::class.java)) } returns mockResponseEntityISO3
    }

    @ParameterizedTest(name = "Henter landkode: {0}. Forventet svar: {1}")
    @MethodSource("landkoder")
    fun `henting av landkode ved bruk av landkode `(expected: String, landkode: String) {

        val actual = kodeverkClient.finnLandkode(landkode)

        assertEquals(expected, actual)
    }

    private companion object {
        @JvmStatic
        fun landkoder() = Stream.of(
            Arguments.of("SE", "SWE"), // landkode 2
            Arguments.of("BMU", "BM"), // landkode 3
            Arguments.of("ALB", "AL"), // landkode 3
        )
    }

    @Test
    fun testerLankodeMed2Siffer() {
        val actual = kodeverkClient.hentLandkoderAlpha2()

        assertEquals("ZW", actual.last())
        assertEquals(249, actual.size)
    }

    @Test
    fun henteAlleLandkoderReturnererAlleLandkoder() {
        val json = kodeverkClient.hentAlleLandkoder()

        val list = mapJsonToAny(json, typeRefs<List<Landkode>>())

        assertEquals(249, list.size)

        assertEquals("AD", list.first().landkode2)
        assertEquals("AND", list.first().landkode3)
    }

    @Test
    fun hentingavIso2landkodevedbrukAvlandkode3FeilerMedNull() {
        val landkode2 = "BMUL"

        val exception  = assertThrows<LandkodeException> {
                 kodeverkClient.finnLandkode(landkode2)

        }
        assertEquals("400 BAD_REQUEST \"Ugyldig landkode: BMUL\"", exception.message)
    }

    private fun createResponseEntityFromJsonFile(filePath: String, httpStatus: HttpStatus = HttpStatus.OK): ResponseEntity<String> {
        val mockResponseString = String(Files.readAllBytes(Paths.get(filePath)))
        return ResponseEntity(mockResponseString, httpStatus)
    }
}
