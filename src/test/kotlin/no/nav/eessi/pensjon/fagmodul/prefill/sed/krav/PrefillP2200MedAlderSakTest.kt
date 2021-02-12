package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.FeilSakstypeForSedException
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2200MedAlderSakTest {

    private val personFnr = generateRandomFnr(68)
    private val ekteFnr = generateRandomFnr(70)
    private val pesysSaksnummer = "14069110"

    private lateinit var prefillData: PrefillDataModel
    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService

    private lateinit var personDataCollection: PersonDataCollection

    @BeforeEach
    fun setup() {
        val person = PersonPDLMock.createWith(fornavn = "BAMSE ULUR", fnr = personFnr)
        val ekte = PersonPDLMock.createWith(fornavn = "BAMSE LUR", fnr = ekteFnr)

        personDataCollection = PersonDataCollection(
            forsikretPerson = person,
            sivilstandstype = Sivilstandstype.GIFT,
            gjenlevendeEllerAvdod = person,
            ektefellePerson = ekte
        )

        val prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("PensjonsinformasjonSaksliste-AP-14069110.xml")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P2200, personFnr, penSaksnummer = pesysSaksnummer)

        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)
    }

    @Test
    fun `forventer exception - ikke relevant saktype for krav-SED - aldersak ikke relevant for P2200`() {
        assertThrows<FeilSakstypeForSedException> {
            prefillSEDService.prefill(prefillData, personDataCollection)
        }
    }
}

