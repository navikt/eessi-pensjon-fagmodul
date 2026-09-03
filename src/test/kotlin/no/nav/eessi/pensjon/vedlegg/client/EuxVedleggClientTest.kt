package no.nav.eessi.pensjon.vedlegg.client

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.fagmodul.api.vedlegg.client.EuxVedleggClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.util.Base64

class EuxVedleggClientTest {

    private val euxNavIdentRestTemplate: RestTemplate = mockk()
    private val euxNavIdentRestTemplateV2: RestTemplate = mockk()

    private val euxVedleggClient = EuxVedleggClient(
        euxNavIdentRestTemplate = euxNavIdentRestTemplate,
        euxNavIdentRestTemplateV2 = euxNavIdentRestTemplateV2
    )

    @Test
    fun `hentEnkeltVedlegg returnerer base64 filinnhold med metadata`() {
        val rinaSakId = "rina123"
        val dokumentId = "sed123"
        val vedleggId = "vedlegg123"
        val vedleggBytes = "PDF-INNHOLD".toByteArray()

        val headers = HttpHeaders().apply {
            contentDisposition = ContentDisposition.attachment().filename("test.pdf").build()
            contentType = MediaType.APPLICATION_PDF
        }

        every {
            euxNavIdentRestTemplateV2.exchange(
                "/buc/$rinaSakId/sed/$dokumentId/vedlegg/$vedleggId",
                HttpMethod.GET,
                null,
                ByteArray::class.java
            )
        } returns ResponseEntity(vedleggBytes, headers, HttpStatus.OK)

        val response = euxVedleggClient.hentEnkeltVedlegg(rinaSakId, dokumentId, vedleggId)

        assertEquals(Base64.getEncoder().encodeToString(vedleggBytes), response.filInnhold)
        assertEquals("test.pdf", response.fileName)
        assertEquals("application/pdf", response.contentType)
    }

    @Test
    fun `hentEnkeltVedlegg kaster feil naar body mangler`() {
        val headers = HttpHeaders().apply {
            contentDisposition = ContentDisposition.attachment().filename("test.pdf").build()
            contentType = MediaType.APPLICATION_PDF
        }

        every {
            euxNavIdentRestTemplateV2.exchange(
                any<String>(),
                HttpMethod.GET,
                null,
                ByteArray::class.java
            )
        } returns ResponseEntity(null, headers, HttpStatus.OK)

        val exception = assertThrows<RuntimeException> {
            euxVedleggClient.hentEnkeltVedlegg("rina123", "sed123", "vedlegg123")
        }

        assertEquals("Vedlegg ikke funnet", exception.message)
    }
}

