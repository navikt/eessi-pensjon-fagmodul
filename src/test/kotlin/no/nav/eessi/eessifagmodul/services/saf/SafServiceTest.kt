package no.nav.eessi.eessifagmodul.services.saf

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

@RunWith(MockitoJUnitRunner::class)
class SafServiceTest {
    @Mock
    private lateinit var safGraphQlOidcRestTemplate: RestTemplate

    @Mock
    private lateinit var safRestOidcRestTemplate: RestTemplate

    lateinit var safService: SafService

    @Before
    fun setup() {
        safService = SafService(safGraphQlOidcRestTemplate, safRestOidcRestTemplate)
    }

    @Test
    fun `gitt en gyldig hentMetadata reponse når metadata hentes så map til HentMetadataResponse`() {
        val responseJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/saf/hentMetadataResponse.json")))
                .trim()
                .replace(System.lineSeparator(), "")
                .replace(" ", "")

        whenever(safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenReturn(ResponseEntity(responseJson, HttpStatus.OK))
        val resp = safService.hentDokumentMetadata("1234567891000")

        val mapper = jacksonObjectMapper()
        assertEquals(mapper.writeValueAsString(resp), responseJson)
    }
}