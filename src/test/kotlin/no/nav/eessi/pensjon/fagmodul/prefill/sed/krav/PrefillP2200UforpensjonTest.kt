package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother.initialPrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.readJsonResponse
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.setupPersondataFraTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2200UforpensjonTest {

    private val personFnr = generateRandomFnr(42)
    private val barn1Fnr = generateRandomFnr(12)
    private val barn2Fnr = generateRandomFnr(17)

    lateinit var prefillData: PrefillDataModel
    lateinit var prefillNav: PrefillNav
    lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService
    @Mock
    lateinit var aktorRegisterService: AktoerregisterService
    @Mock
    lateinit var prefillPDLNav: PrefillPDLNav

    @BeforeEach
    fun setup() {
        val persondataFraTPS = setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-20000.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-30000.json", barn1Fnr, MockTpsPersonServiceFactory.MockTPS.TPSType.BARN),
                MockTpsPersonServiceFactory.MockTPS("Person-30000.json", barn2Fnr, MockTpsPersonServiceFactory.MockTPS.TPSType.BARN)
        ))
        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("P2200-UP-INNV.xml")

        prefillData = initialPrefillDataModel("P2200", personFnr, penSaksnummer = "22874955").apply {
            partSedAsJson["PersonInfo"] = readJsonResponse("other/person_informasjon_selvb.json")
        }
        prefillSEDService = PrefillSEDService(prefillNav, persondataFraTPS, EessiInformasjon(), dataFromPEN, aktorRegisterService, prefillPDLNav)

    }

    @Test
    fun `Testing av komplett utfylling kravsøknad uførepensjon P2200`() {
        val pendata: Pensjonsinformasjon = dataFromPEN.hentPensjonInformasjon(prefillData.bruker.aktorId)

        doReturn(AktoerId("3323332333233323")).`when`(aktorRegisterService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(barn1Fnr))
        doReturn(AktoerId("123332333233323")).`when`(aktorRegisterService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(barn2Fnr))

        assertNotNull(pendata.brukersSakerListe)

        val P2200 = prefillSEDService.prefill(prefillData)
        val p2200Actual = P2200.toJsonSkipEmpty()
        assertNotNull(p2200Actual)
    }

}
