package no.nav.eessi.pensjon.fagmodul.prefill.person

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother.initialPrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefillSedEnkeTest {

    private lateinit var personDataFromTPS: MockTpsPersonServiceFactory
    private lateinit var pensjonsinformasjonService: PensjonsinformasjonService
    private lateinit var pensjonsinformasjonServiceGjen: PensjonsinformasjonService
    private val pesysSaksnummer = "21975717"

    private val fnr = generateRandomFnr(67)
    private val b1fnr = generateRandomFnr(37)
    private val b2fnr =  generateRandomFnr(17)

    @BeforeEach
    fun setup() {
        personDataFromTPS = MockTpsPersonServiceFactory(
                setOf(
                        MockTpsPersonServiceFactory.MockTPS("Person-20000.json", fnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                        MockTpsPersonServiceFactory.MockTPS("Person-21000.json", b1fnr, MockTpsPersonServiceFactory.MockTPS.TPSType.BARN),
                        MockTpsPersonServiceFactory.MockTPS("Person-22000.json", b2fnr, MockTpsPersonServiceFactory.MockTPS.TPSType.BARN)
                ))
        pensjonsinformasjonService = PrefillTestHelper.lesPensjonsdataFraFil("KravAlderEllerUfore_AP_UTLAND.xml")
        pensjonsinformasjonServiceGjen = PrefillTestHelper.lesPensjonsdataFraFil("P2100-GL-UTL-INNV.xml")

    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2000`() {
        val preutfyllingTPS = personDataFromTPS.mockPersonV3Service()
        val prefillNav = PrefillNav(mock<PrefillAdresse>(), institutionid = "NO:noinst002", institutionnavn = "NOINST002, NO INST002, NO")
        val fnr = personDataFromTPS.getRandomNavFodselsnummer() ?: "02345678901"
        val prefillData = initialPrefillDataModel(sedType = "P2000", pinId = fnr, vedtakId = "", penSaksnummer = pesysSaksnummer)
        val sed = PrefillSEDService(prefillNav, preutfyllingTPS, EessiInformasjon(), pensjonsinformasjonService).prefill(prefillData)

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)
        assertEquals(2, sed.nav?.barn?.size)

    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2100`() {
        val preutfyllingTPS = personDataFromTPS.mockPersonV3Service()
        val prefillNav = PrefillNav(mock<PrefillAdresse>(), institutionid = "NO:noinst002", institutionnavn = "NOINST002, NO INST002, NO")
        val prefillData = initialPrefillDataModel(sedType = "P2100", pinId = fnr, avdod = PersonId(norskIdent = fnr, aktorId = "212"), vedtakId = "", penSaksnummer = "22875355")
        val personData = PersonData(forsikretPerson = preutfyllingTPS.hentBruker(fnr)!!, ektefelleBruker = null, ekteTypeValue = "ENKE", brukerEllerGjenlevende = preutfyllingTPS.hentBruker(fnr), barnBrukereFraTPS = listOf(preutfyllingTPS.hentBruker(b1fnr)!!, preutfyllingTPS.hentBruker(b2fnr)!!))
        val response = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData, brukerInformasjon = prefillData.getPersonInfoFromRequestData())
        val sed = prefillData.sed
        sed.nav = response

        print("----------------------------------------------------------")
        println("SED: ${sed.toJsonSkipEmpty()}")
        print("----------------------------------------------------------")

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)

        assertEquals(2, sed.nav?.barn?.size)

        val resultBarn = sed.nav?.barn

        val item1 = resultBarn.orEmpty().get(0)
        assertEquals("BOUWMANS", item1.person?.etternavn)
        assertEquals("TOPPI DOTTO", item1.person?.fornavn)
        val ident1 = item1.person?.pin?.get(0)?.identifikator
        val navfnr1 = NavFodselsnummer(ident1!!)
        assertEquals(false, navfnr1.isUnder18Year())
        assertEquals(37, navfnr1.getAge())


        val item2 = resultBarn.orEmpty().get(1)
        assertEquals("BOUWMANS", item2.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", item2.person?.fornavn)
        val ident = item2.person?.pin?.get(0)?.identifikator
        val navfnr = NavFodselsnummer(ident!!)
        assertEquals(true, navfnr.isUnder18Year())
    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2200`() {
        val preutfyllingTPS = personDataFromTPS.mockPersonV3Service()
        val prefillNav = PrefillNav(mock<PrefillAdresse>(), institutionid = "NO:noinst002", institutionnavn = "NOINST002, NO INST002, NO")

        val prefillData = initialPrefillDataModel(sedType = "P2200", pinId = fnr, vedtakId = "", penSaksnummer = "14915730")
        val sed = PrefillSEDService(prefillNav, preutfyllingTPS, EessiInformasjon(), pensjonsinformasjonService).prefill(prefillData)

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)
        assertEquals(2, sed.nav?.barn?.size)

        assertEquals("P2200", sed.sed)

    }

}
