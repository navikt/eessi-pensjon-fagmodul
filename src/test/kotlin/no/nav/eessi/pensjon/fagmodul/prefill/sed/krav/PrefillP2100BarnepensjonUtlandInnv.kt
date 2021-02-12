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
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2100BarnepensjonUtlandInnv {

    private val personFnr = generateRandomFnr(12)
    private val avdodPersonFnr = generateRandomFnr(40)
    private val pesysSaksnummer = "22915555"

    private lateinit var prefillData: PrefillDataModel
    private lateinit var prefillSEDService: PrefillSEDService

    private lateinit var  personDataCollection: PersonDataCollection

    @BeforeEach
    fun setup() {
        personDataCollection = PersonPDLMock.createAvdodFamilie(personFnr, avdodPersonFnr)

        val prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:NAVAT02",
                institutionnavn = "NOINST002, NO INST002, NO")

        val dataFromPEN = lesPensjonsdataFraFil("BARNEP_KravUtland_ForeldreAvdod.xml")


        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = SEDType.P2100,
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr, "112233445566")).apply {
            partSedAsJson["PersonInfo"] = PrefillTestHelper.readJsonResponse("other/person_informasjon_selvb.json")
            partSedAsJson["P4000"] = PrefillTestHelper.readJsonResponse("other/p4000_trygdetid_part.json")
        }
        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)

    }

    @Test
    fun `forventet korrekt utfylt P2100 uforepensjon med kap4 og 9`() {
        val p2100 = prefillSEDService.prefill(prefillData, personDataCollection)

        val p2100gjenlev = SED(
                type = SEDType.P2100,
                pensjon = p2100.pensjon,
                nav = Nav(krav = p2100.nav?.krav)
        )

        val sed = p2100gjenlev
        assertNotNull(sed.nav?.krav)
        assertEquals("2020-08-20", sed.nav?.krav?.dato)

    }

    @Test
    fun `forventet korrekt utfylt P2100 uforepensjon med mockdata fra testfiler`() {
        val p2100 = prefillSEDService.prefill(prefillData, personDataCollection)

        assertEquals(null, p2100.nav?.barn)

        assertEquals("foo", p2100.nav?.bruker?.bank?.navn)
        assertEquals("bar", p2100.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", p2100.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals("BAMSE LUR", p2100.nav?.bruker?.person?.fornavn)
        assertEquals("MOMBALO", p2100.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p2100.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(40, navfnr1.getAge())
        assertEquals("M", p2100.nav?.bruker?.person?.kjoenn)

        assertNotNull(p2100.nav?.bruker?.person?.pin)
        val pinlist = p2100.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:NAVAT02", pinitem?.institusjonsid)
        assertEquals(avdodPersonFnr, pinitem?.identifikator)

        assertEquals("BAMSE ULUR", p2100.pensjon?.gjenlevende?.person?.fornavn)
        assertEquals("DOLLY", p2100.pensjon?.gjenlevende?.person?.etternavn)
        val navfnr2 = NavFodselsnummer(p2100.pensjon?.gjenlevende?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(12, navfnr2.getAge())
        assertEquals("K", p2100.pensjon?.gjenlevende?.person?.kjoenn)

    }

}

