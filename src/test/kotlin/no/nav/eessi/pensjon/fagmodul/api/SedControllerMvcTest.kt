package no.nav.eessi.pensjon.fagmodul.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.logging.AuditLogger
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.fagmodul.api"])
@WebMvcTest(SedController::class)
    @MockkBean(name = "auditLogger", types = [AuditLogger::class], relaxed = true)
    @MockkBean(name = "bucController", types = [BucController::class], relaxed = true)
    @MockkBean(name = "euxController", types = [EuxController::class], relaxed = true)
    @MockkBean(name = "euxKlient", types = [EuxKlientAsSystemUser::class], relaxed = true)
    @MockkBean(name = "prefillController", types = [PrefillController::class], relaxed = true)
    @MockkBean(name = "euxNavIdentRestTemplateV2", types = [RestTemplate::class])
    @MockkBean(name = "gcpStorageService", types = [GcpStorageService::class], relaxed = true)
class SedControllerMvcTest {

    @MockkSpyBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @Autowired
    private lateinit var euxKlient: EuxKlientAsSystemUser

    @Autowired
    private lateinit var mockMvc: MockMvc


    @Test
    fun `Gitt at vi forsøker å lage pdf så skal vi gi ok melding ved ok`() {
        val endpointUrl = "/sed/pdf"
        every { euxKlient.lagPdf(any()) } returns mockk(relaxed = true)

        mockMvc.perform(
            post(endpointUrl)
                .content("Sed er sendt til Rina")
        )
            .andExpect(status().isOk())
            .andExpect(content().string("{\"filInnhold\":\"\",\"fileName\":\"\",\"contentType\":\"\"}"))

    }

    @Test
    fun `Gitt at vi forsøker å lage pdf med en tom String så skal vi kaste en exception`() {
        val endpointUrl = "/sed/pdf"
        every { euxKlient.lagPdf(any()) } returns mockk()

        mockMvc.perform(
            post(endpointUrl)
                .content("")
        )
            .andExpect(status().is4xxClientError())
            .andReturn()

    }

    @Test
    @Disabled
    fun `Gitt at vi forsøker å lage pdf som feiler så skal vi logge en errormelding`() {
        val endpointUrl = "/sed/pdf"
        every { euxInnhentingService.lagPdf(any()) } throws Exception("")

        mockMvc.perform(
            post(endpointUrl)
                .content("Sed er sendt til Rina")
        )
            .andExpect(status().is5xxServerError())
            .andExpect(content().string(""))
            .andReturn()
    }
}



