package no.nav.eessi.pensjon.fagmodul.pesys

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PensjonsinformasjonUtlandControllerTest {

    private val gcpStorage = mockk<Storage>(relaxed = true)
    private val kodeverkClient = mockk<KodeverkClient>(relaxed = true)
    private val gcpStorageService = GcpStorageService(
        "_",
        "_",
        "_",
        "pesys",
        gcpStorage
    )
    private val euxInnhentingService = mockk<EuxInnhentingService>(relaxed = true)
    private val controller = PensjonsinformasjonUtlandController(pensjonsinformasjonUtlandService = mockk(), gcpStorageService = gcpStorageService, euxInnhentingService, kodeverkClient)
    private val aktoerId = "2477958344057"
    private val rinaNr = "1446033"

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("NO") } returns "NOR"
        every { kodeverkClient.finnLandkode("CY") } returns "CYR"
        every { kodeverkClient.finnLandkode("BG") } returns "BGD"
        every { kodeverkClient.finnLandkode("HR") } returns "HRD"

    }

    @Test
    fun `gitt en aktørid og rinanr som matcher trygdetid i gcp saa skal denne returneres`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns trygdeTidJson().toJson().toByteArray()
        }

        val result = controller.hentTrygdetid(aktoerId, rinaNr)
        println(result?.trygdetid.toString())
        assertEquals(aktoerId, result?.aktoerId)
        assertEquals(rinaNr, result?.rinaNr)
        assertEquals(trygdeTidListResultat(), result?.trygdetid.toString())
    }

    @Test
    fun `gitt en pesysId som finnes i gcp saa skal sedene henstes fra Rina`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer().toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(any(), any()) } returns hentTestP6000()

        val result = controller.hentP6000Detaljer("22975052")[0]
        assertEquals("112233445566", result.nav?.bruker?.person?.pin?.get(0)?.identifikator)
    }

    private fun p6000Detaljer() =
        """
            {
              "pesysId" : "22975052",
              "rinaSakId" : "1446704",
              "dokumentId" : [ "a6bacca841cf4c7195d694729151d4f3", "b152e3cf041a4b829e56e6b1353dd8cb" ]
            }
        """.trimIndent()


    fun trygdeTidListResultat(): String {
        return ("[Trygdetid(land=NOR, acronym=NAVAT07, type=21, startdato=2010-10-31, sluttdato=2010-12-01, aar=null, mnd=1, dag=2, dagtype=7, ytelse=111, ordning=00, beregning=111), " +
                "Trygdetid(land=CYR, acronym=NAVAT05, type=10, startdato=2016-01-01, sluttdato=2018-02-28, aar=2, mnd=2, dag=null, dagtype=7, ytelse=null, ordning=null, beregning=111), " +
                "Trygdetid(land=HRD, acronym=NAVAT05, type=10, startdato=2016-06-01, sluttdato=2016-06-22, aar=null, mnd=null, dag=21, dagtype=7, ytelse=001, ordning=00, beregning=111), " +
                "Trygdetid(land=BGD, acronym=NAVAT05, type=10, startdato=2020-01-01, sluttdato=2024-08-15, aar=null, mnd=3, dag=null, dagtype=7, ytelse=001, ordning=00, beregning=001), " +
                "Trygdetid(land=NOR, acronym=NAVAT07, type=41, startdato=2023-05-01, sluttdato=2023-05-31, aar=null, mnd=1, dag=null, dagtype=7, ytelse=null, ordning=null, beregning=null)]").trimMargin()
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

    private fun hentTestP6000(): SED {
        return javaClass.getResource("/json/sed/P6000-RINA.json")?.readText()?.let { json -> mapJsonToAny<P6000>(json) }!!
    }
}

