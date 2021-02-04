package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.readJsonResponse
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2200UPUtlandInnvTest {

    private val personFnr = generateRandomFnr(68)
    private val ekteFnr = generateRandomFnr(70)
    private val pesysSaksnummer = "22874955"

    lateinit var prefillData: PrefillDataModel

    lateinit var prefillNav: PrefillPDLNav
    lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var personDataCollection: PersonDataCollection

    @BeforeEach
    fun setup() {
        personDataCollection = PersonPDLMock.createEnkelFamilie(personFnr, ekteFnr)

        prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("P2200-UP-INNV.xml")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P2200, personFnr, penSaksnummer = pesysSaksnummer).apply {
            partSedAsJson["PersonInfo"] = readJsonResponse("other/person_informasjon_selvb.json")
            partSedAsJson["P4000"] = readJsonResponse("other/p4000_trygdetid_part.json")
        }
        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)

    }

    @Test
    fun `forventet korrekt utfylt P2200 uforepensjon med kap4 og 9`() {
        val P2200 = prefillSEDService.prefill(prefillData, personDataCollection)

        val P2200ufor = SED(
                type = SEDType.P2200,
                pensjon = P2200.pensjon,
                nav = Nav(krav = P2200.nav?.krav)
        )

        val sed = P2200ufor
        assertNotNull(sed.nav?.krav)
        assertEquals("2019-07-15", sed.nav?.krav?.dato)

    }

    @Test
    fun `forventet korrekt utfylt P2200 uforepensjon med mockdata fra testfiler`() {
        val p2200 = prefillSEDService.prefill(prefillData, personDataCollection)

        assertEquals(null, p2200.nav?.barn)

        assertEquals("", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.yrke)
        assertEquals("2018-11-11", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtstartdato)
        assertEquals("2018-11-13", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtpensjoneringsdato)
        assertEquals("07", p2200.nav?.bruker?.arbeidsforhold?.get(0)?.type)

        assertEquals("foo", p2200.nav?.bruker?.bank?.navn)
        assertEquals("bar", p2200.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", p2200.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals("ODIN ETTØYE", p2200.nav?.bruker?.person?.fornavn)
        assertEquals("BALDER", p2200.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p2200.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(68, navfnr1.getAge())

        assertNotNull(p2200.nav?.bruker?.person?.pin)
        val pinlist = p2200.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(personFnr, pinitem?.identifikator)

        assertEquals("THOR-DOPAPIR", p2200.nav?.ektefelle?.person?.fornavn)
        assertEquals("RAGNAROK", p2200.nav?.ektefelle?.person?.etternavn)

        val navfnr = NavFodselsnummer(p2200.nav?.ektefelle?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(70, navfnr.getAge())
    }

}

