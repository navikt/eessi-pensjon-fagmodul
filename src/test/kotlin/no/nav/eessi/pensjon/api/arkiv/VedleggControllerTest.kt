package no.nav.eessi.pensjon.api.arkiv

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.vedlegg.*
import no.nav.eessi.pensjon.vedlegg.client.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class VedleggControllerTest {

    @Mock
    lateinit var safClient: SafClient

    @Mock
    lateinit var auditLogger: AuditLogger

    lateinit var vedleggController: VedleggController

    @BeforeEach
    fun setup() {
        vedleggController = VedleggController(safClient, auditLogger)

    }

    @Test
    fun `gitt en 400 httpstatuscode fra safClient når metadata hentes så returnes 400 httpstatuscode`() {
        whenever(safClient.hentDokumentMetadata("123"))
                .thenThrow(SafException("noe gikk galt", HttpStatus.valueOf(400)))

        val resp = vedleggController.hentDokumentMetadata("123")
        assertEquals(HttpStatus.valueOf(400), resp.statusCode)
        assertTrue(resp.body!!.contains("noe gikk galt"))
    }

    @Test
    fun `gitt en 200 httpstatuscode fra safClient når dokument metadata hentes så returnes 200 httpstatuscode`() {

        val responseJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/saf/hentMetadataResponse.json")))
                .trim()
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
        val mapper = jacksonObjectMapper()

        whenever(safClient.hentDokumentMetadata("123"))
                .thenReturn(mapper.readValue(responseJson, HentMetadataResponse::class.java))

        val resp = vedleggController.hentDokumentMetadata("123")
        assertEquals(HttpStatus.valueOf(200), resp.statusCode)
        assertEquals(resp.body!!.trim().replace("\r", "").replace("\n", "").replace(" ", ""), responseJson)
    }

    @Test
    fun `gitt en 400 httpstatuscode fra safClient når dokumentinnhold hentes så returnes 400 httpstatuscode`() {
        whenever(safClient.hentDokumentInnhold("123", "4567", VariantFormat.ARKIV))
                .thenThrow(SafException("noe gikk galt", HttpStatus.valueOf(400)))

        val resp = vedleggController.getDokumentInnhold("123", "4567", VariantFormat.ARKIV)
        assertEquals(HttpStatus.valueOf(400), resp.statusCode)
        assertTrue(resp.body!!.contains("noe gikk galt"))
    }

    @Test
    fun `gitt en 200 httpstatuscode fra safClient når dokumentinnhold hentes så returnes 200 httpstatuscode`() {
        whenever(safClient.hentDokumentInnhold("123", "4567", VariantFormat.ARKIV))
                .thenReturn(HentdokumentInnholdResponse("WVdKag==", "enFil.pdf", "application/pdf"))

        val resp = vedleggController.getDokumentInnhold("123", "4567", VariantFormat.ARKIV)
        assertEquals(HttpStatus.valueOf(200), resp.statusCode)
        assertEquals(resp.body?.replace("\r","") , String(Files.readAllBytes(Paths.get("src/test/resources/json/saf/hentDokumentInnholdResponse.json"))).replace("\r","")
        )
    }
}
