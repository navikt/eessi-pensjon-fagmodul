package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.ValidationException
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.tps.BrukerFromTPS
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2000ValidateTest {

    private lateinit var prefillP2000: PrefillP2000
    lateinit var prefillNav: PrefillNav

    @Mock
    lateinit var dataFromPEN: PensjonsinformasjonHjelper

    @Mock
    lateinit var persondataFraTPS: BrukerFromTPS

    @Mock
    lateinit var sakHelper: PrefillP2xxxPensjon

    @Mock
    lateinit var brukerFromTPS: BrukerFromTPS

    @BeforeEach
    fun before() {
        prefillNav = PrefillNav(brukerFromTPS,
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002", institutionnavn = "NOINST002, NO INST002, NO")
        prefillP2000 = PrefillP2000(prefillNav, dataFromPEN, persondataFraTPS)
    }

    @Test
    fun `call prefillAndPreview  Exception ved validating SED`() {
        assertThrows<ValidationException> {
            prefillP2000.validate(generateMockP2000ForValidatorError(generatePrefillModel()))
        }
    }

    private fun generatePrefillModel(): PrefillDataModel {
        return PrefillDataModel(penSaksnummer = "123456789999", bruker = PersonId("12345678901", "dummy")).apply {
            euxCaseID = "1000"
            sed = SED("P2000")
            buc = "P_BUC_01"
            institution = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        }
    }

    private fun generateMockP2000ForValidatorError(prefillModel: PrefillDataModel): SED {
        val mocksed = prefillModel.sed
        mocksed.nav = Nav()
        mocksed.pensjon = Pensjon()
        return mocksed
    }
}
