package no.nav.eessi.eessifagmodul.controllers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.services.EessiServiceException
import no.nav.eessi.eessifagmodul.services.saf.HentMetadataResponse
import no.nav.eessi.eessifagmodul.services.saf.HentdokumentInnholdResponse
import no.nav.eessi.eessifagmodul.services.saf.SafService
import no.nav.eessi.eessifagmodul.services.saf.VariantFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class SafControllerTest {

    @Mock
    lateinit var safService: SafService

    lateinit var safController: SafController

    @Before
    fun setup() {
        safController = SafController(safService)

    }

    @Test
    fun `gitt en 400 httpstatuscode fra safService når metadata hentes så returnes 400 httpstatuscode`() {
        whenever(safService.hentDokumentMetadata("123"))
                .thenThrow(EessiServiceException("noe gikk galt", HttpStatus.valueOf(400)))

        val resp = safController.hentDokumentMetadata("123")
        assertEquals(HttpStatus.valueOf(400), resp.statusCode)
        assertTrue(resp.body!!.contains("noe gikk galt"))
    }

    @Test
    fun `gitt en 200 httpstatuscode fra safService når dokument metadata hentes så returnes 200 httpstatuscode`() {

        val responseJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/saf/hentMetadataResponse.json")))
                .trim()
                .replace(System.lineSeparator(), "")
                .replace(" ", "")
        val mapper = jacksonObjectMapper()

        whenever(safService.hentDokumentMetadata("123"))
                .thenReturn(mapper.readValue(responseJson, HentMetadataResponse::class.java))

        val resp = safController.hentDokumentMetadata("123")
        assertEquals(HttpStatus.valueOf(200), resp.statusCode)
        assertEquals(resp.body!!.trim().replace(System.lineSeparator(), "").replace(" ", ""), responseJson)
    }

    @Test
    fun `gitt en 400 httpstatuscode fra safService når dokumentinnhold hentes så returnes 400 httpstatuscode`() {
        whenever(safService.hentDokumentInnhold("123", "4567", VariantFormat.ARKIV))
                .thenThrow(EessiServiceException("noe gikk galt", HttpStatus.valueOf(400)))

        val resp = safController.getDokumentInnhold("123", "4567", VariantFormat.ARKIV)
        assertEquals(HttpStatus.valueOf(400), resp.statusCode)
        assertTrue(resp.body!!.contains("noe gikk galt"))
    }

    @Test
    fun `gitt en 200 httpstatuscode fra safService når dokumentinnhold hentes så returnes 200 httpstatuscode`() {
        whenever(safService.hentDokumentInnhold("123", "4567", VariantFormat.ARKIV))
                .thenReturn(HentdokumentInnholdResponse("abc", "enFil.pdf", "application/pdf"))

        val resp = safController.getDokumentInnhold("123", "4567", VariantFormat.ARKIV)
        assertEquals(HttpStatus.valueOf(200), resp.statusCode)
        assertEquals(resp.body, String(Files.readAllBytes(Paths.get("src/test/resources/json/saf/hentDokumentInnholdResponse.json")))
        )
    }
}