
package no.nav.eessi.pensjon.fagmodul.api

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class EuxControllerTest {

    @SpyK
    private lateinit var mockEuxInnhentingService: EuxInnhentingService
    private var gcpStorageService: GcpStorageService = mockk()
    private lateinit var euxController: EuxController

    private val backupList = listOf("AT", "BE", "BG", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK", "UK")
    private val euxRestTemplate = mockk<RestTemplate>()
    private val euxSystemRestTemplate = mockk<RestTemplate>()

    @BeforeEach
    fun before() {

        mockEuxInnhentingService = EuxInnhentingService(
            "Q2",
            EuxKlientAsSystemUser(euxRestTemplate, euxSystemRestTemplate),
            gcpStorageService
        )

        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)

        euxController = EuxController(
            euxInnhentingService = mockEuxInnhentingService
        )
    }


    @Test
    fun `gitt en liste over landkoder over instusjoner fra eux gir tomliste sakl backuplist returneres`() {
        every { mockEuxInnhentingService.getInstitutions(any(), "") } returns emptyList()

        val result = euxController.getPaakobledeland(P_BUC_06)

        val list = mapJsonToAny<List<String>>(result.body!!)
        assertIterableEquals(backupList, list)

    }

    @Test
    fun `gitt en liste over landkoder over instusjoner fra eux med liste så retureres den`() {
        every { mockEuxInnhentingService.getInstitutions(any(), "") } returns listOf(InstitusjonItem("NO", "31231","3123"))

        val result = euxController.getPaakobledeland(P_BUC_06)

        val list = mapJsonToAny<List<String>>(result.body!!)
        assertEquals(1, list.size)
    }

    @Test
    fun `Gitt at vi skal sende en P2000 saa returneres true etter sending`() {
        val euxCaseId = "111"
        val dokumentId = "222"
        val path = "/buc/$euxCaseId/sed/$dokumentId/send?ventePaAksjon=false"
        every {
            euxRestTemplate.postForEntity(path, any<HttpEntity<String>>(), String::class.java)
        } returns ResponseEntity("", HttpStatus.OK)

        val result = euxController.sendSeden(euxCaseId, dokumentId)

        assertEquals(true, result.statusCode.is2xxSuccessful)
    }

    @Test
    fun `sendSedMedMottakere skal ta i mot en liste med mottakere og sende videre`() {
        val rinaSakId = "123"
        val dokumentId = "456"
        val mottakere = listOf("NO:974652382", "NO:NAVAT05", "NO:NAVAT04")
        every {
            euxRestTemplate.postForEntity(match<String> { path -> path.contains("/buc/$rinaSakId/sed/$dokumentId/sendTo") }, any<HttpEntity<String>>(), String::class.java)
        } returns ResponseEntity("", HttpStatus.OK)

        val result = euxController.sendSedMedMottakere(rinaSakId, dokumentId, mottakere)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals("Sed er sendt til Rina", result.body)
    }

    @Test
    fun `resendtDokumenter skal gi 200 ved gyldig input`() {
        val dokumentListe = "1452061_5120d7d59ae548a4a980fe93eb58f9bd_1"
        val capturedRequestBody = slot<HttpEntity<String>>()

        every {
            euxRestTemplate.postForEntity(match<String> { path -> path.contains("/resend/liste") }, capture(capturedRequestBody), String::class.java)
        } returns ResponseEntity("{\"status\":\"OK\"}", HttpStatus.OK)

        val result = euxController.resendtDokumenter(dokumentListe)
        println("resultat: ${capturedRequestBody.captured.body}")

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals("Sederer resendt til Rina", result.body)
        assert(capturedRequestBody.captured.body!!.contains("1452061_5120d7d59ae548a4a980fe93eb58f9bd_1"))
    }

    @Test
    fun `resendtDokumenter skal gi feil ved BAD_REQUEST`() {
        val dokumentListe = "1452061_5120d7d59ae548a4a980fe93eb58f9bd_1"
        val capturedRequestBody = slot<HttpEntity<String>>()

        every {
            euxRestTemplate.postForEntity(match<String> { path -> path.contains("/resend/liste") }, capture(capturedRequestBody), String::class.java)
        } returns ResponseEntity("{\"status\":\"BAD_REQUEST\",\"messages\":\"400 BAD_REQUEST \\\"Følgende linjer hadde feil format: \\n1452077_167ac108dde642f9b2784d58c5f7e55e_ 2\\\"\",\"timestamp\":\"23-05-2025 13:36:48\"}", HttpStatus.OK)

        val result = euxController.resendtDokumenter(dokumentListe)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assert(result.toString().contains("Følgende linjer hadde feil format:"))
        assert(result.toString().contains("Seder ble IKKE resendt til Rina"))
    }
}


