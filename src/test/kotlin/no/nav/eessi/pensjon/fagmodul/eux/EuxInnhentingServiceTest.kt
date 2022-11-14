package no.nav.eessi.pensjon.fagmodul.eux

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.*
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

private const val SAKSNR = "1111111"
private const val FNR = "13057065487"
private const val AKTOERID = "1234568"
private const val INTERNATIONAL_ID = "e94e1be2daff414f8a49c3149ec00e66"

@SpringJUnitConfig(classes = [EuxInnhentingService::class])
internal class EuxInnhentingServiceTest {

    @MockkBean(name = "fagmodulEuxKlient")
    private lateinit var euxKlient: EuxKlient

    @Autowired
    private lateinit var euxInnhentingService: EuxInnhentingService


    @BeforeEach
    fun setUp() {
    }

    @Test
    fun `Sjekker at vi faar alle instanser ved kall til getbuc`() {
        val euxCaseId = "12345"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()

        every { euxKlient.getBucJson(any()) } returns json
        val result = euxInnhentingService.getBuc(euxCaseId)

        val creator = """
            {
              "organisation" : {
                "acronym" : "NAV ACCT 07",
                "countryCode" : "NO",
                "name" : "NAV ACCEPTANCE TEST 07",
                "id" : "NO:NAVAT07"
              }
            }
        """.trimIndent()

        val subject = """
            {
              "birthday" : "2019-03-13",
              "address" : null,
              "surname" : "STRIELA",
              "sex" : "f",
              "contactMethods" : null,
              "name" : "NYDELIG",
              "pid" : null,
              "id" : null,
              "title" : null,
              "age" : null
            }
        """.trimIndent()


        assertEquals(creator, result.creator?.toJson())
        assertEquals(subject, result.subject?.toJson())
        assertEquals(INTERNATIONAL_ID, result.internationalId)
        assertEquals("3893690", result.id)
        assertEquals("P_BUC_02", result.processDefinitionName)
        assertEquals("v4.2", result.processDefinitionVersion)
        assertEquals("2020-04-14T09:11:37.000+0000", result.lastUpdate)
        assertEquals("2020-04-14T09:01:39.537+0000", result.startDate)

    }

    @Test
    fun getBucViewBruker() {
        val euxCaseId = "3893690"
        val rinaSaker = listOf(Rinasak(euxCaseId, BucType.P_BUC_02.name, Traits(), "", Properties(), "open", internationalId = INTERNATIONAL_ID))
        every { euxKlient.getRinasaker(eq(FNR), any(), any(), eq( "\"open\"")) } returns rinaSaker

        val result = euxInnhentingService.getBucViewBruker(FNR, AKTOERID, SAKSNR)
        assertEquals(1, result.size)
        assertEquals(BucView(euxCaseId=euxCaseId, buctype= BucType.P_BUC_02, aktoerId= AKTOERID, saknr= SAKSNR, avdodFnr=null, kilde=BucViewKilde.BRUKER, internationalId= INTERNATIONAL_ID), result[0])
    }

    @Test
    fun getBucViewBrukerSaf() {
        val euxCaseId = "3893690"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()
        every { euxKlient.getBucJson(any()) } returns json

        val result = euxInnhentingService.getBucViewBrukerSaf(AKTOERID, SAKSNR, listOf(euxCaseId))
        assertEquals(1, result.size)
        assertEquals(BucView(euxCaseId=euxCaseId, buctype= BucType.P_BUC_02, aktoerId= AKTOERID, saknr= SAKSNR, avdodFnr=null, kilde=BucViewKilde.SAF, internationalId= INTERNATIONAL_ID), result[0])
    }
}