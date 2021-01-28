package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoenn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoennstyper
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Statsborgerskap
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class SedP3000XXTest {

    @Mock
    lateinit var prefillNav: PrefillNav

    @Mock
    lateinit var eessiInformasjon: EessiInformasjon

    @Mock
    lateinit var dataFromPEN: PensjonsinformasjonService

    @Mock
    lateinit var dataFromTPS: PersonV3Service

    @Mock
    lateinit var prefillPDLNav: PrefillPDLNav

    lateinit var prefillSEDService: PrefillSEDService

    private val personFnr = FodselsnummerMother.generateRandomFnr(68)

    private lateinit var person: Bruker

    @Mock
    lateinit var aktorRegisterService: AktoerregisterService


    @BeforeEach
    fun setupAndRunAtStart() {
        person = lagTPSBruker(personFnr, "Ola", "Testbruker")

        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        prefillSEDService = PrefillSEDService(prefillNav, dataFromTPS, eessiInformasjon, dataFromPEN, aktorRegisterService, prefillPDLNav)
        whenever(dataFromTPS.hentBruker(any())).thenReturn(person)
    }


    @Test
    fun testP3000_AT() {

        val datamodel = getMockDataModel("P3000_AT", personFnr)

        val sed = prefillSEDService.prefill(datamodel)
        Assertions.assertEquals("P3000_AT", sed.sed)

    }

    @Test
    fun testP3000_IT() {

        val datamodel = getMockDataModel("P3000_IT", personFnr)

        val sed = prefillSEDService.prefill(datamodel)
        Assertions.assertEquals("P3000_IT", sed.sed)
    }

    @Test
    fun testP3000_SE() {
        val datamodel = getMockDataModel("P3000_SE", personFnr)

        val sed = prefillSEDService.prefill(datamodel)
        Assertions.assertEquals("P3000_SE", sed.sed)
    }


    private fun getMockDataModel(sedType: String, fnr: String  = "someFnr"): PrefillDataModel {
        val req = ApiRequest(
                institutions = listOf(),
                sed = sedType,
                sakId = "12231231",
                euxCaseId = "99191999911",
                aktoerId = "00000",
                fnr = fnr,
                buc = "P_BUC_01",
                subjectArea = "Pensjon",
                payload = "{}"
        )
        return ApiRequest.buildPrefillDataModelOnExisting(req, "12345", null)
    }

    private fun lagTPSBruker(foreldersPin: String, fornavn: String, etternavn: String) =
            no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker()
                    .withPersonnavn(Personnavn()
                            .withEtternavn(etternavn)
                            .withFornavn(fornavn))
                    .withKjoenn(Kjoenn().withKjoenn(Kjoennstyper().withValue("M")))
                    .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(foreldersPin)))
                    .withStatsborgerskap(Statsborgerskap().withLand(Landkoder().withValue("NOR")))

}
