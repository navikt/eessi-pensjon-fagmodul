package no.nav.eessi.pensjon.services.kodeverk

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class KodeverkClientTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    private lateinit var kodeverkClient: KodeverkClient

    @BeforeEach
    fun setup() {
       kodeverkClient = KodeverkClient(mockrestTemplate, "eessi-fagmodul")
    }

    @Test
    fun hentingavIso2landkodevedbrukAvlandkode2() {
        val expected = "SE"
        val landkode3 = "SWE"

        val mockResponseEntityISO3 = createResponseEntityFromJsonFile("src/test/resources/json/kodeverk/landkoderSammensattIso2.json")
        doReturn(mockResponseEntityISO3)
                .whenever(mockrestTemplate)
                .exchange(
                        eq("/api/v1/hierarki/LandkoderSammensattISO2/noder"),
                        any(),
                        any<HttpEntity<Unit>>(),
                        eq(String::class.java)
                )
        val actual = kodeverkClient.finnLandkode2(landkode3)
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun hentingavIso2landkodevedbrukAvlandkode3() {
        val expected = "BMU"
        val landkode2 = "BM"

        val mockResponseEntityISO3 = createResponseEntityFromJsonFile("src/test/resources/json/kodeverk/landkoderSammensattIso2.json")
        doReturn(mockResponseEntityISO3)
                .whenever(mockrestTemplate)
                .exchange(
                        eq("/api/v1/hierarki/LandkoderSammensattISO2/noder"),
                        any(),
                        any<HttpEntity<Unit>>(),
                        eq(String::class.java)
                )

        val actual = kodeverkClient.finnLandkode3(landkode2)

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun hentingavIso2landkodevedbrukAvlandkode3part2() {
        val expected = "ALB"
        val landkode2 = "AL"

        val mockResponseEntityISO3 = createResponseEntityFromJsonFile("src/test/resources/json/kodeverk/landkoderSammensattIso2.json")
        doReturn(mockResponseEntityISO3)
                .whenever(mockrestTemplate)
                .exchange(
                        eq("/api/v1/hierarki/LandkoderSammensattISO2/noder"),
                        any(),
                        any<HttpEntity<Unit>>(),
                        eq(String::class.java)
                )

        val actual = kodeverkClient.finnLandkode3(landkode2)

        Assertions.assertEquals(expected, actual)
    }


    @Test
    fun testerLankodeMed2Siffer() {
        val mockResponseEntityISO3 = createResponseEntityFromJsonFile("src/test/resources/json/kodeverk/landkoderSammensattIso2.json")
        doReturn(mockResponseEntityISO3)
                .whenever(mockrestTemplate)
                .exchange(
                        eq("/api/v1/hierarki/LandkoderSammensattISO2/noder"),
                        any(),
                        any<HttpEntity<Unit>>(),
                        eq(String::class.java)
                )
        val actual = kodeverkClient.hentLandkoderAlpha2()

        Assertions.assertEquals("ZW", actual.last())
        Assertions.assertEquals(249, actual.size)
    }

    @Test
    fun henteAlleLandkoderReturnererAlleLandkoder() {
        val mockResponseEntityISO3 = createResponseEntityFromJsonFile("src/test/resources/json/kodeverk/landkoderSammensattIso2.json")
        doReturn(mockResponseEntityISO3)
                .whenever(mockrestTemplate)
                .exchange(
                        eq("/api/v1/hierarki/LandkoderSammensattISO2/noder"),
                        any(),
                        any<HttpEntity<Unit>>(),
                        eq(String::class.java)
                )

        val json = kodeverkClient.hentAlleLandkoder()

        val list = mapJsonToAny(json, typeRefs<List<Landkode>>())

        Assertions.assertEquals(249, list.size)

        Assertions.assertEquals("AD", list.first().landkode2)
        Assertions.assertEquals("AND", list.first().landkode3)

    }

    @Test
    fun hentingavIso2landkodevedbrukAvlandkode3FeilerMedNull() {
        val expected = null
        val landkode2 = "BMUL"

        val mockResponseEntityISO3 = createResponseEntityFromJsonFile("src/test/resources/json/kodeverk/landkoderSammensattIso2.json")
        doReturn(mockResponseEntityISO3)
                .whenever(mockrestTemplate)
                .exchange(
                        eq("/api/v1/hierarki/LandkoderSammensattISO2/noder"),
                        any(),
                        any<HttpEntity<Unit>>(),
                        eq(String::class.java)
                )

        val actual = kodeverkClient.finnLandkode3(landkode2)

        Assertions.assertEquals(expected, actual)

    }

    private fun createResponseEntityFromJsonFile(filePath: String, httpStatus: HttpStatus = HttpStatus.OK): ResponseEntity<String> {
        val mockResponseString = String(Files.readAllBytes(Paths.get(filePath)))
        return ResponseEntity(mockResponseString, httpStatus)
    }
}