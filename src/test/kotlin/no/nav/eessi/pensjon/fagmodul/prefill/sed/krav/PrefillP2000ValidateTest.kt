package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2000ValidateTest {

    private lateinit var prefillNav: PrefillNav

    @Mock
    lateinit var dataFromPEN: PensjonsinformasjonService

    @Mock
    lateinit var personV3Service: PersonV3Service

    private lateinit var prefillSEDService: PrefillSEDService

    private lateinit var person: Bruker


    @BeforeEach
    fun before() {
        person = lagTPSBruker("12345678901", "Ola", "Testbruker")

        whenever(personV3Service.hentBruker(any())).thenReturn(person)

        prefillNav = PrefillNav(prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")
        prefillSEDService = PrefillSEDService(prefillNav, personV3Service, EessiInformasjon(), dataFromPEN)
    }

    @Test
    fun `call prefillAndPreview  Exception ved validating SED`() {
        assertThrows<ValidationException> {
            prefillSEDService.prefill(generatePrefillModel())
        }
    }

    private fun generatePrefillModel(): PrefillDataModel {
        return PrefillDataModel(penSaksnummer = "123456789999", bruker = PersonId("12345678901", "dummy"), avdod = null).apply {
            euxCaseID = "1000"
            sed = SED("P2000")
            buc = "P_BUC_01"
            institution = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        }
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
