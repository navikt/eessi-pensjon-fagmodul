package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.pen.FeilSakstypeForSedException
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2000MedUforeSakTest {

    private val personFnr = generateRandomFnr(68)
    private val pesysSaksnummer = "22874955"

    lateinit var prefillData: PrefillDataModel

    lateinit var prefillNav: PrefillNav
    lateinit var dataFromPEN: PensjonsinformasjonService
    lateinit var prefillSEDService: PrefillSEDService

    @Mock
    lateinit var aktorRegisterService: AktoerregisterService

    @Mock
    lateinit var prefillPDLNav: PrefillPDLNav

    @BeforeEach
    fun setup() {
        val persondataFraTPS = PrefillTestHelper.setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-11000-GIFT.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-12000-EKTE.json", generateRandomFnr(70), MockTpsPersonServiceFactory.MockTPS.TPSType.EKTE)
        ))

        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("P2200-UP-INNV.xml")

        prefillData = PrefillDataModelMother.initialPrefillDataModel("P2000", personFnr, penSaksnummer = pesysSaksnummer)

        prefillSEDService = PrefillSEDService(prefillNav, persondataFraTPS, EessiInformasjon(), dataFromPEN, aktorRegisterService, prefillPDLNav)
    }

    @Test
    fun `forventer exception - ikke relevant saktype for krav-SED - uforesak ikke relevant for P2000`() {
        assertThrows<FeilSakstypeForSedException> {
            prefillSEDService.prefill(prefillData)
        }
    }
}

