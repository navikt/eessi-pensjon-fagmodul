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
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2100GjenlevendeRevurdering {

    private val personFnr = generateRandomFnr(45)
    private val avdodPersonFnr = generateRandomFnr(45)
    private val pesysSaksnummer = "22915550"
    private val pesysKravid = "41098605"

    private lateinit var prefillData: PrefillDataModel
    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var prefillNav: PrefillPDLNav

    @BeforeEach
    fun setup() {
        prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:NAVAT02",
                institutionnavn = "NOINST002, NO INST002, NO")
    }

    @Test
    fun `forventet korrekt utfylt P2100 uforepensjon med kap4 og 9`() {
        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = SEDType.P2100,
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr, "112233445566"),
                kravId = pesysKravid)
        dataFromPEN = lesPensjonsdataFraFil("P2100-GJENLEV-REVURDERING-M-KRAVID-INNV.xml")

//        val persondataFraTPS = setupPersondataFraTPS(setOf(
//                MockTpsPersonServiceFactory.MockTPS("Person-30000.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON)
//        ))

        val person = PersonPDLMock.createWith(fornavn = "BAMSE ULUR", fnr = personFnr)
        val avdod = PersonPDLMock.createWith(fornavn = "BAMSE LUR", fnr = avdodPersonFnr, erDod = true)
        val persondataCollection = PersonDataCollection(
            forsikretPerson = person,
            sivilstandstype = Sivilstandstype.ENKE_ELLER_ENKEMANN,
            gjenlevendeEllerAvdod = avdod
        )

        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)
        val p2100 = prefillSEDService.prefill(prefillData, persondataCollection)

        val p2100gjenlev = SED(
                type = SEDType.P2100,
                pensjon = p2100.pensjon,
                nav = Nav(krav = p2100.nav?.krav)
        )

        val sed = p2100gjenlev
        assertNotNull(sed.nav?.krav)
        assertEquals("2020-02-12", sed.nav?.krav?.dato)
    }


}

