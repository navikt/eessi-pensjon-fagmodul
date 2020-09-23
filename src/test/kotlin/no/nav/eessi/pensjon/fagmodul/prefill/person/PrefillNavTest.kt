package no.nav.eessi.pensjon.fagmodul.prefill.person

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.prefill.model.BrukerInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.services.geo.PostnummerService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.convertToXMLocal
import no.nav.eessi.pensjon.utils.createXMLCalendarFromString
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PrefillNavTest {

    @Mock
    lateinit var kodeverkClient: KodeverkClient

    lateinit var prefillNav: PrefillNav

    private val somePenSaksnr = "somePenSaksnr"
    private val someInstitutionId = "enInstId"
    private val someIntitutionNavn = "instNavn"
    private val mockTpsPersonService = mock<PersonV3Service>()

    @BeforeEach
    fun beforeStart() {

        prefillNav = PrefillNav(
                PrefillAdresse(PostnummerService(), kodeverkClient),
                someInstitutionId,
                someIntitutionNavn)
    }

    @Test
    fun `minimal prefill med barn`() {
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val foreldersPin = "somePersonNr"
        val prefillData = PrefillDataModel(penSaksnummer = somePenSaksnr, bruker = PersonId(foreldersPin, "dummy"), avdod = null)
        val barnetsPin = "12345678901"

        val forelder = lagTPSBruker(foreldersPin, "Christopher", "Robin").medBarn(barnetsPin)
        val barn = lagTPSBruker(barnetsPin, "Ole", "Brum")

        val personData = PersonData(forsikretPerson = forelder, ektefelleBruker = null, ekteTypeValue = "", gjenlevendeEllerAvdod = forelder, barnBrukereFraTPS = listOf(barn))

        val actual = prefillNav.prefill(prefillData.penSaksnummer, prefillData.bruker, prefillData.avdod, personData, prefillData.getPersonInfoFromRequestData())

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid =  someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer =  somePenSaksnr, land =  "NO")),
                bruker = Bruker(
                        person = lagPerson(foreldersPin, "Christopher", "Robin"),
                        adresse = lagTomAdresse()),
                barn = listOf(BarnItem(
                        person = lagPerson(barnetsPin, "Ole", "Brum"),
                        relasjontilbruker = "BARN")))

        assertEquals(expected, actual)
    }


    @Test
    fun `prefill med barn og relasjon Far`() {
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val somePersonNr = FodselsnummerMother.generateRandomFnr(57)
        val someBarnPersonNr = FodselsnummerMother.generateRandomFnr(17)

        val prefillData = PrefillDataModel(penSaksnummer = somePenSaksnr, bruker = PersonId(somePersonNr, "dummy"), avdod = null)

        val barn = lagTPSBruker(someBarnPersonNr, "Nasse", "Nøff")
        val far = lagTPSBruker(somePersonNr, "Ole", "Brum")
                .withHarFraRolleI(Familierelasjon().withTilRolle(Familierelasjoner().withValue("BARN")).withTilPerson(barn))

        barn.withHarFraRolleI(Familierelasjon().withTilRolle(Familierelasjoner().withValue("FARA")).withTilPerson(far))

        val personData = PersonData(forsikretPerson = far, ektefelleBruker = null, ekteTypeValue = "", gjenlevendeEllerAvdod = far, barnBrukereFraTPS = listOf(barn))

        val actual = prefillNav.prefill(prefillData.penSaksnummer, prefillData.bruker, prefillData.avdod, personData, prefillData.getPersonInfoFromRequestData())
        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid =  someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer =  somePenSaksnr, land =  "NO")),
                bruker = Bruker(
                        person = lagPerson(somePersonNr, "Ole", "Brum"),
                        adresse = lagTomAdresse()),
                barn = listOf(BarnItem(
                        mor = null,
                        far = Foreldre(Person(
                                fornavn = "Ole",
                                etternavnvedfoedsel = null,
                                pin = listOf( PinItem(identifikator = somePersonNr, land = "NO", institusjonsid = "enInstId", institusjonsnavn = "instNavn")))),
                        person = lagPerson(someBarnPersonNr, "Nasse", "Nøff"), relasjontilbruker = "BARN")))

        assertEquals(expected, actual)
    }

    @Test
    fun `prefill med familie relasjon person og ektefelle`() {
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val somerEktefellePersonNr = FodselsnummerMother.generateRandomFnr(50)

        val personfnr = NavFodselsnummer(somePersonNr)
        val ektefnr = NavFodselsnummer(somerEktefellePersonNr)
        val personFdato = personfnr.getBirthDate().toString()
        val ektefellFdato = ektefnr.getBirthDate().toString()

        val pair = createPersonMedEktefellPartnet(somePersonNr, somerEktefellePersonNr, "EKTE")
        val person = pair.first
        val ektefelle = pair.second

        val prefillData = PrefillDataModel(penSaksnummer = somePenSaksnr, bruker = PersonId(somePersonNr, "dummy"), avdod = null)
        val personData = PersonData(forsikretPerson = person, ektefelleBruker = ektefelle, ekteTypeValue = "EKTE", gjenlevendeEllerAvdod = person, barnBrukereFraTPS = listOf())

        val actual = prefillNav.prefill(prefillData.penSaksnummer, prefillData.bruker, prefillData.avdod, personData, prefillData.getPersonInfoFromRequestData())

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid =  someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer =  somePenSaksnr, land =  "NO")),
                bruker = Bruker(
                        person = lagPerson(somePersonNr, "Ola", "Testbruker", personFdato),
                        adresse = lagTomAdresse()
                ),
                ektefelle = Ektefelle(
                        person = lagPerson(somerEktefellePersonNr, "Jonna", "Dolla", ektefellFdato),
                        type = "ektefelle"
                )
        )
        assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty())
    }

    @Test
    fun `prefill med samboerpar relasjon person og partner`() {
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val somerEktefellePersonNr = FodselsnummerMother.generateRandomFnr(50)

        val personfnr = NavFodselsnummer(somePersonNr)
        val ektefnr = NavFodselsnummer(somerEktefellePersonNr)
        val personFdato = personfnr.getBirthDate().toString()
        val ektefellFdato = ektefnr.getBirthDate().toString()

        val pair = createPersonMedEktefellPartnet(somePersonNr, somerEktefellePersonNr, "REPA")
        val person = pair.first
        val ektefelle = pair.second

        val prefillData = PrefillDataModel(penSaksnummer = somePenSaksnr, bruker = PersonId(somePersonNr, "dummy"), avdod = null)

        val personData = PersonData(forsikretPerson = person, ektefelleBruker = ektefelle, ekteTypeValue = "REPA", gjenlevendeEllerAvdod = person, barnBrukereFraTPS = listOf())

        val actual = prefillNav.prefill(prefillData.penSaksnummer, prefillData.bruker, prefillData.avdod, personData, prefillData.getPersonInfoFromRequestData())

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid =  someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer =  somePenSaksnr, land =  "NO")),
                bruker = Bruker(
                        person = lagPerson(somePersonNr, "Ola", "Testbruker", personFdato),
                        adresse = lagTomAdresse()
                ),
                ektefelle = Ektefelle(
                        person = lagPerson(somerEktefellePersonNr, "Jonna", "Dolla", ektefellFdato),
                        type = "part_i_et_registrert_partnerskap"
                )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `prefill med samboer relasjon person og bofellesskap`() {
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val somerEktefellePersonNr = FodselsnummerMother.generateRandomFnr(50)

        val personfnr = NavFodselsnummer(somePersonNr)
        val ektefnr = NavFodselsnummer(somerEktefellePersonNr)
        val personFdato = personfnr.getBirthDate().toString()
        val ektefellFdato = ektefnr.getBirthDate().toString()

        val pair = createPersonMedEktefellPartnet(somePersonNr, somerEktefellePersonNr, "SAMB")
        val person = pair.first
        val ektefelle = pair.second

        val prefillData = PrefillDataModel(penSaksnummer = somePenSaksnr, bruker = PersonId(somePersonNr, "dummy"), avdod = null)

        val personData = PersonData(forsikretPerson = person, ektefelleBruker = ektefelle, ekteTypeValue = "SAMB", gjenlevendeEllerAvdod = person, barnBrukereFraTPS = listOf())

        val actual = prefillNav.prefill(prefillData.penSaksnummer, prefillData.bruker, prefillData.avdod, personData, prefillData.getPersonInfoFromRequestData())

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid =  someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer =  somePenSaksnr, land =  "NO")),
                bruker = Bruker(
                        person = lagPerson(somePersonNr, "Ola", "Testbruker", personFdato),
                        adresse = lagTomAdresse()
                ),
                ektefelle = Ektefelle(
                        person = lagPerson(somerEktefellePersonNr, "Jonna", "Dolla", ektefellFdato),
                        type = "samboer"
                )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `prefill person singel med mellomnavn`() {
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)

        val personfnr = NavFodselsnummer(somePersonNr)
        val personFdato = personfnr.getBirthDate().toString()

        val person = lagTPSBruker(somePersonNr, "Ola", "Testbruker")
        person.personnavn = Personnavn().withEtternavn("Test Bruker").withMellomnavn("Mellomnavn Mellomn").withFornavn("Fornavn Ole").withSammensattNavn("Ole Test Bruker")
        person.foedselsdato = Foedselsdato().withFoedselsdato( convertToXMLocal(personfnr.getBirthDate()))

        val prefillData = PrefillDataModel(penSaksnummer = somePenSaksnr, bruker = PersonId(somePersonNr, "dummy"), avdod = null)

        doReturn(person)
                .whenever(mockTpsPersonService)
                .hentBruker(somePersonNr)

        val personData = PersonData(forsikretPerson = person, ektefelleBruker = null, ekteTypeValue = "", gjenlevendeEllerAvdod = person, barnBrukereFraTPS = listOf())

        val actual = prefillNav.prefill(prefillData.penSaksnummer, prefillData.bruker, prefillData.avdod, personData, prefillData.getPersonInfoFromRequestData())

        val fornavn = "Fornavn Ole Mellomnavn Mellomn"
        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid =  someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer =  somePenSaksnr, land =  "NO")),
                bruker = Bruker(
                        person = lagPerson(somePersonNr, fornavn, "Test Bruker", personFdato),
                        adresse = lagTomAdresse()
                )
        )
        assertEquals(expected, actual)
    }



    @Test
    fun `minimal prefill med brukerinfo på request`() {
        val brukerensPin = "somePersonNr"
        val prefillData = PrefillDataModel(penSaksnummer = somePenSaksnr, bruker = PersonId(brukerensPin, "dummy"), avdod = null).apply {
            partSedAsJson["PersonInfo"] = mapAnyToJson(
                    BrukerInformasjon(
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Nordnb",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null))
        }

        val brukeren = lagTPSBruker(brukerensPin, "Ole", "Brum")

        whenever(mockTpsPersonService.hentBruker(brukerensPin)).thenReturn(brukeren)
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val personData = PersonData(forsikretPerson = brukeren, ektefelleBruker = null, ekteTypeValue = "", gjenlevendeEllerAvdod = brukeren, barnBrukereFraTPS = listOf())

        val actual = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData, brukerInformasjon = prefillData.getPersonInfoFromRequestData())

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid =  someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer =  somePenSaksnr, land =  "NO")),
                bruker = Bruker(person = lagPerson(brukerensPin, "Ole", "Brum"),
                        arbeidsforhold = listOf(ArbeidsforholdItem(
                                planlagtstartdato = "",
                                arbeidstimerperuke = "",
                                planlagtpensjoneringsdato = "",
                                yrke = "",
                                type = "",
                                sluttdato = "")),
                        bank = Bank(
                                navn = "Nordnb",
                                konto = Konto(
                                        sepa = Sepa(),
                                        innehaver = Innehaver(
                                                rolle = "01",
                                                navn = "Nordnb")),
                                adresse = Adresse()),
                        adresse = lagTomAdresse()))

        assertEquals(expected, actual)
    }

    private fun createPersonMedEktefellPartnet(personPersonnr: String, ektefellePersonnr: String, relasjonType: String) : Pair<no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker, no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker> {
        val personfnr = NavFodselsnummer(personPersonnr)
        val ektefnr = NavFodselsnummer(ektefellePersonnr)

        val ektefelle = lagTPSBruker(ektefellePersonnr, "Jonna", "Dolla")
        val person    = lagTPSBruker(personPersonnr, "Ola", "Testbruker")

        person.withHarFraRolleI(Familierelasjon().withTilRolle(Familierelasjoner().withValue(relasjonType)).withTilPerson(ektefelle))
        person.withFoedselsdato(Foedselsdato().withFoedselsdato( convertToXMLocal(personfnr.getBirthDate())))

        ektefelle.withHarFraRolleI(Familierelasjon().withTilRolle(Familierelasjoner().withValue(relasjonType)).withTilPerson(person))
        ektefelle.withFoedselsdato(Foedselsdato().withFoedselsdato( convertToXMLocal(ektefnr.getBirthDate())))

        return Pair<no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker, no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker>(person, ektefelle)
    }

    private fun lagPerson(foreldersPin: String, fornavn: String, etternavn: String) = lagPerson(foreldersPin, fornavn, etternavn, null)
    private fun lagPerson(foreldersPin: String, fornavn: String, etternavn: String, fdato: String?) =
            Person(
                    pin = listOf(PinItem(
                            institusjonsnavn = someIntitutionNavn,
                            institusjonsid = someInstitutionId,
                            identifikator = foreldersPin,
                            land = "NO")),
                    statsborgerskap = listOf(StatsborgerskapItem(land = "NO")),
                    etternavn = etternavn,
                    fornavn = fornavn,
                    kjoenn = "M",
                    foedselsdato = fdato,
                    fornavnvedfoedsel = null)

    private fun lagTPSBruker(foreldersPin: String, fornavn: String, etternavn: String) =
            no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker()
                    .withPersonnavn(Personnavn()
                            .withEtternavn(etternavn)
                            .withFornavn(fornavn))
                    .withKjoenn(Kjoenn().withKjoenn(Kjoennstyper().withValue("M")))
                    .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(foreldersPin)))
                    .withStatsborgerskap(Statsborgerskap().withLand(Landkoder().withValue("NOR")))

    private fun no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker.medBarn(barnetsPin: String): no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker =
            this
                    .withHarFraRolleI(Familierelasjon()
                            .withTilRolle(Familierelasjoner()
                                    .withValue("BARN"))
                            .withTilPerson(no.nav.tjeneste.virksomhet.person.v3.informasjon.Person()
                                    .withAktoer(PersonIdent()
                                            .withIdent(NorskIdent()
                                                    .withIdent(barnetsPin)))))


    private fun lagTomAdresse(): Adresse {
        return Adresse(
                gate = "",
                bygning = "",
                by = "",
                postnummer = "",
                land = "")
    }

    @Test
    fun testPaahentSivilstandArr9999BlirBlank() {
        val brukerTps = mockBrukerSivilstandTps("9999-01-01", "SKPQ")

        val actualList = PrefillNav.createSivilstand(brukerTps)
        val actual = actualList[0]

        assertEquals("", actual.fradato)
        assertEquals(null, actual.status)
    }

    @Test
    fun testPaahentSivilstandOk() {
        val brukerTps = mockBrukerSivilstandTps("1999-01-01", "UGIF")

        val actualList = PrefillNav.createSivilstand(brukerTps)
        val actual = actualList[0]

        assertEquals("1999-01-01", actual.fradato)
        assertEquals("01", actual.status)
    }

    @Test
    fun testPaahentSivilstandAar2099() {
        val brukerTps = mockBrukerSivilstandTps("2499-12-01", "REPA")

        val actualList = PrefillNav.createSivilstand(brukerTps)
        val actual = actualList[0]

        assertEquals("2499-12-01", actual.fradato)
        assertEquals("04", actual.status)
    }


    private fun mockBrukerSivilstandTps(gyldigPeriode: String, sivilstandType: String): no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker {
        val brukerTps = no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker()
        brukerTps.endretAv = "Test"
        brukerTps.foedested = "OSLO"

        val personnavnTps = Personnavn()
        personnavnTps.mellomnavn = "Dummy Absurd"
        personnavnTps.fornavn = "Dummy"
        personnavnTps.etternavn = "Absurd"
        brukerTps.personnavn = personnavnTps

        val sivilstandTps = Sivilstand()
        sivilstandTps.endretAv = "Test"
        sivilstandTps.fomGyldighetsperiode = createXMLCalendarFromString(gyldigPeriode)

        val sivilstanderTps = Sivilstander()
        sivilstanderTps.value = sivilstandType

        sivilstandTps.sivilstand = sivilstanderTps
        brukerTps.sivilstand = sivilstandTps

        return brukerTps
    }

    @Test
    fun `create birthplace as unknown`() {
        val bruker = no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker()
        bruker.foedested = null

        val result = PrefillNav.createFodested(bruker)

        assertNull(result)
    }

    @Test
    fun `create correct birthplace known`() {
        val bruker = no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker()
        bruker.foedested = "NOR"

        val result = PrefillNav.createFodested(bruker)

        assertNotNull(result)
        assertEquals("NOR", result?.land)
    }

    @Test
    fun `isPersonAvdod gir true`() {
        val person = no.nav.tjeneste.virksomhet.person.v3.informasjon.Person()
                .withPersonstatus(Personstatus()
                        .withPersonstatus(Personstatuser()
                                .withValue("DØD")))

        assertTrue(PrefillNav.isPersonAvdod(person))
    }
}
