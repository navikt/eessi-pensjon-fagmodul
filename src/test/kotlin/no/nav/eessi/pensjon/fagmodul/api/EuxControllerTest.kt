
package no.nav.eessi.pensjon.fagmodul.api

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class EuxControllerTest {

    @SpyK
    private lateinit var mockEuxInnhentingService: EuxInnhentingService

    private lateinit var euxController: EuxController

    private val backupList = listOf("AT", "BE", "BG", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK", "UK")
    private val euxRestTemplate = mockk<RestTemplate>()
    private val euxSystemRestTemplate = mockk<RestTemplate>()

    @BeforeEach
    fun before() {

        mockEuxInnhentingService = EuxInnhentingService("Q2", EuxKlientAsSystemUser(euxRestTemplate, euxSystemRestTemplate))

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
        val path = "/buc/{$euxCaseId}/sed/{$dokumentId}/send?ventePaAksjon=false"
        every {
            euxRestTemplate.postForEntity(path, any<HttpEntity<String>>(), String::class.java)
        } returns ResponseEntity("", HttpStatus.OK)

        val result = euxController.sendSeden(euxCaseId, dokumentId)

        assertEquals(true, result.statusCode.is2xxSuccessful)
    }
}


