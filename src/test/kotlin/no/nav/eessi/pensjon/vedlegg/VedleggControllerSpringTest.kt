package no.nav.eessi.pensjon.vedlegg

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.EessiFagmodulApplicationUnsecureTest
import no.nav.eessi.pensjon.vedlegg.client.HentdokumentInnholdResponse
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@SpringBootTest(classes = [EessiFagmodulApplicationUnsecureTest::class] ,webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class VedleggControllerSpringTest {

    @MockBean
    lateinit var vedleggService: VedleggService

    @Autowired
    private val mockMvc: MockMvc? = null

    @Test
    @Throws(Exception::class)
    fun shouldReturnDefaultMessage() {
        doNothing().`when`(vedleggService).leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any())
        doReturn(HentdokumentInnholdResponse("WVdKag==","blah.pdf", "application/pdf")).`when`(vedleggService).hentDokumentInnhold(any(), any(), any())

        this.mockMvc!!.perform(put("/saf/vedlegg/1231231231231/111111/2222222/3333333/4444444/ARKIV"))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("{\"success\": true}")))

        verify(vedleggService, times(1)).leggTilVedleggPaaDokument(any(), any(), any(), any(), any(), any())
        verify(vedleggService, times(1)).hentDokumentInnhold(any(), any(), any())
    }
}