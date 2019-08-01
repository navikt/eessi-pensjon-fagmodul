package no.nav.eessi.pensjon.fagmodul.prefill.tps

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.services.geo.LandkodeService
import no.nav.eessi.pensjon.services.geo.PostnummerService
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.utils.createXMLCalendarFromString
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Sivilstand
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Sivilstander
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals

@RunWith(MockitoJUnitRunner::class)
class PrefillPersonDataFromTPSTest{

    @Mock
    private lateinit var personV3Service: PersonV3Service

    private val postnummerService = PostnummerService()

    private val landkodeService = LandkodeService()

    private val eessiInfo = EessiInformasjon()

    private lateinit var prefillPersonFromTPS: PrefillPersonDataFromTPS

    private val prefillAdresse = PrefillAdresse(postnummerService, landkodeService)

    @Before
    fun bringItOnDude() {

        prefillPersonFromTPS = PrefillPersonDataFromTPS(personV3Service, prefillAdresse, eessiInfo)

    }

    @Test
    fun testPaahentSivilstandArr9999BlirBlank() {
        val brukerTps = mockBrukerSivilstandTps("9999-01-01", "SKPQ")

        val actualList = prefillPersonFromTPS.hentSivilstand(brukerTps)
        val actual = actualList[0]

        assertEquals("", actual.fradato)
        assertEquals(null, actual.status)
    }

    @Test
    fun testPaahentSivilstandOk() {
        val brukerTps = mockBrukerSivilstandTps("1999-01-01", "UGIF")

        val actualList = prefillPersonFromTPS.hentSivilstand(brukerTps)
        val actual = actualList[0]

        assertEquals("1999-01-01", actual.fradato)
        assertEquals("01", actual.status)
    }

    @Test
    fun testPaahentSivilstandAar2099() {
        val brukerTps = mockBrukerSivilstandTps("2499-12-01", "REPA")

        val actualList = prefillPersonFromTPS.hentSivilstand(brukerTps)
        val actual = actualList[0]

        assertEquals("2499-12-01", actual.fradato)
        assertEquals("04", actual.status)
    }


    private fun mockBrukerSivilstandTps(gyldigPeriode: String, sivilstandType: String): Bruker {
        val brukerTps = Bruker()
        brukerTps.endretAv = "Test"
        brukerTps.foedested = "OSLO"

        val personnavnTps = Personnavn()
        personnavnTps.mellomnavn = "Dummy Absurd"
        personnavnTps.fornavn = "Dummy"
        personnavnTps.etternavn = "Absurd"
        brukerTps.personnavn = personnavnTps

        val sivilstandTps = Sivilstand()
        sivilstandTps.endretAv = "Test"
        sivilstandTps.fomGyldighetsperiode = createXMLCalendarFromString(gyldigPeriode)

        val sivilstanderTps = Sivilstander()
        sivilstanderTps.value = sivilstandType

        sivilstandTps.sivilstand = sivilstanderTps
        brukerTps.sivilstand = sivilstandTps

        return brukerTps
    }

}