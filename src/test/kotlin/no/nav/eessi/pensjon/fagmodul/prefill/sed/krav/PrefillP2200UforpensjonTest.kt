package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother.initialPrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.readJsonResponse
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2200UforpensjonTest {

    private val personFnr = generateRandomFnr(42)
    private val barn1Fnr = generateRandomFnr(12)
    private val barn2Fnr = generateRandomFnr(17)

    lateinit var prefillData: PrefillDataModel
    lateinit var prefillNav: PrefillPDLNav
    lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService

    @BeforeEach
    fun setup() {
        prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("P2200-UP-INNV.xml")

        prefillData = initialPrefillDataModel(SEDType.P2200, personFnr, penSaksnummer = "22874955").apply {
            partSedAsJson["PersonInfo"] = readJsonResponse("other/person_informasjon_selvb.json")
        }
        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)

    }

    @Test
    fun `Testing av komplett utfylling kravsøknad uførepensjon P2200`() {
        val pendata: Pensjonsinformasjon = dataFromPEN.hentPensjonInformasjon(prefillData.bruker.aktorId)
        val persondataCollection = PersonPDLMock.createEnkeWithBarn(personFnr, barn1Fnr, barn2Fnr)

        assertNotNull(pendata.brukersSakerListe)

        val P2200 = prefillSEDService.prefill(prefillData, persondataCollection)
        val p2200Actual = P2200.toJsonSkipEmpty()
        assertNotNull(p2200Actual)
        assertEquals(SEDType.P2200, P2200.type)
        assertEquals("JESSINE TORDNU", P2200.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", P2200.nav?.bruker?.person?.etternavn)
        assertEquals(2, P2200.nav?.barn?.size)

        val barn1 = P2200.nav?.barn?.first()
        val barn2 = P2200.nav?.barn?.last()

        assertEquals("BOUWMANS", barn1?.person?.etternavn)
        assertEquals("TOPPI DOTTO", barn1?.person?.fornavn)
        assertEquals("BOUWMANS", barn2?.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", barn2?.person?.fornavn)

    }

}
