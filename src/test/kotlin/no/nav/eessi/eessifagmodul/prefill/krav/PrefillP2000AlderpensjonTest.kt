package no.nav.eessi.eessifagmodul.prefill.krav

import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.PensjonsinformasjonHjelper
import no.nav.eessi.eessifagmodul.prefill.Prefill
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillNav
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPersonDataFromTPS
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@RunWith(MockitoJUnitRunner::class)
class PrefillP2000AlderpensjonTest : AbstractMockKravPensionHelper("P2000", "P2000-AP-14069110.xml") {

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2000(prefillNav, personTPS, pensionDataFromPEN)
    }

    override fun createPayload(prefillData: PrefillDataModel) {
        prefillData.penSaksnummer = "14069110"
        prefillData.partSedAsJson["PersonInfo"] = createPersonInfoPayLoad()
        prefillData.partSedAsJson["P4000"] = createPersonTrygdetidHistorikk()
    }

    override fun createPersonInfoPayLoad(): String {
        return readJsonResponse("person_informasjon_selvb.json")
    }

    override fun createPersonTrygdetidHistorikk(): String {
        return readJsonResponse("Trygdetid_part.json")
    }

    @Test
    fun `sjekk av kravsøknad alderpensjon P2000`() {
        pendata = kravdata.getPensjoninformasjonFraSak(prefillData)
        assertNotNull(pendata)

        val pensak = kravdata.getPensjonSak(prefillData, pendata)
        assertNotNull(pensak)

        assertNotNull(pendata.brukersSakerListe)
        assertEquals("ALDER", pensak.sakType)

    }

    @Test
    fun `testing av komplett utfylling kravsøknad alderpensjon P2000`() {
        val P2000 = prefill.prefill(prefillData)

        P2000.print()

        assertEquals(2, P2000.nav?.barn?.size)

        assertEquals("BOUWMANS", P2000.nav?.barn?.get(0)?.person?.etternavn)
        assertEquals("TOPPI DOTTO", P2000.nav?.barn?.get(0)?.person?.fornavn)
        assertEquals("22345678901", P2000.nav?.barn?.get(0)?.person?.pin?.get(0)?.identifikator)
        assertEquals("SINGLE", P2000.nav?.barn?.get(0)?.person?.sivilstand?.get(0)?.status)

        assertEquals("BOUWMANS", P2000.nav?.barn?.get(1)?.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", P2000.nav?.barn?.get(1)?.person?.fornavn)
        assertEquals("12345678901", P2000.nav?.barn?.get(1)?.person?.pin?.get(0)?.identifikator)
        assertEquals("SINGLE", P2000.nav?.barn?.get(1)?.person?.sivilstand?.get(0)?.status)

        assertEquals("n/a", P2000.nav?.bruker?.arbeidsforhold?.get(0)?.yrke)
        assertEquals("2018-11-12", P2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtstartdato)
        assertEquals("2018-11-14", P2000.nav?.bruker?.arbeidsforhold?.get(0)?.planlagtpensjoneringsdato)
        assertEquals("07", P2000.nav?.bruker?.arbeidsforhold?.get(0)?.type)

        assertEquals("foo", P2000.nav?.bruker?.bank?.navn)
        assertEquals("bar", P2000.nav?.bruker?.bank?.konto?.sepa?.iban)
        assertEquals("baz", P2000.nav?.bruker?.bank?.konto?.sepa?.swift)

        assertEquals("7618", P2000.pensjon?.ytelser?.get(0)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(0)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(0)?.pin?.identifikator)


        assertEquals("7839", P2000.pensjon?.ytelser?.get(1)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(1)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(1)?.pin?.identifikator)

        assertEquals("8075", P2000.pensjon?.ytelser?.get(2)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(2)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(2)?.pin?.identifikator)

        assertEquals("8309", P2000.pensjon?.ytelser?.get(3)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(3)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(3)?.pin?.identifikator)

        assertEquals("8406", P2000.pensjon?.ytelser?.get(4)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(4)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(4)?.pin?.identifikator)

    }

    @Test
    fun `utfylling av barn`() {
        val P2000 = prefill.prefill(prefillData)
        assertEquals(2, P2000.nav?.barn?.size)

        assertEquals("BOUWMANS", P2000.nav?.barn?.get(0)?.person?.etternavn)
        assertEquals("TOPPI DOTTO", P2000.nav?.barn?.get(0)?.person?.fornavn)
        assertEquals("22345678901", P2000.nav?.barn?.get(0)?.person?.pin?.get(0)?.identifikator)
        assertEquals("SINGLE", P2000.nav?.barn?.get(0)?.person?.sivilstand?.get(0)?.status)

        assertEquals("BOUWMANS", P2000.nav?.barn?.get(1)?.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", P2000.nav?.barn?.get(1)?.person?.fornavn)
        assertEquals("12345678901", P2000.nav?.barn?.get(1)?.person?.pin?.get(0)?.identifikator)
        assertEquals("SINGLE", P2000.nav?.barn?.get(1)?.person?.sivilstand?.get(0)?.status)
    }

    @Test
    fun `utfulling og test på ektefelle samboer partner`() {
        val P2000 = prefill.prefill(prefillData)

        val result = P2000.nav?.ektefelle

        if (result != null) {

            println(mapAnyToJson(result))

        }

    }

    @Test
    fun `utfulling og test på verge`() {
        val P2000 = prefill.prefill(prefillData)

        val result = P2000.nav?.verge

        if (result != null) {
            println(mapAnyToJson(result))
        }

    }


    @Test
    fun `utfylling alderpensjon ytelser`() {
        val P2000 = prefill.prefill(prefillData)

        assertEquals("7618", P2000.pensjon?.ytelser?.get(0)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(0)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(0)?.pin?.identifikator)


        assertEquals("7839", P2000.pensjon?.ytelser?.get(1)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(1)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(1)?.pin?.identifikator)

        assertEquals("8075", P2000.pensjon?.ytelser?.get(2)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(2)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(2)?.pin?.identifikator)

        assertEquals("8309", P2000.pensjon?.ytelser?.get(3)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(3)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(3)?.pin?.identifikator)

        assertEquals("8406", P2000.pensjon?.ytelser?.get(4)?.totalbruttobeloeparbeidsbasert)
        assertEquals("FOLKETRYGD", P2000.pensjon?.ytelser?.get(4)?.annenytelse)
        assertEquals("02345678901", P2000.pensjon?.ytelser?.get(4)?.pin?.identifikator)

    }

}