package no.nav.eessi.pensjon.vedlegg

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
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
@MockkBeans(
    MockkBean(name = "personService", classes = [PersonService::class]),
    MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "restEuxTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxKlient", classes = [EuxKlientAsSystemUser::class]),
    MockkBean(name = "kodeverkRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "prefillOAuthTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class]),
    MockkBean(name = "euxSystemRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxNavIdentRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pensjonsinformasjonClient", classes = [PensjonsinformasjonClient::class])
)
class VedleggControllerSpringTest {
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