package no.nav.eessi.eessifagmodul.services.saf

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.services.EessiServiceException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.*
import org.springframework.web.client.RestClientException
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

    @Test(expected = EessiServiceException::class)
    fun `gitt noe annet enn 200 httpCopde feil når metadata hentes så kast EessiServiceExeption med tilhørende httpCode`() {
        whenever(safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenReturn(ResponseEntity("", HttpStatus.NOT_FOUND))
        safService.hentDokumentMetadata("1234567891000")
    }

    @Test(expected = EessiServiceException::class)
    fun `gitt en feil når metadata hentes så kast EessiServiceExeption med tilhørende httpCode`() {
        whenever(safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenThrow(RestClientException("some error"))
        safService.hentDokumentMetadata("1234567891000")
    }

    @Test
    fun `gitt en gyldig hentDokumentInnhold reponse når dokumentData hentes så map til HentdokumentInnholdResponse`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.contentDisposition = ContentDisposition.builder("application/pdf").filename("enFil.pdf").build()

        whenever(safRestOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenReturn(ResponseEntity("abc", headers, HttpStatus.OK))
        val resp = safService.hentDokumentInnhold("123", "456", VariantFormat.ARKIV)

        assertEquals("YWJj", resp.base64)
    }

    @Test(expected = EessiServiceException::class)
    fun `gitt noe annet enn 200 httpCopde feil når dokumentinnhold hentes så kast EessiServiceExeption med tilhørende httpCode`() {
        whenever(safRestOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenReturn(ResponseEntity("", HttpStatus.NOT_FOUND))
        safService.hentDokumentInnhold("123","456", VariantFormat.ARKIV)
    }

    @Test(expected = EessiServiceException::class)
    fun `gitt en feil når dokumentinnhold hentes så kast EessiServiceExeption med tilhørende httpCode`() {
        whenever(safRestOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java)))
                .thenThrow(RestClientException("some error"))
        safService.hentDokumentInnhold("123","456", VariantFormat.ARKIV)
    }
}