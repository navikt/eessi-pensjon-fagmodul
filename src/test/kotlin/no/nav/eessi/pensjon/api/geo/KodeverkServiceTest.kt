package no.nav.eessi.pensjon.api.geo

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class KodeverkServiceTest {

    private val restTemplate = mockk<RestTemplate>()
    private val kodeverkService = KodeverkService(restTemplate)

    @ParameterizedTest
    @ValueSource(strings = ["v4.2", "v4.3", "v4.4"])
    fun `getLandkoderAkseptertAvRina skal ikke endre JSON`(version: String) {
        val body = """
            {
                "$version": {
                    "euEftaLand": [{"landkode": "NO", "landnavn": "Norge"}],
                    "verdensLand": [{"landkode": "NO", "landnavn": "Norge"}],
                    "statsborgerskap": [{"landkode": "NO", "landnavn": "Norge"}],
                    "verdensLandHistorisk": [{"landkode": "NO", "landnavn": "Norge"}]
                }
            }
        """.trimIndent()
        every {
            restTemplate.exchange(
                "/landkoder/rina?format=iso2",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        } returns ResponseEntity.ok(body)

        val result = kodeverkService.getLandkoderAkseptertAvRina("iso2")

        assertNotNull(result)
        JSONAssert.assertEquals(body, result!!.toJson(), true)
    }

    @Test
    fun `getLandkoderAkseptertAvRina ved null verdi av v45 skal returnere et objekt med v45 som null`() {
        val body = """
            {
                "v4.5": null
            }
        """.trimIndent()
        every {
            restTemplate.exchange(
                "/landkoder/rina",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                String::class.java
            )
        } returns ResponseEntity.ok(body)

        val result = kodeverkService.getLandkoderAkseptertAvRina()

        assertNotNull(result)
        assertNull(result!!.v45)
        JSONAssert.assertEquals("{}", result.toJson(), true)
    }
}
