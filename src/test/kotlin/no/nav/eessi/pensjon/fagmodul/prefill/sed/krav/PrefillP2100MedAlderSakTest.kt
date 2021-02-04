package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
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

    private lateinit var prefillNav: PrefillPDLNav

    @BeforeEach
    fun setup() {
        prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("KravAlderEllerUfore_AP_UTLAND.xml")
    }

    @Test
    fun `forventer utfylt P2100`() {
        val person = PersonPDLMock.createWith(fornavn = "BAMSE ULUR", fnr = personFnr)
        val avdod = PersonPDLMock.createWith(fornavn = "BAMSE LUR", fnr = avdodPersonFnr, erDod = true)
        val persondataCollection = PersonDataCollection(
            forsikretPerson = person,
            ektefellePerson = null,
            sivilstandstype = Sivilstandstype.ENKE_ELLER_ENKEMANN,
            gjenlevendeEllerAvdod = avdod
        )

        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = SEDType.P2100,
                pinId = personFnr,
                kravId = "3243243",
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr, "112233445566"))

        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)
        val p2100 = prefillSEDService.prefill(prefillData, persondataCollection)

        assertEquals(SEDType.P2100, p2100.type)
        assertEquals("BAMSE ULUR", p2100.pensjon?.gjenlevende?.person?.fornavn)
        assertEquals("BAMSE LUR", p2100.nav?.bruker?.person?.fornavn)
        assertEquals("2015-06-16", p2100.pensjon?.kravDato?.dato)
        prefillData.melding?.isNotEmpty()?.let { assert(it) }

    }
}

