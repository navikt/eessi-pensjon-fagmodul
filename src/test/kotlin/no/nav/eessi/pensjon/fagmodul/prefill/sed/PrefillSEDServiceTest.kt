package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillSEDServiceTest {


    lateinit var prefillSEDService: PrefillSEDService

    @Mock
    lateinit var prefillNav: PrefillNav

    @Mock
    lateinit var tpsPersonService: PersonV3Service

    @Mock
    lateinit var eessiInformasjon: EessiInformasjon

    @Mock
    lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @Mock
    lateinit var aktorRegisterService: AktoerregisterService

    @Mock
    lateinit var prefillPDLNav: PrefillPDLNav

    @BeforeEach
    fun beforeEach() {
        prefillSEDService = PrefillSEDService(prefillNav, tpsPersonService, eessiInformasjon, pensjonsinformasjonService, aktorRegisterService, prefillPDLNav)
    }


    @Test
    fun testBarnInnhentingBarnOver18aar() {

        val foreldersPin = "15084535647"
        val barnetsPin = "10107512458"
        val forelder = lagTPSBruker(foreldersPin, "Christopher", "Robin").medBarn(barnetsPin)

        val actual = prefillSEDService.hentBarnFraTps(forelder)

        Assertions.assertEquals(0, actual.size)
    }

    @Test
    fun testBarnInnhentingBarnUnder18aar() {

        val foreldersPin = FodselsnummerMother.generateRandomFnr(40)
        val barnetsPin = FodselsnummerMother.generateRandomFnr(13)
        val barnets2Pin = "09109000000"
        val forelder = lagTPSBruker(foreldersPin, "Christopher", "Robin").medBarn(barnetsPin).medBarn(barnets2Pin)
        val barn = lagTPSBruker(barnetsPin, "Ole", "Brum")

        doReturn(barn).whenever(tpsPersonService).hentBruker(any())
        doReturn(AktoerId("3323332333233323")).`when`(aktorRegisterService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(barnetsPin))

        val actual = prefillSEDService.hentBarnFraTps(forelder)

        Assertions.assertEquals(1, actual.size)
        Assertions.assertEquals(barn, actual.first())

    }

    @Test
    fun `gitt en hovedperson uten barn så skal den preutfylte seden ikke inneholde barn`() {

        val foreldersPin = "15084535647"
        val barnetsPin = "10107512458"
        val forelder = lagTPSBruker(foreldersPin, "Christopher", "Robin").medBarn(barnetsPin)

        val actual = prefillSEDService.hentBarnFraTps(forelder)

        Assertions.assertEquals(0, actual.size)
    }

    @Test
    fun `gitt hovedperson med to barn skal begge barna bli preutfylt`() {
        val foreldersPin = FodselsnummerMother.generateRandomFnr(42)
        val eldstebarnetsPin = FodselsnummerMother.generateRandomFnr(17)
        val yngstebarnetsPin = FodselsnummerMother.generateRandomFnr(13)
        val forelder = lagTPSBruker(foreldersPin, "Christoffer", "Robin").medBarn(eldstebarnetsPin).medBarn(yngstebarnetsPin)
        val eldsteBarn = lagTPSBruker(eldstebarnetsPin, "Ole", "Brum")
        val yngsteBarn = lagTPSBruker(yngstebarnetsPin, "Nasse", "Nuff")


        doReturn(eldsteBarn).whenever(tpsPersonService).hentBruker(eldstebarnetsPin)
        doReturn(yngsteBarn).whenever(tpsPersonService).hentBruker(yngstebarnetsPin)

        doReturn(AktoerId("3323332333233323")).`when`(aktorRegisterService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(eldstebarnetsPin))
        doReturn(AktoerId("121212123233323")).`when`(aktorRegisterService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(yngstebarnetsPin))


        val actual = prefillSEDService.hentBarnFraTps(forelder)

        Assertions.assertEquals(2, actual.size)
        Assertions.assertTrue(actual.contains(eldsteBarn))
        Assertions.assertTrue(actual.contains(yngsteBarn))
    }

    private fun lagTPSBruker(foreldersPin: String, fornavn: String, etternavn: String) =
            Bruker()
                    .withPersonnavn(Personnavn()
                            .withEtternavn(etternavn)
                            .withFornavn(fornavn))
                    .withKjoenn(Kjoenn().withKjoenn(Kjoennstyper().withValue("M")))
                    .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(foreldersPin)))
                    .withStatsborgerskap(Statsborgerskap().withLand(Landkoder().withValue("NOR")))

    private fun Bruker.medBarn(barnetsPin: String): Bruker =
            this
                    .withHarFraRolleI(Familierelasjon()
                            .withTilRolle(Familierelasjoner()
                                    .withValue("BARN"))
                            .withTilPerson(Person()
                                    .withAktoer(PersonIdent()
                                            .withIdent(NorskIdent()
                                                    .withIdent(barnetsPin)))))
}