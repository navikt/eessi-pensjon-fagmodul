package no.nav.eessi.pensjon.fagmodul.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

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
        val expected = """{"result":"Sed ble IKKE sendt til Rina","status":"BAD_REQUEST","message":null,"stackTrace":null}"""
        val endpointUrl = "/eux/buc/12345/sed/dokid/send"
        every { euxKlient.sendSed(any(), any()) } returns false

        val result = mockMvc.post(endpointUrl).andReturn().response

        assertEquals(expected, result.contentAsString)

    }

    @Test
    fun `Hent påkoblede land som institusjoner`() {
        val expected = """ {"result":"[ \"NO\", \"SE\" ]","status":"OK","message":null,"stackTrace":null}""".trimIndent()

        val institusjoner = listOf(
            InstitusjonItem("NO", "institusjonId", "institusjonNavn"),
            InstitusjonItem("SE", "SweInstitusjonId", "SweInstitusjonNavn"),
        )

        every { euxInnhentingService.getInstitutions(any()) } returns institusjoner

        val result = mockMvc.get("/eux/countries/P_BUC_06")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        assertEquals(expected, result)

    }

    @Test
    fun `getEuxInstitusjoner returnerer en liste over institusjoner med detaljer`() {
        val expected = """
            {"result":[{"country":"DK","institution":"DKInstitusjonId","name":"DKInstitusjonNavn","acronym":null},{"country":"BE","institution":"BEInstitusjonId","name":"BEInstitusjonNavn","acronym":null}],"status":"OK","message":null,"stackTrace":null}
        """.trimIndent()

        val institusjoner = listOf(
            InstitusjonItem("DK", "DKInstitusjonId", "DKInstitusjonNavn"),
            InstitusjonItem("BE", "BEInstitusjonId", "BEInstitusjonNavn"),
        )

        every { euxInnhentingService.getInstitutions(any()) } returns institusjoner

        val result = mockMvc.get("/eux/institutions/P_BUC_01")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        assertEquals(expected, result)
    }

    @Test
    fun `resendtDokumenter skal returnere liste over dokumenter som er sendt over`() {
        val expected = """
            {"result":"Seder er resendt til Rina","status":"OK","message":null,"stackTrace":null}
        """.trimIndent()

        every { euxInnhentingService.reSendRinasaker(any()) } returns EuxKlientLib.HentResponseBody(status = HttpStatus.OK,)
        val expectedresult = """452061_5120d7d59ae548a4a980fe93eb58f9bd_1"""
        val result = mockMvc.perform(post("/eux/resend/liste")
            .contentType(MediaType.APPLICATION_JSON)
            .content(expectedresult.toJson()))
            .andReturn().response.contentAsString

        assertEquals(expected, result)
    }

    @Test
    fun `resendeDokumenterMedRinaId skal returnere liste over dokumenter som er sendt over`() {
        val expected = """
            {"result":"Seder er resendt til Rina","status":"OK","message":null,"stackTrace":null}
        """.trimIndent()

        every { euxInnhentingService.reSendeRinasakerMedRinaId(eq("123456"), eq("452061_5120d7d59ae548a4a980fe93eb58f9bd_1")) } returns EuxKlientLib.HentResponseBody(status = HttpStatus.OK,)
        val expectedresult = """"123456", 452061_5120d7d59ae548a4a980fe93eb58f9bd_1"""
        val result = mockMvc.perform(post("/eux/resend/buc/123456/sed/452061_5120d7d59ae548a4a980fe93eb58f9bd_1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(expectedresult.toJson()))
            .andReturn().response.contentAsString

        assertEquals(expected, result)
    }

    @Test
    fun `sendSedMedMottakere skal sende sed med mottakere`() {
        val expected = """{"result":"Sed er sendt til Rina","status":"OK","message":null,"stackTrace":null}"""
        val requestBody = listOf("NO:974652382", "NO:NAVAT05", "NO:NAVAT04")
        val params: MultiValueMap<String, String> = LinkedMultiValueMap()
        params.add("rinasakId", "123456")
        params.add("dokumentId", "452061_5120d7d59ae548a4a980fe93eb58f9bd_1")
        every { euxInnhentingService.sendSedTilMottakere(eq("123456"),eq("452061_5120d7d59ae548a4a980fe93eb58f9bd_1"), any()) } returns true

        val result = mockMvc.perform(post("/eux/buc/123456/sed/452061_5120d7d59ae548a4a980fe93eb58f9bd_1/sendto")
            .contentType(MediaType.APPLICATION_JSON)
            .params(params)
            .content(requestBody.toJson()))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString

        assertEquals(expected, result)
    }

}



