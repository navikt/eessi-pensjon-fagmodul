package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2100GLutlandInnvTest {

    private val personFnr = generateRandomFnr(65)
    private val avdodPersonFnr = generateRandomFnr(75)
    private val pesysSaksnummer = "22875355"

    private lateinit var prefillData: PrefillDataModel
    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var persondataCollection: PersonDataCollection

    @BeforeEach
    fun setup() {
        persondataCollection = PersonPDLMock.createAvdodFamilie(personFnr, avdodPersonFnr)

        val prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("P2100-GL-UTL-INNV.xml")


        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = SedType.P2100,
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr, "112233445566")).apply {
            partSedAsJson["PersonInfo"] = readJsonResponse("other/person_informasjon_selvb.json")
            partSedAsJson["P4000"] = readJsonResponse("other/p4000_trygdetid_part.json")

        }
        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)

    }

    @Test
    fun `forventet korrekt utfylt P2100 uforepensjon med kap4 og 9`() {
        val p2100 = prefillSEDService.prefill(prefillData, persondataCollection)

        val p2100gjenlev = SED(
                type = SedType.P2100,
                pensjon = p2100.pensjon,
                nav = Nav(krav = p2100.nav?.krav)
        )

        val sed = p2100gjenlev
        assertNotNull(sed.nav?.krav)
        assertEquals("2019-06-01", sed.nav?.krav?.dato)

    }

    @Test
    fun `forventet korrekt utfylt P2100 uforepensjon med mockdata fra testfiler`() {
        val p2100 = prefillSEDService.prefill(prefillData, persondataCollection)

        assertEquals(null, p2100.nav?.barn)

        assertEquals("", p2100.nav?.bruker?.arbeidsforhold?.get(0)?.yrke)
        assertEquals("2018-11-11", p2100.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtstartdato)
        assertEquals("2018-11-13", p2100.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtpensjoneringsdato)
        assertEquals("07", p2100.nav?.bruker?.arbeidsforhold?.get(0)?.type)

        assertEquals("foo", p2100.nav?.bruker?.bank?.navn)
        assertEquals("bar", p2100.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", p2100.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals("BAMSE LUR", p2100.nav?.bruker?.person?.fornavn)
        assertEquals("MOMBALO", p2100.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p2100.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(75, navfnr1.getAge())
        assertEquals("M", p2100.nav?.bruker?.person?.kjoenn)

        assertNotNull(p2100.nav?.bruker?.person?.pin)
        val pinlist = p2100.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(avdodPersonFnr, pinitem?.identifikator)

        assertEquals("BAMSE ULUR", p2100.pensjon?.gjenlevende?.person?.fornavn)
        assertEquals("DOLLY", p2100.pensjon?.gjenlevende?.person?.etternavn)
        val navfnr2 = NavFodselsnummer(p2100.pensjon?.gjenlevende?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(65, navfnr2.getAge())
        assertEquals("K", p2100.pensjon?.gjenlevende?.person?.kjoenn)

    }

}

