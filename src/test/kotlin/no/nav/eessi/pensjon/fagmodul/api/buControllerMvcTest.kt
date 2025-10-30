package no.nav.eessi.pensjon.fagmodul.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ActiveProfiles("unsecured-webmvctest")
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.fagmodul.api"])
@WebMvcTest(BucController::class)
@MockkBeans(
    MockkBean(name = "euxInnhentingService", classes = [EuxInnhentingService::class], relaxed = true),
    MockkBean(name = "auditlogging", classes = [AuditLogger::class], relaxed = true),
    MockkBean(name = "innhentingService", classes = [InnhentingService::class], relaxed = true),
    MockkBean(name = "prefillController", classes = [PrefillController::class], relaxed = true),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class], relaxed = true),
)

class BucControllerMVCTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @SpykBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @SpykBean
    private lateinit var innhentingService: InnhentingService

    @SpykBean
    private lateinit var gcpStorageService: GcpStorageService

    @Test
    fun `Ved kall til getRinasakerBrukerkontekst skal det returneres en liste med bucview`() {
        val expected = """{"result":[{"euxCaseId":"123456","buctype":"P_BUC_01","aktoerId":"1234567891012","saknr":"1234567890","avdodFnr":null,"kilde":"BRUKER"},{"euxCaseId":"321654","buctype":"P_BUC_02","aktoerId":"3216548268656","saknr":"654258","avdodFnr":null,"kilde":"BRUKER"}],"status":"OK","message":null,"stackTrace":null}""".trimIndent()
        val aktoerId = "1234567891012"
        val saknr = "1234567890"
        val bucViewBruker = listOf(EuxInnhentingService.BucView(
            euxCaseId = "123456",
            buctype = P_BUC_01,
            aktoerId = aktoerId,
            saknr = saknr,
            avdodFnr = null,
            kilde = mockk(relaxed = true),
        ))

        val bucviews = listOf(EuxInnhentingService.BucView(
            euxCaseId = "321654",
            buctype = P_BUC_02,
            aktoerId = "3216548268656",
            saknr = "654258",
            avdodFnr = null,
            kilde = mockk(relaxed = true)
        ))

        every { innhentingService.hentFnrfraAktoerService(any()) } returns NorskIdent("12345678910")
        every { innhentingService.hentRinaSakIderFraJoarksMetadata(any()) } returns listOf(("12345678910"))
        every { euxInnhentingService.hentBucViewBruker(any(), any(), any()) } returns bucViewBruker
        every { euxInnhentingService.lagBucViews(any(), any(), any(), any()) } returns bucviews
        every { gcpStorageService.gjennySakFinnes(any()) } returns false


        val result = mockMvc.get("/buc/rinasaker/1234567891012/saknr/123456")
            .andReturn().response.contentAsString

        println("result = $result")

        assertEquals(expected, result)

    }


    @Test
    fun `Ved hentBucerMedJournalforteSeder returneres `() {
        val expected = """{"result":[{"id":"","startDate":null,"lastUpdate":null,"status":null,"subject":null,"creator":null,"documents":null,"participants":null,"processDefinitionName":"P_BUC_01","applicationRoleId":null,"businessId":null,"internationalId":null,"processDefinitionVersion":null,"comments":null,"actions":null,"attachments":null,"sensitive":null,"sensitiveCommitted":null}],"status":"OK","message":null,"stackTrace":null}"""
        val bucListe = listOf(Buc(id = "", processDefinitionName = "P_BUC_01"))

        every { gcpStorageService.gjennySakFinnes(any()) } returns false
        every { euxInnhentingService.hentBucer(any(), any(), any()) } returns bucListe

        val result = mockMvc.get("/buc/joark/aktoer/12345678912/pesyssak/123456")
            .andReturn().response.contentAsString

        assertEquals(expected, result)

    }


    @Test
    fun tester2() {
        val expected = """{"result":["P_BUC_01","P_BUC_02","P_BUC_03","P_BUC_05","P_BUC_06","P_BUC_09","P_BUC_10","P_BUC_04","P_BUC_07","P_BUC_08"],"status":"OK","message":null,"stackTrace":null}"""

        every { gcpStorageService.gjennySakFinnes(any()) } returns false

        val result = mockMvc.get("/buc/bucs")
            .andReturn().response.contentAsString

        assertEquals(expected, result)

    }



}