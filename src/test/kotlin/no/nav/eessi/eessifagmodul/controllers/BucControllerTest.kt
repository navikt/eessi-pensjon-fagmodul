package no.nav.eessi.eessifagmodul.controllers

//@RunWith(MockitoJUnitRunner.Silent::class)
class BucControllerTest {
//
//    @Mock
//    lateinit var mockEuxService: EuxService
//
//    @Mock
//    lateinit var mockBucUtils: BucUtils
//
//    @Mock
//    lateinit var mockAktoerregisterService: AktoerregisterService
//
//    private lateinit var bucController: BucController
//
//    @Before
//    fun GetItOn() {
//        this.bucController = BucController(mockEuxService, mockAktoerregisterService)
//        doReturn("12105033302").whenever(mockAktoerregisterService).hentGjeldendeNorskIdentForAktorId(ArgumentMatchers.anyString())
//    }

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
//        val mockaksjoner = mapJsonToAny(json, typeRefs<List<RinaAksjon>>())
//
//        doReturn(mockaksjoner).whenever(mockBucUtils).getRinaAksjon()
//        doReturn(mockBucUtils).whenever(mockEuxService).getBucUtils(
//                ArgumentMatchers.anyString()
//        )
//        val result = bucController.getMuligeAksjoner("12345666777", "P")
//        assertEquals(3, result.size)
//    }


}