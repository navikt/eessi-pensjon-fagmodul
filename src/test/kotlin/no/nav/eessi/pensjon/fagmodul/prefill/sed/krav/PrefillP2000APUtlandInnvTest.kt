package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.readJsonResponse
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.setupPersondataFraTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2000APUtlandInnvTest {

    private val personFnr = generateRandomFnr(68)
    private val pesysSaksnummer = "14398627"

    lateinit var prefillData: PrefillDataModel
    lateinit var prefillNav: PrefillNav
    lateinit var dataFromPEN: PensjonsinformasjonService
    lateinit var prefillSEDService: PrefillSEDService

    @BeforeEach
    fun setup() {
        val persondataFraTPS = setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-11000-GIFT.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-12000-EKTE.json", generateRandomFnr(70), MockTpsPersonServiceFactory.MockTPS.TPSType.EKTE)
        ))
        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        dataFromPEN = lesPensjonsdataFraFil("P2000-AP-UTL-INNV-24015012345_PlanB.xml")

        prefillData = PrefillDataModelMother.initialPrefillDataModel("P2000", personFnr, penSaksnummer = pesysSaksnummer).apply {
            partSedAsJson = mutableMapOf(
                    "PersonInfo" to readJsonResponse("other/person_informasjon_selvb.json"),
                    "P4000" to readJsonResponse("other/p4000_trygdetid_part.json"))
        }
        prefillSEDService = PrefillSEDService(prefillNav, persondataFraTPS, EessiInformasjon(), dataFromPEN)

    }

    @Test //(expected = MangelfulleInndataException::class)
    fun `forventet korrekt utfylt P2000 alderpensjon med kap4 og 9`() {
        val P2000 = prefillSEDService.prefill(prefillData)

        val P2000pensjon = SED(
                sed = "P2000",
                pensjon = P2000.pensjon,
                nav = Nav( krav = P2000.nav?.krav )
        )

        val sed = P2000pensjon
        assertNotNull(sed.nav?.krav)
        assertEquals("2015-11-25", sed.nav?.krav?.dato)

    }

    @Test
    fun `forventet korrekt utfylt P2000 alderpersjon med mockdata fra testfiler`() {
        val p2000 = prefillSEDService.prefill(prefillData)

        assertEquals(null, p2000.nav?.barn)

        assertEquals("", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.yrke)
        assertEquals("2018-11-11", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtstartdato)
        assertEquals("2018-11-13", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtpensjoneringsdato)
        assertEquals("07", p2000.nav?.bruker?.arbeidsforhold?.get(0)?.type)

        assertEquals("foo", p2000.nav?.bruker?.bank?.navn)
        assertEquals("bar", p2000.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", p2000.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals("HASNAWI-MASK", p2000.nav?.bruker?.person?.fornavn)
        assertEquals("OKOULOV", p2000.nav?.bruker?.person?.etternavn)
        val navfnr1 = NavFodselsnummer(p2000.nav?.bruker?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(68, navfnr1.getAge())

        assertNotNull(p2000.nav?.bruker?.person?.pin)
        val pinlist = p2000.nav?.bruker?.person?.pin
        val pinitem = pinlist?.get(0)
        assertEquals(null, pinitem?.sektor)
        assertEquals("NOINST002, NO INST002, NO", pinitem?.institusjonsnavn)
        assertEquals("NO:noinst002", pinitem?.institusjonsid)
        assertEquals(personFnr, pinitem?.identifikator)


        assertEquals("RANNAR-MASK", p2000.nav?.ektefelle?.person?.fornavn)
        assertEquals("MIZINTSEV", p2000.nav?.ektefelle?.person?.etternavn)

        val navfnr = NavFodselsnummer(p2000.nav?.ektefelle?.person?.pin?.get(0)?.identifikator!!)
        assertEquals(70, navfnr.getAge())

    }

    @Test
    fun `testing av komplett P2000 med utskrift og testing av innsending`() {
        val P2000 = prefillSEDService.prefill(prefillData)

        val json = mapAnyToJson(createMockApiRequest("P2000", "P_BUC_01", P2000.toJson()))
        assertNotNull(json)

    }

    private fun createMockApiRequest(sedName: String, buc: String, payload: String): ApiRequest {
        val items = listOf(InstitusjonItem(country = "NO", institution = "NAVT003"))
        return ApiRequest(
                institutions = items,
                sed = sedName,
                sakId = "01234567890",
                euxCaseId = "99191999911",
                aktoerId = "1000060964183",
                buc = buc,
                subjectArea = "Pensjon",
                payload = payload
        )
    }

}

