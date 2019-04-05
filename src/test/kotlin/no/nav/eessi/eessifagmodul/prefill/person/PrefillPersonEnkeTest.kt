package no.nav.eessi.eessifagmodul.prefill.person

import no.nav.eessi.eessifagmodul.prefill.EessiInformasjon
import no.nav.eessi.eessifagmodul.utils.NavFodselsnummer
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


@RunWith(MockitoJUnitRunner::class)
class PrefillPersonEnkeTest : PersonDataFromTPS(
        setOf(
                MockTPS("Person-20000.json", generateRandomFnr(67), MockTPS.TPSType.PERSON),
                MockTPS("Person-21000.json", generateRandomFnr(37), MockTPS.TPSType.BARN),
                MockTPS("Person-22000.json", generateRandomFnr(17), MockTPS.TPSType.BARN)
        ), EessiInformasjon().apply
{
    institutionBy = "Oslo"
    institutionLand = "NO"
    institutionid = "NO:NAV"
    institutionnavn = "NAV"
}
) {

    @Test
    fun `create birthplace as unknown`() {
        //val fnr = getRandomNavFodselsnummer(MockTPS.TPSType.PERSON) ?: "02345678901"
        //val prefillData = generatePrefillData("P2000", fnr)

        val bruker = Bruker()
        bruker.foedested = null

        val result = preutfyllingTPS.hentFodested(bruker)

        assertNull(result)
        //assertEquals(null, result?.land)

    }
    @Test
    fun `create correct birthplace known`() {
        val bruker = Bruker()
        bruker.foedested = "NOR"

        val result = preutfyllingTPS.hentFodested(bruker)

        assertNotNull(result)
        assertEquals("NOR", result?.land)

    }

    @Test
    fun `create personAdresse`() {
        val landkode = Landkoder()
        landkode.value = "NOR"
        val postnr = Postnummer()
        postnr.value = "0123"
        val gateadresse = Gateadresse()
        gateadresse.husnummer = 12
        gateadresse.kommunenummer = "120"
        gateadresse.gatenavn = "Kirkeveien"
        gateadresse.landkode = landkode
        gateadresse.poststed = postnr

        //val struktadr = StrukturertAdresse()
        //struktadr.
        val bostedadr = Bostedsadresse()
        bostedadr.strukturertAdresse = gateadresse

        val person = Person()
        person.bostedsadresse = bostedadr

        val result = preutfyllingTPS.hentPersonAdresse(person)!!

        println(result)
        assertNotNull(result)

        assertEquals("NO", result.land)
        assertEquals( "Kirkeveien 12", result.gate)
        assertEquals("OSLO", result.by)
    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2000`() {
        PersonDataFromTPS.MockTPS.TPSType.PERSON
        val fnr = getRandomNavFodselsnummer(PersonDataFromTPS.MockTPS.TPSType.PERSON) ?: "02345678901"
        val prefillData = generatePrefillData("P2000", fnr)

        val response = prefillNav.prefill(prefillData)

        val sed = prefillData.sed
        sed.nav = response

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)
        assertEquals(2, sed.nav?.barn?.size)

    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2100`() {
        val fnr = getRandomNavFodselsnummer(PersonDataFromTPS.MockTPS.TPSType.PERSON) ?: "02345678901"
        val prefillData = generatePrefillData("P2100", fnr)
        val response = prefillNav.prefill(prefillData)

        val sed = prefillData.sed
        sed.nav = response

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)
        assertEquals("08", sed.nav?.bruker?.person?.sivilstand?.get(0)?.status)

        assertEquals(2, sed.nav?.barn?.size)

        val resultBarn = sed.nav?.barn

        val item1 = resultBarn.orEmpty().get(0)
        assertEquals("BOUWMANS", item1.person?.etternavn)
        assertEquals("TOPPI DOTTO", item1.person?.fornavn)
        val ident1 = item1.person?.pin?.get(0)?.identifikator
        val navfnr1 = NavFodselsnummer(ident1!!)
        assertEquals(false, navfnr1.isUnder18Year())
        assertEquals(37, navfnr1.getAge())


        val item2 = resultBarn.orEmpty().get(1)
        assertEquals("BOUWMANS", item2.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", item2.person?.fornavn)
        val ident = item2.person?.pin?.get(0)?.identifikator
        val navfnr = NavFodselsnummer(ident!!)
        assertEquals(true, navfnr.isUnder18Year())
    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2200`() {
        val fnr = getRandomNavFodselsnummer(MockTPS.TPSType.PERSON) ?: "02345678901"
        val prefillData = generatePrefillData("P2200", fnr)
        val response = prefillNav.prefill(prefillData)

        val sed = prefillData.sed
        sed.nav = response

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)
        assertEquals(2, sed.nav?.barn?.size)

        assertEquals("P2200", sed.sed)

    }


}