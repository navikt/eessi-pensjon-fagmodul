package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.setupPersondataFraTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2100MedAlderSakTest {

    private val personFnr = generateRandomFnr(68)
    private val pesysSaksnummer = "21975717"
    private val avdodPersonFnr = generateRandomFnr(75)

    lateinit var prefillData: PrefillDataModel

    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var persondataFraTPS :PersonV3Service
    private lateinit var prefillNav: PrefillNav

    @BeforeEach
    fun setup() {
        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

    }

    @Test
    fun `forventer utfylt P2100`() {
        persondataFraTPS = setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-30000.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-31000.json", avdodPersonFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON)
        ))

        dataFromPEN = lesPensjonsdataFraFil("KravAlderEllerUfore_AP_UTLAND.xml")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = "P2100",
                pinId = personFnr,
                kravId = "3243243",
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr,"112233445566"))

        prefillSEDService = PrefillSEDService(prefillNav, persondataFraTPS, EessiInformasjon(), dataFromPEN)
        val p2100 = prefillSEDService.prefill(prefillData)

        assertEquals("P2100", p2100.sed)
        assertEquals("BAMSE ULUR", p2100.pensjon?.gjenlevende?.person?.fornavn)
        assertEquals("BAMSE LUR", p2100.nav?.bruker?.person?.fornavn)
        assertEquals("2015-06-16", p2100.pensjon?.kravDato?.dato)
        prefillData.melding?.isNotEmpty()?.let { assert(it) }

    }
}

