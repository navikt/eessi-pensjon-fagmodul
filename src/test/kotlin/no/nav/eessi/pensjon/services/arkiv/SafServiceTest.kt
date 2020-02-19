package no.nav.eessi.pensjon.services.arkiv

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@ExtendWith(MockitoExtension::class)
class SafServiceTest {

    @Mock
    private lateinit var safGraphQlOidcRestTemplate: RestTemplate

    @Mock
    private lateinit var safRestOidcRestTemplate: RestTemplate

    lateinit var safService: SafService

    @BeforeEach
    fun setup() {
        safService = SafService(safGraphQlOidcRestTemplate, safRestOidcRestTemplate)
    }

    @Test
    fun `gitt en gyldig hentMetadata reponse når metadata hentes så map til HentMetadataResponse`() {
        val responseJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/saf/hentMetadataResponse.json")))

        whenever(safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenReturn(ResponseEntity(responseJson, HttpStatus.OK))
        val resp = safService.hentDokumentMetadata("1234567891000")

        val mapper = jacksonObjectMapper()
        JSONAssert.assertEquals(mapper.writeValueAsString(resp), responseJson, true)
    }

    @Test
    fun `gitt noe annet enn 200 httpCopde feil når metadata hentes så kast SafException med tilhørende httpCode`() {

        doThrow(HttpClientErrorException.create (HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, HttpHeaders(), "".toByteArray(), Charset.defaultCharset()))
                .whenever(safGraphQlOidcRestTemplate).exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))

        assertThrows<SafException> {
            safService.hentDokumentMetadata("1234567891000")
        }
    }

    @Test
    fun `gitt en feil når metadata hentes så kast SafException med tilhørende httpCode`() {

        whenever(safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenThrow(RestClientException("some error"))
        assertThrows<SafException> {
            safService.hentDokumentMetadata("1234567891000")
        }
    }

    @Test
    fun `gitt noe annet enn 200 httpCopde feil når dokumentinnhold hentes så kast SafException med tilhørende httpCode`() {

        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, HttpHeaders(), "".toByteArray(), Charset.defaultCharset()))
                .whenever(safRestOidcRestTemplate).exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))

        assertThrows<SafException> {
            safService.hentDokumentInnhold("123", "456", VariantFormat.ARKIV)
        }
    }

    @Test
    fun `gitt en feil når dokumentinnhold hentes så kast SafException med tilhørende httpCode`() {
        whenever(safRestOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenThrow(RestClientException("some error"))
        assertThrows<SafException> {
            safService.hentDokumentInnhold("123", "456", VariantFormat.ARKIV)
        }
    }

    @Test
    fun `base64Test`() {
      FileInputStream(File("src/test/resources/OversiktBUCogSED.pdf")).readBytes()

        val dokumentInnholdBase64 = String(Base64.getEncoder().encode(FileInputStream(File("src/test/resources/OversiktBUCogSED.pdf")).readBytes()))
        val sliceActual = dokumentInnholdBase64.slice(IntRange(0, 100))
        val expected = "JVBERi0xLjUNJeLjz9MNCjg5NSAwIG9iag08PC9MaW5lYXJpemVkIDEvTCAyMTc2MjAvTyA4OTcvRSAxMDU5MzQvTiAxMy9UIDIxN"
        assertEquals(expected, sliceActual)
    }

    @Test
    fun `gitt en aktørid med journalposter med EESSI Tilleggsopplysninger når etterspør sakIder fra tilleggsopplysninger så returner liste av sakIder`() {
        val responseJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/saf/hentMetadataResponse.json")))

        whenever(safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenReturn(ResponseEntity(responseJson, HttpStatus.OK))
        val sakIder = safService.hentRinaSakIderFraDokumentMetadata("1234567891000")
        assertEquals(sakIder.size, 1)
        assertEquals(sakIder[0], "1111")
    }

}
