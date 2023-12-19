package no.nav.eessi.pensjon.integrationtest.buc

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Subject
import no.nav.eessi.pensjon.fagmodul.api.BucController
import no.nav.eessi.pensjon.fagmodul.eux.*
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(BucController::class)
@ContextConfiguration(classes = [BucControllerTest.Config::class, UnsecuredWebMvcTestLauncher::class])
@AutoConfigureMockMvc
class BucControllerTest {

    @Autowired
    private lateinit var innhentingService: InnhentingService

    @Autowired
    private lateinit var euxInnhentingService: EuxInnhentingService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @TestConfiguration
    class Config {
        @Bean
        fun euxInnhentingService() : EuxInnhentingService = mockk(relaxed = true)
        @Bean
        fun innhentingService() : InnhentingService = mockk(relaxed = true)
        @Bean
        fun bucController() = BucController(euxInnhentingService(), mockk( relaxed = true), innhentingService())
    }

    @Test
    fun `hentAvdodFraVedtak skal returerer en tom liste om vedtaksId er noe annet enn tall`(){
        val result = mvcPerform("/buc/rinasaker/111/saknr/222/vedtak/undefined")
        assertEquals("[]", result)
    }

    @Test
    fun `getRinasakerJoark skal returnere en liste over bucer paa aktoerId`() {
        val pesyssak = "123456"
        val aktoerId = "12666"
        val rinanummer = "1111"
        val endpointUrl = "/buc/joark/aktoer/$aktoerId/pesyssak/$pesyssak"
        val buc = Buc(id = rinanummer, processDefinitionName = P_BUC_01.name)

        every { innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)} returns listOf(rinanummer)
        every { euxInnhentingService.hentBucer(aktoerId, pesyssak, listOf(rinanummer)) } returns listOf(buc)

        val result = mvcPerform(endpointUrl)
        val expected = "[{\"processDefinitionName\":\"P_BUC_01\",\"id\":\"$rinanummer\"}]"

        JSONAssert.assertEquals(expected, result, false)
    }

    @Test
    fun `getBuc returnerer en Buc ut i fra et rinanummer`() {
        val rinanummer = "1111"
        val aktoerId = "12666"
        val endpointUrl = "/buc/$rinanummer"
        val buc = Buc(id = rinanummer, processDefinitionName = P_BUC_01.name)

        every { euxInnhentingService.getBuc(rinanummer) } returns buc
        every { innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)} returns listOf(rinanummer, "2222")

        val result = mvcPerform(endpointUrl)
        val expected = "{\"processDefinitionName\":\"P_BUC_01\",\"id\":\"$rinanummer\"}"

        JSONAssert.assertEquals(expected, result, false)
    }

    @Test
    fun `getbucs skal gi en liste både med og uten saksId`() {
        mvcPerform("/buc/bucs")
    }

    private fun mvcPerform(endpointUrl: String) : String {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get(endpointUrl)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        return result.response.getContentAsString(charset("UTF-8"))
    }

    @Test
    fun `Get gjenny cases for gjenlevende and avdod with kilde avdod`() {
        val aktoerId = "12345678901"
        val avdodFnr = "12345678900"
        val gjenlevendeFnr = "13084526251"
        val kilde = "AVDOD"
        val euxcaseIdAvdod = "123456"
        val euxcaseIdGjenlev = "2222222"
        val saknr = "null"

        val endpointUrl = "/buc/enkeldetalj/$euxcaseIdAvdod/aktoerid/$aktoerId/saknr/$saknr/avdodfnr/$avdodFnr/kilde/$kilde"

        val bucs01 = Buc(id = euxcaseIdGjenlev, subject = Subject(pid = gjenlevendeFnr), processDefinitionName = P_BUC_01.name)
        val bucs02 = Buc(id = euxcaseIdAvdod, subject = Subject(pid = avdodFnr), processDefinitionName = P_BUC_02.name)

        every { innhentingService.hentFnrfraAktoerService(any() ) } returns NorskIdent(avdodFnr)
        every { euxInnhentingService.hentBucOgDocumentIdAvdod(any()) } returns listOf(BucOgDocumentAvdod(euxcaseIdAvdod, bucs02, ""))
        every { euxInnhentingService.hentDocumentJsonAvdod(any()) } returns listOf(BucOgDocumentAvdod(euxcaseIdAvdod, bucs02, ""))
        every { euxInnhentingService.filterGyldigBucGjenlevendeAvdod(any(), any()) } returns listOf(bucs01, bucs02)
        every { euxInnhentingService.getBucAndSedViewWithBuc(any(), any(), any()) } returns listOf(
            BucAndSedView(
            type = "P_BUC_02",
            caseId = euxcaseIdAvdod,
            internationalId = "",
            subject = BucAndSedSubject(avdod = SubjectFnr( avdodFnr))
            ),
            BucAndSedView(
                type = "P_BUC_01",
                caseId = euxcaseIdGjenlev,
                internationalId = "",
                subject = BucAndSedSubject(avdod = SubjectFnr( avdodFnr))
            )
        )

        val result = mvcPerform(endpointUrl)

        val expected = """
            {"type":"P_BUC_02",
            "caseId":"123456",
            "internationalId":"",
            "creator":null,
            "sakType":null,
            "status":null,
            "startDate":null,
            "lastUpdate":null,
            "institusjon":null,
            "seds":null,
            "error":null,
            "readOnly":false,
            "subject":{
                "gjenlevende":null,
                "avdod":{
                    "fnr":"12345678900"
                    }
                }
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, result, false)

    }

}