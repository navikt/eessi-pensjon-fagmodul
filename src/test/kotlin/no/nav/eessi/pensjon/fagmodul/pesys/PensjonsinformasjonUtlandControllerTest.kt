package no.nav.eessi.pensjon.fagmodul.pesys

import com.google.cloud.storage.Blob
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class PensjonsinformasjonUtlandControllerTest {

    private val gcpStorage = mockk<Storage>(relaxed = true)
    private val gcpStorageService = GcpStorageService(
        "_",
        "_",
        "pesys",
        gcpStorage
    )
    private val controller = PensjonsinformasjonUtlandController(pensjonsinformasjonUtlandService = mockk(), gcpStorageService = gcpStorageService)
    private val aktoerId = "2477958344057"
    private val rinaNr = "1446033"

    @Test
    fun `gitt en aktørid og rinanr som matcher trygdetid i gcp saa skal denne returneres`() {
        val trygdetidList = trygdeTidJson()
        every { gcpStorage.list("pesys").iterateAll() } returns createMockBlobList(aktoerId, rinaNr, listOf(trygdetidList))

        val result = controller.hentTrygdetid(aktoerId, rinaNr)

        assertEquals(aktoerId, result?.aktoerId)
        assertEquals(rinaNr, result?.rinaNr)
        assertEquals(trygdeTidListResultat(), result?.trygdetid)
    }

    @Test
    fun `gitt flere resulatat fra gcp saa skal det returneres tom liste og feilmelding`() {
        val trygdetidList = listOf("""[{"land":"SE","acronym":"NAVAT08"}]""", """[{"land":"NO","acronym":"NAVAT07"}]""")
        every { gcpStorage.list("pesys").iterateAll() } returns
                createMockBlobList(aktoerId, rinaNr, trygdetidList)

        val result = controller.hentTrygdetid(aktoerId, rinaNr)
        assertNotNull(result)
        assertEquals("Det er registrert 2 trygdetid for rinaNr: $rinaNr, aktoerId: $aktoerId, gir derfor ingen trygdetid tilbake.", result?.error)
    }

    fun trygdeTidListResultat(): String {
        return """"[\n  {\"land\":\"NO\",\"acronym\":\"NAVAT07\",\"type\":\"21\",\"startdato\":\"2010-10-31\",\"sluttdato\":\"2010-12-01\",\"aar\":\"\",\"mnd\":\"1\",\"dag\":\"2\",\"dagtype\":\"7\",\"ytelse\":\"111\",\"ordning\":\"00\",\"beregning\":\"111\"},\n  {\"land\":\"CY\",\"acronym\":\"NAVAT05\",\"type\":\"10\",\"startdato\":\"2016-01-01\",\"sluttdato\":\"2018-02-28\",\"aar\":\"2\",\"mnd\":\"2\",\"dag\":\"\",\"dagtype\":\"7\",\"ytelse\":\"\",\"ordning\":\"\",\"beregning\":\"111\"},\n  {\"land\":\"HR\",\"acronym\":\"NAVAT05\",\"type\":\"10\",\"startdato\":\"2016-06-01\",\"sluttdato\":\"2016-06-22\",\"aar\":\"\",\"mnd\":\"\",\"dag\":\"21\",\"dagtype\":\"7\",\"ytelse\":\"001\",\"ordning\":\"00\",\"beregning\":\"111\"},\n  {\"land\":\"BG\",\"acronym\":\"NAVAT05\",\"type\":\"10\",\"startdato\":\"2020-01-01\",\"sluttdato\":\"2024-08-15\",\"aar\":\"\",\"mnd\":\"3\",\"dag\":\"\",\"dagtype\":\"7\",\"ytelse\":\"001\",\"ordning\":\"00\",\"beregning\":\"001\"},\n  {\"land\":\"NO\",\"acronym\":\"NAVAT07\",\"type\":\"41\",\"startdato\":\"2023-05-01\",\"sluttdato\":\"2023-05-31\",\"aar\":\"\",\"mnd\":\"1\",\"dag\":\"\",\"dagtype\":\"7\",\"ytelse\":\"\",\"ordning\":\"\",\"beregning\":\"\"}\n]""""
    }
    private fun trygdeTidJson(): String {
        val trygdetidList = """
            [
              {"land":"NO","acronym":"NAVAT07","type":"21","startdato":"2010-10-31","sluttdato":"2010-12-01","aar":"","mnd":"1","dag":"2","dagtype":"7","ytelse":"111","ordning":"00","beregning":"111"},
              {"land":"CY","acronym":"NAVAT05","type":"10","startdato":"2016-01-01","sluttdato":"2018-02-28","aar":"2","mnd":"2","dag":"","dagtype":"7","ytelse":"","ordning":"","beregning":"111"},
              {"land":"HR","acronym":"NAVAT05","type":"10","startdato":"2016-06-01","sluttdato":"2016-06-22","aar":"","mnd":"","dag":"21","dagtype":"7","ytelse":"001","ordning":"00","beregning":"111"},
              {"land":"BG","acronym":"NAVAT05","type":"10","startdato":"2020-01-01","sluttdato":"2024-08-15","aar":"","mnd":"3","dag":"","dagtype":"7","ytelse":"001","ordning":"00","beregning":"001"},
              {"land":"NO","acronym":"NAVAT07","type":"41","startdato":"2023-05-01","sluttdato":"2023-05-31","aar":"","mnd":"1","dag":"","dagtype":"7","ytelse":"","ordning":"","beregning":""}
            ]
            """.trimIndent()
        return trygdetidList
    }

    private fun createMockBlobList(aktorid: String, rinaNr: String, trygdeTidList: List<String>): List<Blob> {
        return trygdeTidList.map { trygdeTid ->
            mockk<Blob>().apply {
                every { name } returns "${aktorid}___PESYS___$rinaNr"
                every { createTimeOffsetDateTime } returns OffsetDateTime.now().minusDays(12)
                every { blobId } returns mockk(relaxed = true)
                every { getContent() } returns trygdeTid.toJson().toByteArray()
            }
        }
    }
}