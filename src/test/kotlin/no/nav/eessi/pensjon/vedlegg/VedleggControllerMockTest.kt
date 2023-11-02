package no.nav.eessi.pensjon.vedlegg

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.vedlegg.client.Dokument
import no.nav.eessi.pensjon.vedlegg.client.HentMetadataResponse
import no.nav.eessi.pensjon.vedlegg.client.HentdokumentInnholdResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class VedleggControllerMockTest {

    var vedleggService: VedleggService = mockk()

    @SpyK
    var auditLogger: AuditLogger = AuditLogger()

    private lateinit var vedleggController: VedleggController

    @BeforeEach
    fun setup() {
        vedleggController = VedleggController(vedleggService, auditLogger)
//        vedleggController.initMetrics()
    }

    @Test
    fun `gitt en 400 httpstatuscode fra safClient når metadata hentes så kastes HttpClientErrorException`() {
        every { vedleggService.hentDokumentMetadata("123") } throws HttpClientErrorException(HttpStatus.valueOf(400),"noe gikk galt")

        assertThrows<HttpClientErrorException> { vedleggController.hentDokumentMetadata("123") }
    }

    @Test
    fun `gitt en 200 httpstatuscode fra safClient når dokument metadata hentes så returnes 200 httpstatuscode`() {

        val responseJson = javaClass.getResource("/json/saf/hentMetadataResponse.json").readText()
                .trim()
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
        val mapper = jacksonObjectMapper()

        every { vedleggService.hentDokumentMetadata("123") } returns mapper.readValue(responseJson, HentMetadataResponse::class.java)

        val resp = vedleggController.hentDokumentMetadata("123")
        assertEquals(HttpStatus.valueOf(200), resp.statusCode)
        assertEquals(resp.body!!.trim().replace("\r", "").replace("\n", "").replace(" ", ""), responseJson)
    }

    @Test
    fun `gitt en 400 httpstatuscode fra safClient når dokumentinnhold hentes så kastes HttpClientErrorException`() {
        every { vedleggService.hentDokumentInnhold("123", "4567", "ARKIV") } throws  HttpClientErrorException(HttpStatus.valueOf(400), "noe gikk galt")

        assertThrows<HttpClientErrorException> {vedleggController.getDokumentInnhold("123", "4567", "ARKIV") }
    }

    @Test
    fun `gitt en 200 httpstatuscode fra safClient når dokumentinnhold hentes så returnes 200 httpstatuscode`() {
        every { vedleggService.hentDokumentInnhold("123", "4567", "ARKIV") } returns HentdokumentInnholdResponse("WVdKag==", "enFil.pdf", "application/pdf")

        val resp = vedleggController.getDokumentInnhold("123", "4567", "ARKIV")
        assertEquals(HttpStatus.valueOf(200), resp.statusCode)
        assertEquals(resp.body?.replace("\r","") , javaClass.getResource("/json/saf/hentDokumentInnholdResponse.json").readText().replace("\r","")
        )
    }

    @Test
    fun `gitt Et Gyldig PutVedleggTilDokument Saa Kall EuxPutVedleggPaaDokument`() {
        val filInnhold = String(Base64.getEncoder().encode(Files.readAllBytes(Paths.get("src/test/resources/etbilde.pdf"))))

        val rinasakid = "456"
        val rinadocid = "7892"

        val filnavn = "P2000 - Krav om alderspensjon.pdf"
        val filtype = "application/pdf".split("/")[1]

        every { vedleggService.hentDokumentMetadata(any(), any(), any()) } returns Dokument("4444444","P2000 - Krav om alderspensjon", emptyList())
        every { vedleggService.hentDokumentInnhold(any(), any(), any()) } returns HentdokumentInnholdResponse(filInnhold, filnavn, "application/pdf")
        justRun { vedleggService.leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any()) }

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val disposition = ContentDisposition
                .builder("form-data")
                .name("file")
                .build().toString()

        val attachmentMeta = LinkedMultiValueMap<String, String>()
        attachmentMeta.add(HttpHeaders.CONTENT_DISPOSITION, disposition)
        val dokumentInnholdBinary = Base64.getDecoder().decode(filInnhold)
        val attachmentPart = HttpEntity(dokumentInnholdBinary, attachmentMeta)

        val body = LinkedMultiValueMap<String, Any>()
        body.add("multipart", attachmentPart)

        vedleggController.putVedleggTilDokument("123",
                rinasakid,
                rinadocid,
                "1",
                "2",
                "ARKIV")

        verify (exactly = 1) { vedleggService.leggTilVedleggPaaDokument(
            eq("123"),
            eq(rinasakid),
            eq(rinadocid),
            eq(filInnhold),
            eq(filnavn),
            eq(filtype))}
    }

    @Test
    fun `Gitt en person med journalposter som inneholder RinaSakIder når SAF metadata blir kallet Så hent RinaSakIDer`() {
        val aktoerId = "1212"

        every { vedleggService.hentRinaSakIderFraMetaData(aktoerId) } returns listOf("1212")

        val resp = vedleggController.hentRinaIderFraMetadata(aktoerId)
        assertEquals(1, resp.size)
    }

    @Test
    fun `Gitt en person uten journalposter som inneholder RinaSakIder når SAF metadata blir kallet Så returner tom liste`() {
        val aktoerId = "1212"

        every { vedleggService.hentRinaSakIderFraMetaData(aktoerId) } returns emptyList<String>()

        val resp = vedleggController.hentRinaIderFraMetadata(aktoerId)
        assertEquals(0, resp.size)
    }

}
