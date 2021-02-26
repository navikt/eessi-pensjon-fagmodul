package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class SedP3000XXTest {

    @Mock
    lateinit var eessiInformasjon: EessiInformasjon

    @Mock
    lateinit var dataFromPEN: PensjonsinformasjonService

    lateinit var prefillSEDService: PrefillSEDService

    private val personFnr = FodselsnummerMother.generateRandomFnr(68)
    private lateinit var personDataCollection: PersonDataCollection


    @BeforeEach
    fun setupAndRunAtStart() {
        val person = LagPDLPerson.lagPerson(personFnr, "Ola", "Testbruker")
        personDataCollection = PersonDataCollection(person, person)

        val prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        prefillSEDService = PrefillSEDService(dataFromPEN, eessiInformasjon, prefillNav)
    }


    @Test
    fun testP3000_AT() {
        val datamodel = getMockDataModel("P3000_AT", personFnr)

        val sed = prefillSEDService.prefill(datamodel, personDataCollection)
        Assertions.assertEquals(SEDType.P3000_AT, sed.type)

    }

    @Test
    fun testP3000_IT() {

        val datamodel = getMockDataModel("P3000_IT", personFnr)

        val sed = prefillSEDService.prefill(datamodel, personDataCollection)
        Assertions.assertEquals(SEDType.P3000_IT, sed.type)
    }

    @Test
    fun testP3000_SE() {
        val datamodel = getMockDataModel("P3000_SE", personFnr)

        val sed = prefillSEDService.prefill(datamodel, personDataCollection)
        Assertions.assertEquals(SEDType.P3000_SE, sed.type)
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
}
