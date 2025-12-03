package no.nav.eessi.pensjon.vedlegg

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.vedlegg.client.Dokument
import no.nav.eessi.pensjon.vedlegg.client.HentdokumentInnholdResponse
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    classes = [UnsecuredWebMvcTestLauncher::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc

@MockkBean(name = "personService", types = [PersonService::class])
@MockkBean(name = "pdlRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "restEuxTemplate", types = [RestTemplate::class])
@MockkBean(name = "euxKlient", types = [EuxKlientAsSystemUser::class])
@MockkBean(name = "kodeverkRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "prefillOAuthTemplate", types = [RestTemplate::class])
@MockkBean(name = "gcpStorageService", types = [GcpStorageService::class])
@MockkBean(name = "euxSystemRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "safRestOidcRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "euxNavIdentRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "safGraphQlOidcRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "euxNavIdentRestTemplateV2", types = [RestTemplate::class])
@MockkBean(name = "kafkaTemplate", types = [KafkaTemplate::class], relaxed = true)
@MockkBean(name = "pensjonsinformasjonClient", types = [PensjonsinformasjonClient::class])

class VedleggControllerSpringTest {
    @MockkBean
    lateinit var vedleggService: VedleggService

    @Autowired
    private val mockMvc: MockMvc? = null

    @Test
    @Throws(Exception::class)
    fun shouldReturnDefaultMessage() {
        justRun { vedleggService.leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any()) }
        every { vedleggService.hentDokumentMetadata(any(), any(), any()) } returns Dokument(
            "4444444",
            "P2000 - Krav om alderspensjon",
            emptyList()
        )
        every {
            vedleggService.hentDokumentInnhold(
                any(),
                any(),
                any()
            )
        } returns HentdokumentInnholdResponse("WVdKag==", "blah.pdf", "application/pdf")

        this.mockMvc!!.perform(put("/saf/vedlegg/1231231231231/111111/2222222/3333333/4444444/ARKIV"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("{\"success\": true}")))

        verify(exactly = 1) { vedleggService.leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { vedleggService.hentDokumentInnhold(any(), any(), any()) }
    }

    @Test
    fun `Kall som genererer HttpClientErrorException 403 skal kun returnere tekstlig melding uten 403`() {
        //given
        justRun { vedleggService.leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any()) }
        every { vedleggService.hentDokumentMetadata(any(), any(), any()) } returns mockk()
        val errorMelding = "Du har ikke tilgang til dette dokument-temaet. Kontakt nærmeste leder for å få tilgang."
        every {
            vedleggService.hentDokumentInnhold(
                any(),
                any(),
                any()
            )
        } throws HttpClientErrorException(HttpStatus.FORBIDDEN, errorMelding)

        // when: putVedleggTilDokument
        val result = mockMvc!!.perform(put("/saf/vedlegg/11/22/33/44/55/ARKIV"))
            .andExpect(status().isForbidden)
            .andReturn()

        //then: skal ha en feilmelding uten 403
        val errorBody: ErrorBody = mapJsonToAny(result.response.contentAsString)
        assertEquals(errorMelding, errorBody.error)
    }

    class ErrorBody(
        val success: Boolean,
        val error: String,
        val uuid: String
    )
}