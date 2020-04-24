package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPensjon
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPerson
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.readJsonResponse
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.setupPersondataFraTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class PrefillP7000_AP_21975717Test {

    private val personFnr = "01071843352"

    lateinit var prefillData: PrefillDataModel
    lateinit var prefill: Prefill<SED>

    @BeforeEach
    fun setup() {
        val persondataFraTPS = setupPersondataFraTPS(setOf(
                PersonDataFromTPS.MockTPS("Person-11000-GIFT.json", personFnr, PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-12000-EKTE.json", generateRandomFnr(70), PersonDataFromTPS.MockTPS.TPSType.EKTE)
        ))

        val prefillNav = PrefillNav(brukerFromTPS = persondataFraTPS,
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002", institutionnavn = "NOINST002, NO INST002, NO")

        val prefillPensjon = PrefillPensjon(persondataFraTPS, prefillNav)

        prefill = PrefillP7000(PrefillPerson(prefillNav, prefillPensjon))

        prefillData = PrefillDataModel(penSaksnummer = "21975717", bruker = PersonId(personFnr, "123456789"), avdod = null).apply {
            rinaSubject = "Pensjon"
            sed = SED("P7000")

            vedtakId = "12312312"
            buc = "P_BUC_99"
            institution = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
            partSedAsJson = mutableMapOf(
                    "PersonInfo" to readJsonResponse("other/person_informasjon_selvb.json"),
                    "P4000" to readJsonResponse("other/p4000_trygdetid_part.json"))
        }
    }

    @Test
    fun `forventet korrekt utfylt P7000 Melding om vedtakssammendrag med MockData fra testfiler`() {
        val p7000 = prefill.prefill(prefillData)

        assertEquals("OKOULOV", p7000.nav?.ektefelle?.person?.etternavn)
        assertEquals("M", p7000.pensjon?.bruker?.person?.kjoenn)

        //"foedselsdato": "1951-11-07T00:00:00.000+0200",
        assertEquals("1951-11-07", p7000.nav?.bruker?.person?.foedselsdato)
    }

    @Test
    fun `forventet P7000 er lik sedfil med MockData fra testfiler`() {
        val filepath = "src/test/resources/json/nav/P7000_OK_NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        val p7000 = prefill.prefill(prefillData)

        val sed = p7000.toJsonSkipEmpty()

        assertTrue(validateJson(sed))
        JSONAssert.assertEquals(json, sed, true)
    }

}

