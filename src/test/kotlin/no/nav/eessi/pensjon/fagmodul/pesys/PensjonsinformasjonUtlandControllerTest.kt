package no.nav.eessi.pensjon.fagmodul.pesys

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandController.TrygdetidRequest
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
    private val controller = PensjonsinformasjonUtlandController(
        pensjonsinformasjonUtlandService = mockk(),
        gcpStorageService = gcpStorageService,
        euxInnhentingService,
        kodeverkClient,
        mockk(relaxed = true)
    )
    private val aktoerId1 = "2477958344057"
    private val aktoerId2 = "2588058344011"
    private val rinaNr = 1446033

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
            every { name } returns "${aktoerId1}___PESYS___$rinaNr"
            every { getContent() } returns trygdeTidJson().toJson().toByteArray()
        }
        mockGcpListeSok(listOf("$rinaNr"))

        val retval = controller.hentTrygdetid(TrygdetidRequest(fnr = aktoerId1, rinaNr = rinaNr))
        println(retval?.toJson())
        retval?.forEach { result ->
            assertEquals(aktoerId1, result.fnr)
            assertEquals(rinaNr, result.rinaNr)
            assertEquals(trygdeTidListResultat(), result.trygdetid.toString())
        }
    }

    @Test
    fun `gitt en samlet periode med flag fra gcp saa skal ogsaa denne hente ut trygdetid`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { name } returns "${aktoerId1}___PESYS___$rinaNr"
            every { getContent() } returns trygdeTidSamletJson().toJson().toByteArray()
        }

        mockGcpListeSok(listOf("$rinaNr"))

        val result = controller.hentTrygdetid(TrygdetidRequest(fnr = aktoerId1))
        val forventertResultat = "[Trygdetid(land=, acronym=NAVAT05, type=10, startdato=1995-01-01, sluttdato=1995-12-31, aar=1, mnd=0, dag=1, dagtype=7, ytelse=111, ordning=null, beregning=111)]"
        assertEquals(rinaNr, result?.firstOrNull()?.rinaNr)
        assertEquals(forventertResultat, result?.firstOrNull()?.trygdetid.toString())
    }

    @Test
    fun `gitt en aktorid tilknyttet flere buc saa skal den gi en liste med flere trygdetider`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { name } returns "${aktoerId1}___PESYS___111111" andThen "${aktoerId1}___PESYS___222222"
            every { getContent() } returns trygdeTidSamletJson().toJson().toByteArray()
        }

        mockGcpListeSok(listOf("111111", "222222"))

        val result = controller.hentTrygdetid(TrygdetidRequest(fnr = aktoerId1))
        val forventertResultat = "[Trygdetid(land=, acronym=NAVAT05, type=10, startdato=1995-01-01, sluttdato=1995-12-31, aar=1, mnd=0, dag=1, dagtype=7, ytelse=111, ordning=null, beregning=111)]"
        assertEquals(111111, result?.get(0)?.rinaNr)
        assertEquals(222222, result?.get(1)?.rinaNr)
        assertEquals(trygdeTidForFlereBuc(), result?.toJson())
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

    private fun mockGcpListeSok(rinaNrList: List<String>) {
        val blobs = rinaNrList.map { rinaNr ->
            val blob = mockk<Blob>(relaxed = true)
            every { blob.name } returns "${aktoerId1}___PESYS___$rinaNr"
            blob
        }
        val page = mockk<Page<Blob>>(relaxed = true)
        every { page.iterateAll() } returns blobs
        every { gcpStorage.list(any<String>(), *anyVararg()) } returns page
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

    private fun trygdeTidForFlereBuc(): String {
        return """
            [ {
              "fnr" : "2477958344057",
              "rinaNr" : 111111,
              "trygdetid" : [ {
                "land" : "",
                "acronym" : "NAVAT05",
                "type" : "10",
                "startdato" : "1995-01-01",
                "sluttdato" : "1995-12-31",
                "aar" : "1",
                "mnd" : "0",
                "dag" : "1",
                "dagtype" : "7",
                "ytelse" : "111",
                "ordning" : null,
                "beregning" : "111"
              } ],
              "error" : null
            }, {
              "fnr" : "2477958344057",
              "rinaNr" : 222222,
              "trygdetid" : [ {
                "land" : "",
                "acronym" : "NAVAT05",
                "type" : "10",
                "startdato" : "1995-01-01",
                "sluttdato" : "1995-12-31",
                "aar" : "1",
                "mnd" : "0",
                "dag" : "1",
                "dagtype" : "7",
                "ytelse" : "111",
                "ordning" : null,
                "beregning" : "111"
              } ],
              "error" : null
            } ]
        """.trimIndent()

    }

    private fun trygdeTidSamletJson(): String {
        val trygdetidList = """
            [
                {"flag":true,"land":"GB","acronym":"NAVAT05","type":"10","startdato":"1995-01-01","sluttdato":"1995-12-31","aar":"1","mnd":"0","dag":"1","dagtype":"7","ytelse":"111","ordning":"","beregning":"111","hasSubrows":true,"flagLabel":"OBS! Periodesum er mindre enn registrert periode"}
            ]
            """.trimIndent()
        return trygdetidList
    }

    private fun hentTestP6000(): SED {
        return javaClass.getResource("/json/sed/P6000-RINA.json")?.readText()?.let { json -> mapJsonToAny<P6000>(json) }!!
    }
}

