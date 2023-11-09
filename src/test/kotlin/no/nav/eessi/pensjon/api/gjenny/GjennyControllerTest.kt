package no.nav.eessi.pensjon.api.gjenny

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.gjenny"])
@WebMvcTest(GjennyController::class)
class GjennyControllerTest {

    @MockkBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @MockkBean
    private lateinit var innhentingService: InnhentingService

    @Autowired
    private lateinit var mockMvc: MockMvc

//    @Test
//    fun `returnerer bucer for avdød`() {
//        val aktoerId = "12345678901"
//        val avdodfnr = "12345678900"
//        val endpointUrl = "/gjenny/rinasaker/$aktoerId/avdod/$avdodfnr"
//
//        val listeOverBucerForAvdod = listOf(BucView(
//                "12345678901", BucType.P_BUC_02, "12345678900", "12345678900", "12345678900", BucViewKilde.AVDOD
//            ))
//
//        every { euxInnhentingService.hentBucViewAvdodGjenny(any(), any()) } returns listeOverBucerForAvdod
//
//        val expected = """
//           "[{\"euxCaseId\":\"12345678901\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"12345678900\",\"saknr\":\"12345678900\",\"avdodFnr\":\"12345678900\",\"kilde\":\"AVDOD\"}]"
//        """.trimIndent()
//
//        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
//        Assertions.assertEquals(expected, result)
//    }

    @Test
    fun `test getRinasakerBrukerkontekstGjenny`() {
        every { innhentingService.hentFnrfraAktoerService(any()) } returns null
        every { innhentingService.hentRinaSakIderFraJoarksMetadata(any()) } returns listOf("12345")
        every { euxInnhentingService.lagBucViews(any(), any(), any(), any()) } returns emptyList()

        mockMvc.get("/gjenny/rinasaker/$123")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
            }
    }
}