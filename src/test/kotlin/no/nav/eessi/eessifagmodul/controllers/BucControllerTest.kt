package no.nav.eessi.eessifagmodul.controllers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.eux.BucUtils
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.eux.RinaAksjon
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner.Silent::class)
class BucControllerTest {

    @Mock
    lateinit var mockEuxService: EuxService

    @Mock
    lateinit var mockBucUtils: BucUtils

    @Mock
    lateinit var mockAktoerregisterService: AktoerregisterService

    private lateinit var bucController: BucController

    @Before
    fun GetItOn() {
        this.bucController = BucController(mockEuxService, mockAktoerregisterService)
        doReturn("12105033302").whenever(mockAktoerregisterService).hentGjeldendeNorskIdentForAktorId(ArgumentMatchers.anyString())
    }

//    @Test
//    fun getMuligeAksjonerUtenFilter() {
//        val filepath = "src/test/resources/json/aksjoner/noen_aksjoner.json"
//        val json = String(Files.readAllBytes(Paths.get(filepath)))
//        assertTrue(validateJson(json))
//        val mockaksjoner = mapJsonToAny(json, typeRefs<List<RinaAksjon>>())
//
//        doReturn(mockaksjoner).whenever(mockBucUtils).getRinaAksjon()
//        doReturn(mockBucUtils).whenever(mockEuxService).getBucUtils(
//                ArgumentMatchers.anyString()
//        )
//
//        val result = bucController.getMuligeAksjoner("12345666777")
//        assertEquals(4, result.size)
//
//
//    }
//
//    @Test
//    fun getMuligeAksjonerMedFilter() {
//        val filepath = "src/test/resources/json/aksjoner/noen_aksjoner.json"
//        val json = String(Files.readAllBytes(Paths.get(filepath)))
//        assertTrue(validateJson(json))
//        val mockaksjoner = mapJsonToAny(json, typeRefs<List<RinaAksjon>>())
//
//        doReturn(mockaksjoner).whenever(mockBucUtils).getRinaAksjon()
//        doReturn(mockBucUtils).whenever(mockEuxService).getBucUtils(
//                ArgumentMatchers.anyString()
//        )
//        val result = bucController.getMuligeAksjoner("12345666777", "P")
//        assertEquals(3, result.size)
//    }


    @Test
    fun getYtelseKravtypeOk() {
        //val mockSedKrav = SED(sed = "P15000", sedGVer = "4", sedVer = "1", nav = Nav(krav = Krav(dato = "2019-02-01", type = "01")))
        val mockKrav = Krav(dato = "2019-02-01", type = "01")

        doReturn(mockKrav).whenever(mockEuxService).
                hentYtelseKravtype(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString()
                )

        val mockResult = bucController.getYtelseKravtype("12123", "3123123")

        assertEquals("01", mockResult.type)
        assertEquals("2019-02-01", mockResult.dato)
    }


}