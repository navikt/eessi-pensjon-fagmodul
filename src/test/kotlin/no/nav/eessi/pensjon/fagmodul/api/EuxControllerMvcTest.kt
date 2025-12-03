package no.nav.eessi.pensjon.fagmodul.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.client.RestTemplate

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.fagmodul.api"])
@WebMvcTest(EuxController::class)

@MockkBean(name = "euxKlient", types = [EuxKlientAsSystemUser::class], relaxed = true)
@MockkBean(name = "bucController", types = [BucController::class], relaxed = true)
@MockkBean(name = "prefillController", types = [PrefillController::class], relaxed = true)
@MockkBean(name = "gcpStorageService", types = [GcpStorageService::class], relaxed = true)
@MockkBean(name = "euxNavIdentRestTemplateV2", types = [RestTemplate::class])
@MockkBean(name = "sedController", types = [SedController::class], relaxed = true)
class EuxControllerMvcTest {

    @MockkSpyBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @Autowired
    private lateinit var euxKlient: EuxKlientAsSystemUser

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Gitt at vi sender en P2000 fra EP til RINA, saa returneres true etter sending`() {
        val endpointUrl = "/eux/buc/12345/sed/p200012354dokid/send"
        every { euxKlient.sendSed(eq("12345"), eq("p200012354dokid")) } returns true

        val result = mockMvc.post(endpointUrl).andReturn().response

        assertEquals(200, result.status)

    }

    @Test
    fun `Gitt at vi sender en P2000 fra EP til RINA, saa returneres false dersom noe går glt under sending`() {
        val endpointUrl = "/eux/buc/12345/sed/dokid/send"
        every { euxKlient.sendSed(any(), any()) } returns false

        val result = mockMvc.post(endpointUrl).andReturn().response

        assertEquals("Sed ble IKKE sendt til Rina", result.contentAsString)

    }

}



