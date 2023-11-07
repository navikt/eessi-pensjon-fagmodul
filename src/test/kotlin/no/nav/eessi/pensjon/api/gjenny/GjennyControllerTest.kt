package no.nav.eessi.pensjon.api.gjenny

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.gjenny"])
@WebMvcTest(GjennyController::class)
@MockkBean(InnhentingService::class)
class GjennyControllerTest {

    @MockkBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun get() {

        mockMvc.get("/gjenny/index").andExpect {
            status { isOk() }
            content {
                string("index")
            }
        }
    }

    @Test
    fun `hentBucerMedJournalforteSeder skal returnere en liste over bucer paa aktoerId`() {
        val fnrlev = "12345678901"
        val fnrdod = "12345678900"
        val endpointUrl = "/gjenny/bucer/fnrlev/$fnrlev/fnravdod/$fnrdod"

        every { euxInnhentingService.hentBucerGjenny(any()) } returns emptyList() andThen listOf(Rinasak("3216546987"))

        val result =  mockMvc.get(endpointUrl).andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        Assertions.assertEquals("[{\"id\":\"3216546987\",\"processDefinitionId\":null,\"traits\":null,\"applicationRoleId\":null,\"properties\":null,\"status\":null}]", response)

    }
}