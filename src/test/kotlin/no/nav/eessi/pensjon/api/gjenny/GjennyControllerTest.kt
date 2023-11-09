package no.nav.eessi.pensjon.api.gjenny

//@ActiveProfiles(profiles = ["unsecured-webmvctest"])
//@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.gjenny"])
//@WebMvcTest(GjennyController::class)
//@MockkBean(InnhentingService::class)
//class GjennyControllerTest {
//
//    @MockkBean
//    private lateinit var euxInnhentingService: EuxInnhentingService
//
//    @Autowired
//    private lateinit var mockMvc: MockMvc
//
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
//
//
//    }
//}