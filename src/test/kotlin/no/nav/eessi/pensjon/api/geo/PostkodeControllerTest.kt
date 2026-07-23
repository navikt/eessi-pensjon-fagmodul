package no.nav.eessi.pensjon.api.geo

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.Postnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate

@WebMvcTest(PostkodeController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.geo"])
@ActiveProfiles("unsecured-webmvctest")
class PostkodeControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var restTemplate: RestTemplate

    @Test
    fun `henter poststed for gyldig postnummer`() {
        every { kodeverkClient.hentPostSted("0607") } returns Postnummer("0607", "Oslo")

        val response = mvc.perform(
            get("/postnummer/0607/sted")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().response

        assertEquals("Oslo", response.contentAsString)
    }

    @Test
    fun `returnerer tom body naar postnummer ikke finnes`() {
        every { kodeverkClient.hentPostSted("9999") } returns null

        val response = mvc.perform(
            get("/postnummer/9999/sted")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().response

        assertEquals("", response.contentAsString)
    }
}
