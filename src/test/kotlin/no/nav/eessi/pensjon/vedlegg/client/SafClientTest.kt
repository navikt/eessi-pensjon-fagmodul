package no.nav.eessi.pensjon.vedlegg.client


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.*

class SafClientTest {

    var safGraphQlOidcRestTemplate: RestTemplate = mockk()

    var safRestOidcRestTemplate: RestTemplate = mockk()

    @MockkBean
    lateinit var safClient: SafClient

    @BeforeEach
    fun setup() {
        safClient = SafClient(safGraphQlOidcRestTemplate, safRestOidcRestTemplate)
    }

    @Test
    fun `gitt en gyldig hentMetadata reponse når metadata hentes så map til HentMetadataResponse`() {
        val responseJson = javaClass.getResource("/json/saf/hentMetadataResponse.json")!!.readText()

        every { safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } returns ResponseEntity(responseJson, HttpStatus.OK)

        val resp = safClient.hentDokumentMetadata("1234567891000")

        val mapper = jacksonObjectMapper()
        JSONAssert.assertEquals(mapper.writeValueAsString(resp), responseJson, true)
    }

    @Test
    fun `henterMetaData skal kunne gi informasjon om relevante datoer`() {
        // given
        val responseJson = javaClass.getResource("/json/saf/hentMetadataResponseMedRelevanteDatoer.json")!!.readText()
        val expectedDate = "2024-02-06T08:56:15"

        every { safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } returns ResponseEntity(responseJson, HttpStatus.OK)

        // when
        val resp = safClient.hentDokumentMetadata("1234567891000")
        val relevantDate = resp.data.dokumentoversiktBruker.journalposter.firstOrNull()?.relevanteDatoer?.firstOrNull()

        // then
        assertEquals("DATO_DOKUMENT", relevantDate?.datotype)
        assertEquals(expectedDate, relevantDate?.dato)
    }

    @Test
    fun `gitt en gyldig hentMetadata reponse med tom tittel når metadata hentes så map til HentMetadataResponse`() {
        val responseJson = javaClass.getResource("/json/saf/hentMetadataResponse.json")!!.readText()
                .replace("\"JOURNALPOSTTITTEL\"", "null")

        every { safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } returns ResponseEntity(responseJson, HttpStatus.OK)

        val resp = safClient.hentDokumentMetadata("1234567891000")

        assertEquals(null, resp.data.dokumentoversiktBruker.journalposter[0].tittel)
    }

    @Test
    fun `gitt en mappingfeil når metadata hentes så kast en feil`() {
        val responseJson = javaClass.getResource("/json/saf/hentMetadataResponseMedError.json")!!.readText()

        every { safGraphQlOidcRestTemplate.exchange(
            any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java))
        } returns ResponseEntity(responseJson, HttpStatus.OK)

        val exception = assertThrows<HttpServerErrorException> {
                safClient.hentDokumentMetadata("1234567891000")
            }
        assertEquals("500 En feil oppstod under henting av dokument metadata fra SAF", exception.message)

    }

    @Test
    fun `gitt noe annet enn 200 httpCopde feil når metadata hentes så kast HttpClientErrorException med tilhørende httpCode`() {

        every { safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } throws HttpClientErrorException.create (HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, HttpHeaders(), "".toByteArray(), Charset.defaultCharset())

        assertThrows<HttpClientErrorException> {
            safClient.hentDokumentMetadata("1234567891000")
        }
    }

    @Test
    fun `gitt en feil når metadata hentes så kast HttpClientErrorException med tilhørende httpCode`() {

        every { safGraphQlOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } throws RestClientException("some error")

        assertThrows<HttpServerErrorException> {
            safClient.hentDokumentMetadata("1234567891000")
        }
    }

    @Test
    fun `gitt noe annet enn 200 httpCopde feil når dokumentinnhold hentes så kast HttpServerErrorException med tilhørende httpCode`() {

        every { safRestOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } throws HttpClientErrorException.create(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, HttpHeaders(), "".toByteArray(), Charset.defaultCharset())

        assertThrows<HttpServerErrorException> {
            safClient.hentDokumentInnhold("123", "456", "ARKIV")
        }
    }

    @Test
    fun `gitt en feil når dokumentinnhold hentes så kast HttpClientErrorException med tilhørende httpCode`() {
        every { safRestOidcRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) } throws RestClientException("some error")

        assertThrows<HttpServerErrorException> {
            safClient.hentDokumentInnhold("123", "456", "ARKIV")
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

}
