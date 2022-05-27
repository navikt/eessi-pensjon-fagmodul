package no.nav.eessi.pensjon.vedlegg

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.vedlegg.client.Dokument
import no.nav.eessi.pensjon.vedlegg.client.HentdokumentInnholdResponse
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class] ,webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class VedleggControllerSpringTest {

    @MockkBean(name = "prefillOAuthTemplate")
    private lateinit var prefillOAuthTemplate: RestTemplate

    @MockkBean(name = "euxNavIdentRestTemplate")
    private lateinit var euxNavIdentRestTemplate: RestTemplate

    @MockkBean(name = "euxSystemRestTemplate")
    private lateinit var euxSystemRestTemplate: RestTemplate

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var restSafTemplate: RestTemplate

    @MockkBean(name = "proxyOAuthRestTemplate")
    private lateinit var proxyOAuthRestTemplate: RestTemplate

    @MockkBean(name = "safRestOidcRestTemplate")
    private lateinit var safRestOidcRestTemplate: RestTemplate

    @MockkBean(name = "pensjoninformasjonRestTemplate")
    private lateinit var pensjoninformasjonRestTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var vedleggService: VedleggService

    @Autowired
    private val mockMvc: MockMvc? = null

    @Test
    @Throws(Exception::class)
    fun shouldReturnDefaultMessage() {
        justRun { vedleggService.leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any()) }
        every { vedleggService.hentDokumentMetadata(any(), any(), any()) } returns Dokument("4444444","P2000 - Krav om alderspensjon", emptyList())
        every { vedleggService.hentDokumentInnhold(any(), any(), any()) } returns HentdokumentInnholdResponse("WVdKag==","blah.pdf", "application/pdf")

        this.mockMvc!!.perform(put("/saf/vedlegg/1231231231231/111111/2222222/3333333/4444444/ARKIV"))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("{\"success\": true}")))

        verify (exactly = 1) { vedleggService.leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any()) }
        verify (exactly = 1) { vedleggService.hentDokumentInnhold(any(), any(), any()) }
    }
}