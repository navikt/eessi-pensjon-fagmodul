package no.nav.eessi.pensjon.fagmodul.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.fagmodul.api"])
@WebMvcTest(SedController::class)
@MockkBeans(
    MockkBean(name = "auditLogger", classes = [AuditLogger::class], relaxed = true),
    MockkBean(name = "bucController", classes = [BucController::class], relaxed = true),
    MockkBean(name = "euxController", classes = [EuxController::class], relaxed = true),
    MockkBean(name = "euxKlient", classes = [EuxKlientAsSystemUser::class], relaxed = true),
    MockkBean(name = "prefillController", classes = [PrefillController::class], relaxed = true)
    )
class SedControllerMvcTest {

    @SpykBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @Autowired
    private lateinit var euxKlient: EuxKlientAsSystemUser

    @Autowired
    private lateinit var mockMvc: MockMvc


    @Test
    fun `Gitt at vi forsøker å lage pdf så skal vi gi ok melding ved ok`() {
        val endpointUrl = "/sed/pdf"
        every { euxKlient.lagPdf(any()) } returns true

        mockMvc.perform(
            post(endpointUrl)
                .content("Sed er sendt til Rina")
        )
            .andExpect(status().isOk())
            .andExpect(content().string("Sed er sendt til Rina"))

    }

    @Test
    fun `Gitt at vi forsøker å lage pdf med en tom String så skal vi kaste en exception`() {
        val endpointUrl = "/sed/pdf"
        every { euxKlient.lagPdf(any()) } returns true

        mockMvc.perform(
            post(endpointUrl)
                .content("")
        )
            .andExpect(status().is4xxClientError())
            .andReturn()

    }

    @Test
    fun `Gitt at vi forsøker å lage pdf som feiler så skal vi logge en errormelding`() {
        val endpointUrl = "/sed/pdf"
        every { euxInnhentingService.lagPdf(any()) } throws Exception("")

        mockMvc.perform(
            post(endpointUrl)
                .content("Sed er sendt til Rina")
        )
            .andExpect(status().is4xxClientError())
            .andExpect(content().string("PDF ble IKKE generert"))
            .andReturn()
    }
}



