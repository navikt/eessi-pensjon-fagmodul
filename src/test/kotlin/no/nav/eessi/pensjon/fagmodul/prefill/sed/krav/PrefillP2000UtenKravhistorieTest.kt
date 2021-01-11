package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
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
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness


@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefillP2000UtenKravhistorieTest {

    private val personFnr = generateRandomFnr(67)

    private lateinit var prefillData: PrefillDataModel
    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var prefillNav: PrefillNav
    private lateinit var personV3Service: PersonV3Service

    @Mock
    lateinit var aktorRegisterService: AktoerregisterService


    @BeforeEach
    fun setup() {
        personV3Service = setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-20000.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-21000.json", generateRandomFnr(43), MockTpsPersonServiceFactory.MockTPS.TPSType.BARN),
                MockTpsPersonServiceFactory.MockTPS("Person-22000.json", generateRandomFnr(17), MockTpsPersonServiceFactory.MockTPS.TPSType.BARN)
        ))
        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("PensjonsinformasjonSaksliste-AP-14069110.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel("P2000", personFnr, penSaksnummer = "14069110").apply {
            partSedAsJson["PersonInfo"] = readJsonResponse("other/person_informasjon_selvb.json")
            partSedAsJson["P4000"] = readJsonResponse("other/p4000_trygdetid_part.json")
        }
        prefillSEDService = PrefillSEDService(prefillNav, personV3Service, EessiInformasjon(), dataFromPEN, aktorRegisterService)

    }

    @Test
    fun `Sjekk av kravsoknad alderpensjon P2000`() {
        val pendata: Pensjonsinformasjon = dataFromPEN.hentPensjonInformasjon(prefillData.bruker.aktorId)

        assertNotNull(PensjonsinformasjonService.finnSak(prefillData.penSaksnummer, pendata))

        assertNotNull(pendata.brukersSakerListe)
        assertEquals("ALDER", PensjonsinformasjonService.finnSak(prefillData.penSaksnummer, pendata)?.sakType)

    }

    @Test
    fun `Preutfylling P2000 uten kravdato skal feile`() {
        doReturn(AktoerId("3323332333233300")).`when`(aktorRegisterService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent("01100354352"))
        doReturn(AktoerId("3323332333233311")).`when`(aktorRegisterService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent("01107743352"))

        val ex = assertThrows<Exception> {
            prefillSEDService.prefill(prefillData)
        }
        assertEquals("400 BAD_REQUEST \"Kan ikke opprette krav-SED: P2000 da vedtak og førstegangsbehandling utland mangler. Dersom det gjelder utsendelse til avtaleland, se egen rutine for utsendelse av SED på Navet.\"", ex.message)
    }


}
