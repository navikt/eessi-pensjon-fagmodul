package no.nav.eessi.pensjon.fagmodul.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.fagmodul.api"])
@WebMvcTest(EuxController::class)
@MockkBeans(
    MockkBean(name = "euxKlient", classes = [EuxKlientAsSystemUser::class], relaxed = true),
    MockkBean(name = "bucController", classes = [BucController::class], relaxed = true),
    MockkBean(name = "prefillController", classes = [PrefillController::class], relaxed = true),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class], relaxed = true),
    MockkBean(name = "sedController", classes = [SedController::class], relaxed = true)
    )
class EuxControllerMvcTest {

    @SpykBean
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



