package no.nav.eessi.eessifagmodul.pesys

import no.nav.eessi.eessifagmodul.metrics.TimingService
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ResourceUtils
import java.time.LocalDate
import kotlin.test.*

@RunWith(MockitoJUnitRunner::class)
class PensjonsinformasjonUtlandControllerTest {

    val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonUtlandControllerTest::class.java) }

    @Mock
    lateinit var controller: PensjonsinformasjonUtlandController

    @Mock
    lateinit var timingService: TimingService

    @Before
    fun bringItOn() {
        controller = PensjonsinformasjonUtlandController(timingService)
    }

    @Test
    fun mockPutKravUtland() {
        val buckey = 999
        val resource = ResourceUtils.getFile("classpath:json/pesys/kravutlandalderpen.json").readText()
        val testdata = mapJsonToAny(resource, typeRefs<KravUtland>())

        //feil med ikke godtatt id
        controller.mockPutKravUtland(buckey, testdata)
        val svar = controller.hentKravUtland(buckey)
        assertEquals(testdata, svar)
        val set = controller.mockGetKravUtlandKeys()
        assertEquals(1, set.size)
        assertEquals(buckey, set.first())

        //rydder opp
        controller.mockDeleteKravUtland(buckey)

        //prøver å hente ut som skal feile.
        try {
            controller.hentKravUtland(buckey)
            fail("skal ikke komme hit")
        } catch (ex: Exception) {
            assertTrue(true)
            ex.printStackTrace()
        }
    }

    @Test
    fun mockDeleteKravUtland() {
        //skal være tom, 0 i set
        assertEquals(0, controller.mockGetKravUtlandKeys().size)
        //legger til
        controller.mockPutKravUtland(940, KravUtland())
        //fjerner
        controller.mockDeleteKravUtland(940)
        //skal være tom, 0 i set
        assertEquals(0, controller.mockGetKravUtlandKeys().size)
    }

    @Test
    fun mockGetKravUtlandKeys() {
        val set = controller.mockGetKravUtlandKeys()
        set.forEach {
            logger.info("" + it)
        }
        assertEquals(0, set.size)
    }

    @Test
    fun hentKravUtlandMockKravUtland() {
        val resource = ResourceUtils.getFile("classpath:json/pesys/kravutlandalderpen.json").readText()
        val testdata = mapJsonToAny(resource, typeRefs<KravUtland>())

        val buckey1 = 910
        controller.mockPutKravUtland(buckey1, testdata)

        val svar1 = controller.hentKravUtland(buckey1)
        assertEquals(testdata, svar1)

        val buckey2 = 920
        controller.mockPutKravUtland(buckey2, testdata)

        val svar2 = controller.hentKravUtland(buckey2)
        assertEquals(testdata, svar2)

        val set = controller.mockGetKravUtlandKeys()
        assertEquals(2, set.size)

        controller.mockDeleteKravUtland(910)
        controller.mockDeleteKravUtland(920)

        assertEquals(0, controller.mockGetKravUtlandKeys().size)
    }

    @Test
    fun hentKravUtlandMockBuc() {
        val response = controller.hentKravUtland(1099)
        assertNotNull(response)
        assertEquals("50", response.uttaksgrad)
        assertEquals("2019-03-11", response.mottattDato.toString())
        assertEquals("SWE", response.personopplysninger?.statsborgerskap)
        assertEquals("SWE", response.soknadFraLand)
        assertEquals(true, response.vurdereTrygdeavtale)

        assertEquals("BRUKER", response.initiertAv)

        assertEquals("UGIF", response.sivilstand?.valgtSivilstatus)
        //assertEquals("2019-01-24", response.sivilstand?.sivilstatusDatoFom.toString())

        val utland = response.utland
        //assertEquals(true, utland?.harOpphold)
        assertEquals(3, utland?.utlandsopphold?.size)

        val utlandEn = utland?.utlandsopphold?.get(0)
        assertEquals("DEU", utlandEn?.land)
        assertEquals("1960-01-01", utlandEn?.fom.toString())
        assertEquals("1965-01-01", utlandEn?.tom.toString())
        assertEquals(false, utlandEn?.bodd)
        assertEquals(true, utlandEn?.arbeidet)
        assertEquals("10010101010", utlandEn?.utlandPin)

        val utlandTo = utland?.utlandsopphold?.get(1)
        assertEquals("DNK", utlandTo?.land)
        assertEquals("2003-01-01", utlandTo?.fom.toString())
        assertEquals("2004-01-01", utlandTo?.tom.toString())
        assertEquals(true, utlandTo?.bodd)
        assertEquals(false, utlandTo?.arbeidet)
        assertEquals("23456789001", utlandTo?.utlandPin)

        val utlandTre = utland?.utlandsopphold?.get(2)
        assertEquals("DNK", utlandTre?.land)
        assertEquals("2002-01-01", utlandTre?.fom.toString())
        assertEquals(null, utlandTre?.tom)
        assertEquals(true, utlandTre?.bodd)
        assertEquals(false, utlandTre?.arbeidet)
        assertEquals("23456789001", utlandTre?.utlandPin)

        val json = mapAnyToJson(response)
        logger.info(json)
    }

    @Test
    fun hentKravUtlandManglerUttaksgradMockBuc() {
        val response = controller.hentKravUtland(1050)
        assertNotNull(response)
        assertNull(response.uttaksgrad)
    }


    @Test
    fun testLocalDatetilJson() {

        val test = KravUtland(
                mottattDato = LocalDate.now(),
                iverksettelsesdato = LocalDate.now(),
                utland = SkjemaUtland(
                        utlandsopphold = null
                ),
                uttaksgrad = "60",
                vurdereTrygdeavtale = false,
                personopplysninger = SkjemaPersonopplysninger(
                        statsborgerskap = "SWE"
                )
        )
        val json = mapAnyToJson(test)
        logger.info(json)


    }

}