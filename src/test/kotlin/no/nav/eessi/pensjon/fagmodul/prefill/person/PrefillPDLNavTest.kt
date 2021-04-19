package no.nav.eessi.pensjon.fagmodul.prefill.person

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.ArbeidsforholdItem
import no.nav.eessi.pensjon.eux.model.sed.Bank
import no.nav.eessi.pensjon.eux.model.sed.BarnItem
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Ektefelle
import no.nav.eessi.pensjon.eux.model.sed.Foedested
import no.nav.eessi.pensjon.eux.model.sed.Foreldre
import no.nav.eessi.pensjon.eux.model.sed.Innehaver
import no.nav.eessi.pensjon.eux.model.sed.Konto
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.Sepa
import no.nav.eessi.pensjon.eux.model.sed.StatsborgerskapItem
import no.nav.eessi.pensjon.fagmodul.models.BrukerInformasjon
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.createPersonMedEktefellePartner
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.lagPerson
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medBarn
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medForeldre
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medKontaktadresseUtland
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedsel
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.Oppholdsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Statsborgerskap
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.services.geo.PostnummerService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PrefillPDLNavTest {

    @Mock
    lateinit var kodeverkClient: KodeverkClient

    lateinit var prefillPDLNav: PrefillPDLNav

    private val somePenSaksnr = "somePenSaksnr"
    private val someInstitutionId = "enInstId"
    private val someIntitutionNavn = "instNavn"

    @BeforeEach
    fun beforeStart() {
        prefillPDLNav = PrefillPDLNav(
                PrefillPDLAdresse(PostnummerService(), kodeverkClient),
                someInstitutionId,
                someIntitutionNavn)


    }

    @Test
    fun `minimal prefill med barn`() {
        val foreldersPin = FodselsnummerMother.generateRandomFnr(40)
        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2100, pinId = foreldersPin, penSaksnummer = somePenSaksnr, avdod = null)
        val barnetsPin = FodselsnummerMother.generateRandomFnr(13)

        val forelder = lagPerson(foreldersPin, "Christopher", "Robin").medBarn(barnetsPin)
        val barn = lagPerson(barnetsPin, "Ole", "Brum").medForeldre(forelder)

        val foreldreFdato = forelder.foedsel?.foedselsdato?.toString()
        val barnFdato = barn.foedsel?.foedselsdato?.toString()

        val personDataCollection = PersonDataCollection(forsikretPerson = forelder, ektefellePerson = null, sivilstandstype = Sivilstandstype.UGIFT, gjenlevendeEllerAvdod = forelder, barnPersonList = listOf(barn))

        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")


        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )
        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
                bruker = Bruker(
                        person = lagNavPerson(foreldersPin, "Christopher", "Robin", foreldreFdato!!, someInstitutionId, someIntitutionNavn),
                        adresse = lagTomAdresse()
                ),
                barn = listOf(
                    BarnItem(
                        person = lagNavPerson(barnetsPin, "Ole", "Brum", barnFdato!!, someInstitutionId, someIntitutionNavn),
                        far = Foreldre(
                            Person(
                            fornavn = "Christopher",
                            pin = listOf(PinItem(identifikator = foreldersPin, land = "NO", institusjonsid = "enInstId", institusjonsnavn = "instNavn")))
                        ),
                        relasjontilbruker = "BARN")
                ))

        assertEquals(expected, actual)
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)

    }

    @Test
    fun `prefill med barn og relasjon Far`() {
        val somePersonNr = FodselsnummerMother.generateRandomFnr(57)
        val someBarnPersonNr = FodselsnummerMother.generateRandomFnr(17)

        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2100, pinId = somePersonNr, avdod = PersonId(someBarnPersonNr, "123232312312"), penSaksnummer = somePenSaksnr)

        val far = lagPerson(somePersonNr, "Ole", "Brum").medBarn(someBarnPersonNr)
        val barn = lagPerson(someBarnPersonNr, "Nasse", "Nøff").medForeldre(far)

        //fdato
        val farfdato = far.foedsel?.foedselsdato?.toString()
        val barnfdato = barn.foedsel?.foedselsdato?.toString()

        val personDataCollection = PersonDataCollection(forsikretPerson = far, ektefellePerson = null, sivilstandstype = Sivilstandstype.UGIFT, gjenlevendeEllerAvdod = far, barnPersonList = listOf(barn))
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )
        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
                bruker = Bruker(
                        person = lagNavPerson(somePersonNr, "Ole", "Brum", farfdato!!, someInstitutionId, someIntitutionNavn),
                        adresse = lagTomAdresse()),
                barn = listOf(BarnItem(
                        mor = null,
                        far = Foreldre(Person(
                                fornavn = "Ole",
                                pin = listOf(PinItem(identifikator = somePersonNr, land = "NO", institusjonsid = "enInstId", institusjonsnavn = "instNavn")))),
                        person = lagNavPerson(someBarnPersonNr, "Nasse", "Nøff", barnfdato!!, someInstitutionId, someIntitutionNavn), relasjontilbruker = "BARN")))

        assertEquals(expected, actual)
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)
    }

    @Test
    fun `prefill med familie relasjon person og ektefelle`() {

        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val somerEktefellePersonNr = FodselsnummerMother.generateRandomFnr(50)

        val personFdato =  NavFodselsnummer(somePersonNr).getBirthDate().toString()
        val ektefellFdato = NavFodselsnummer(somerEktefellePersonNr).getBirthDate().toString()

        //ektefelle
        val pair = createPersonMedEktefellePartner(somePersonNr, somerEktefellePersonNr, Sivilstandstype.GIFT)

        val person = pair.first
        val ektefelle = pair.second

        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2000, pinId = somePersonNr, penSaksnummer = somePenSaksnr)
        val personDataCollection = PersonDataCollection(forsikretPerson = person, ektefellePerson = ektefelle, sivilstandstype = Sivilstandstype.GIFT, gjenlevendeEllerAvdod = person, barnPersonList = emptyList())
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
                bruker = Bruker(
                        person = lagNavPerson(somePersonNr, "Ola", "Testbruker", personFdato, someInstitutionId, someIntitutionNavn),
                        adresse = lagTomAdresse()
                ),
                ektefelle = Ektefelle(
                        person = lagNavPerson(somerEktefellePersonNr, "Jonna", "Dolla", ektefellFdato, someInstitutionId, someIntitutionNavn, "K"),
                        type = "ektefelle"
                )
        )

        assertEquals(expected, actual)
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)

    }

    @Test
    fun `prefill komplett familierelasjon og sivilstand`() {
        //generer fnr
        val farfnr = FodselsnummerMother.generateRandomFnr(42)
        val morfnr = FodselsnummerMother.generateRandomFnr(41)
        val barn1 = FodselsnummerMother.generateRandomFnr(11)
        val barn2 = FodselsnummerMother.generateRandomFnr(13)

        val personFdato = NavFodselsnummer(farfnr).getBirthDateAsString()
        val ektefellFdato = NavFodselsnummer(morfnr).getBirthDateAsString()
        val barnetfdato = NavFodselsnummer(barn1).getBirthDateAsString()
        val barntofdato = NavFodselsnummer(barn2).getBirthDateAsString()

        //far og mor i pair
        val pair = createPersonMedEktefellePartner(farfnr, morfnr, Sivilstandstype.GIFT)

        //far og mor med barn
        val far = pair.first.medAdresse("STORGATA").medBarn(barn1).medBarn(barn2)
        val mor = pair.second.medAdresse("STORGATA").medBarn(barn1).medBarn(barn2)

        //barn
        val barnet = lagPerson(barn1, fornavn = "OLE", etternavn = "BRUM").medForeldre(far).medForeldre(mor)
        val barnto = lagPerson(barn2, fornavn = "NASSE", etternavn = "NØFF").medForeldre(far).medForeldre(mor)

        val personDataCollection = PersonDataCollection(forsikretPerson = far, ektefellePerson = mor, sivilstandstype = Sivilstandstype.GIFT, gjenlevendeEllerAvdod = far, barnPersonList = listOf(barnet, barnto))
        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2200, pinId = farfnr, penSaksnummer = somePenSaksnr)

        //landkode NO
        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
            eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
            bruker = Bruker(
                person = lagNavPerson(farfnr, "Ola", "Testbruker", personFdato, someInstitutionId, someIntitutionNavn),
                adresse = Adresse("STORGATA 12", postnummer = "0101", by = "OSLO", land = "NO")
            ),
            ektefelle = Ektefelle(
                person = lagNavPerson(morfnr, "Jonna", "Dolla", ektefellFdato, someInstitutionId, someIntitutionNavn, "K"),
                type = "ektefelle"
            ),
            barn = listOf(
                BarnItem(
                    mor = Foreldre(Person(
                        fornavn = "Jonna",
                        pin = listOf(PinItem(identifikator = morfnr, land = "NO", institusjonsid = "enInstId", institusjonsnavn = "instNavn")))),
                    far = Foreldre(Person(
                        fornavn = "Ola",
                        pin = listOf(PinItem(identifikator = farfnr, land = "NO", institusjonsid = "enInstId", institusjonsnavn = "instNavn")))),
                    person = lagNavPerson(barn1, "OLE", "BRUM", barnetfdato, someInstitutionId, someIntitutionNavn), relasjontilbruker = "BARN"),
                BarnItem(
                    mor = Foreldre(Person(
                        fornavn = "Jonna",
                        pin = listOf(PinItem(identifikator = morfnr, land = "NO", institusjonsid = "enInstId", institusjonsnavn = "instNavn")))),
                    far = Foreldre(Person(
                        fornavn = "Ola",
                        pin = listOf(PinItem(identifikator = farfnr, land = "NO", institusjonsid = "enInstId", institusjonsnavn = "instNavn")))),
                    person = lagNavPerson(barn2, "NASSE", "NØFF", barntofdato, someInstitutionId, someIntitutionNavn), relasjontilbruker = "BARN")
                ))

        assertEquals(expected, actual)
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)

    }


    @Test
    fun `prefill med samboerpar relasjon person og partner`() {
        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val somerEktefellePersonNr = FodselsnummerMother.generateRandomFnr(50)

        val personfnr = NavFodselsnummer(somePersonNr)
        val ektefnr = NavFodselsnummer(somerEktefellePersonNr)
        val personFdato = personfnr.getBirthDate().toString()
        val ektefellFdato = ektefnr.getBirthDate().toString()

        val pair = createPersonMedEktefellePartner(somePersonNr, somerEktefellePersonNr, Sivilstandstype.REGISTRERT_PARTNER)

        val person = pair.first
        val partner = pair.second


        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2000, pinId = somePersonNr, penSaksnummer = somePenSaksnr)
        val personDataCollection = PersonDataCollection(forsikretPerson = person, ektefellePerson = partner, sivilstandstype = Sivilstandstype.REGISTRERT_PARTNER, gjenlevendeEllerAvdod = person, barnPersonList = emptyList())

        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
                bruker = Bruker(
                        person = lagNavPerson(somePersonNr, "Ola", "Testbruker", personFdato, someInstitutionId, someIntitutionNavn),
                        adresse = lagTomAdresse()
                ),
                ektefelle = Ektefelle(
                        person = lagNavPerson(somerEktefellePersonNr, "Jonna", "Dolla", ektefellFdato, someInstitutionId, someIntitutionNavn, "K"),
                        type = "part_i_et_registrert_partnerskap"
                )
        )

        assertEquals(expected, actual)
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)

    }

    @Test
    fun `prefill person singel med mellomnavn`() {
        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)

        val personfnr = NavFodselsnummer(somePersonNr)
        val personFdato = personfnr.getBirthDate().toString()

        val single = lagPerson(somePersonNr, "Ola", "Testbruker").copy(navn = Navn("Fornavn Ole","Mellomnavn Mellomn", "Test Bruker", null, null, null, LagPDLPerson.mockMeta()))

        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2000, pinId = somePersonNr, penSaksnummer = somePenSaksnr)
        val personDataCollection = PersonDataCollection(forsikretPerson = single, ektefellePerson = null,  sivilstandstype = Sivilstandstype.UGIFT, gjenlevendeEllerAvdod = single, barnPersonList = emptyList())
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
                bruker = Bruker(
                        person = lagNavPerson(somePersonNr, "Fornavn Ole Mellomnavn Mellomn", "Test Bruker", personFdato, someInstitutionId, someIntitutionNavn),
                        adresse = lagTomAdresse()
                )
        )

        assertEquals(expected, actual)
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)

    }

    @Test
    fun `prefill person med utlandsadresse fra oppholdsadresse`() {
        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val personfnr = NavFodselsnummer(somePersonNr)
        val personFdato = personfnr.getBirthDate().toString()

        val single = lagPerson(somePersonNr)
            .copy(bostedsadresse = null, oppholdsadresse = Oppholdsadresse(
                LocalDateTime.of(2000, 10, 2, 9, 32, 1),
                null,
                null,
                UtenlandskAdresse(
                    "Storavegsentra 12, Noenhusbygg, 2012 SE, Østaby",
                    "örasund",
                    null,
                    "SWE",
                    null,
                    null,
                    null
                ),
                LagPDLPerson.mockMeta()
            ))

        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2000, pinId = somePersonNr, penSaksnummer = somePenSaksnr)
        val personDataCollection = PersonDataCollection(forsikretPerson = single, ektefellePerson = null,  sivilstandstype = Sivilstandstype.UGIFT, gjenlevendeEllerAvdod = single, barnPersonList = emptyList())
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
            eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
            bruker = Bruker(
                person = lagNavPerson(somePersonNr, "OLE", "OLSEN", personFdato, someInstitutionId, someIntitutionNavn),
                adresse = Adresse(
                    "",
                    "",
                    "",
                    "",
                    null,
                    ""
                )
            )
        )

        assertEquals(expected, actual)
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)

    }

    @Test
    fun `prefill person med kontaktadresse og utlandsadresse i frittformat`() {
        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val personfnr = NavFodselsnummer(somePersonNr)
        val personFdato = personfnr.getBirthDate().toString()

        val single = lagPerson(somePersonNr)
            .copy(bostedsadresse = null, oppholdsadresse = Oppholdsadresse(
                LocalDateTime.of(2000, 10, 2, 9, 32, 1),
                null,
                null,
                UtenlandskAdresse(
                    "Adresselinje 1, Adresselinje 2, Adresselinje 3",
                    null,
                    null,
                    "SWE",
                    null,
                    null,
                    null
                ),
                LagPDLPerson.mockMeta()
            )).medKontaktadresseUtland()

        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2000, pinId = somePersonNr, penSaksnummer = somePenSaksnr)
        val personDataCollection = PersonDataCollection(forsikretPerson = single, ektefellePerson = null,  sivilstandstype = Sivilstandstype.UGIFT, gjenlevendeEllerAvdod = single, barnPersonList = emptyList())
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")
        doReturn("SE").`when`(kodeverkClient).finnLandkode2("SWE")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
            eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
            bruker = Bruker(
                person = lagNavPerson(somePersonNr, "OLE", "OLSEN", personFdato, someInstitutionId, someIntitutionNavn),
                adresse = Adresse(
                    "Adresselinje 1",
                    "Adresselinje 2",
                    "Adresselinje 3",
                    null,
                    null,
                    "SE"
                )
            )
        )

        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)
    }

    @Test
    fun `prefill person utland med oppholdsadresse samt kontaktadresse i frittformat`() {
        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val personfnr = NavFodselsnummer(somePersonNr)
        val personFdato = personfnr.getBirthDate().toString()

        val single = lagPerson(somePersonNr)
            .copy(bostedsadresse = null, oppholdsadresse = Oppholdsadresse(
                LocalDateTime.of(2000, 10, 2, 9, 32, 1),
                null,
                null,
                UtenlandskAdresse(
                    "Adresselinje 1, Adresselinje 2, Adresselinje 3",
                    "utenland by",
                    null,
                    "SWE",
                    null,
                    "postkoden",
                    null
                ),
                LagPDLPerson.mockMeta()
            )).medKontaktadresseUtland()

        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2000, pinId = somePersonNr, penSaksnummer = somePenSaksnr)
        val personDataCollection = PersonDataCollection(forsikretPerson = single, ektefellePerson = null,  sivilstandstype = Sivilstandstype.UGIFT, gjenlevendeEllerAvdod = single, barnPersonList = emptyList())
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")
        doReturn("SE").`when`(kodeverkClient).finnLandkode2("SWE")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
            eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
            bruker = Bruker(
                person = lagNavPerson(somePersonNr, "OLE", "OLSEN", personFdato, someInstitutionId, someIntitutionNavn),
                adresse = Adresse(
                    "Adresselinje 1",
                    "Adresselinje 2",
                    "Adresselinje 3",
                    null,
                    null,
                    "SE"
                )
            )
        )

        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)
    }


    @Test
    fun `minimal prefill med brukerinfo på request`() {
        val somePersonNr = FodselsnummerMother.generateRandomFnr(60)
        val personfnr = NavFodselsnummer(somePersonNr)
        val personFdato = personfnr.getBirthDate().toString()

        val prefillData = PrefillDataModelMother.initialPrefillDataModel(SedType.P2000, pinId = somePersonNr, penSaksnummer = somePenSaksnr).apply {
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

        val person = lagPerson(somePersonNr, "Ole", "Brum")
        val personDataCollection = PersonDataCollection(forsikretPerson = person, ektefellePerson = null,  sivilstandstype = Sivilstandstype.UGIFT, gjenlevendeEllerAvdod = person, barnPersonList = emptyList())
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")

        val actual = prefillPDLNav.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            personDataCollection,
            prefillData.getPersonInfoFromRequestData(),
            null,
            null
        )

        val expected = Nav(
                eessisak = listOf(EessisakItem(institusjonsid = someInstitutionId, institusjonsnavn = someIntitutionNavn, saksnummer = somePenSaksnr, land = "NO")),
                bruker = Bruker(person = lagNavPerson(somePersonNr, "Ole", "Brum", personFdato, someInstitutionId, someIntitutionNavn),
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
        JSONAssert.assertEquals(expected.toJsonSkipEmpty(), actual.toJsonSkipEmpty(), true)

    }

    @Test
    fun `create birthplace as unknown`() {
        val person = lagPerson().copy(foedsel = null)
        val result = prefillPDLNav.createFodested(person)

        assertNull(result)
    }

    @Test
    fun `create correct birthplace known`() {
        val person = lagPerson()
        val nyPerson = person.copy(foedsel = Foedsel(person.foedsel?.foedselsdato, "NOR", "OSLO", null, null, LagPDLPerson.mockMeta()))
        doReturn("NO").`when`(kodeverkClient).finnLandkode2("NOR")


        val result = prefillPDLNav.createFodested(nyPerson)

        assertNotNull(result)
        assertEquals("NO", result?.land)
    }

    @Test
    fun `isPersonAvdod gir true`() {
        val dodPerson = lagPerson().copy(doedsfall = Doedsfall(LocalDate.of(2010, 10, 1), null, LagPDLPerson.mockMeta()))
        assertEquals (true, PrefillPDLNav.isPersonAvdod(dodPerson))
    }
    @Test
    fun `isPersonAvdod gir false`() {
        val person = lagPerson()
        assertEquals (false, PrefillPDLNav.isPersonAvdod(person))
    }

    @Test
    fun `Gitt en person med kosovo statsborgerskap Når preutfyller Statsborgerstak Så preutfyll tomt statsborgerskap`() {
        val personfnr = FodselsnummerMother.generateRandomFnr(40)
        val person = lagPerson(personfnr).copy(statsborgerskap = listOf(Statsborgerskap("XXK", LocalDate.of(2000, 10, 1), LocalDate.of(2300, 10, 1), LagPDLPerson.mockMeta())))

        val bruker = prefillPDLNav.createBruker(person, bank = null, ansettelsesforhold = null)

        assertEquals(bruker!!.person!!.statsborgerskap!!.size, 1)
        assertNull(bruker.person!!.statsborgerskap!![0].land)
    }

    @Test
    fun `Gitt en person med noe annet enn kosovo statsborgerskap Når preutfyller Statsborgerstak Så preutfyll statsborgerskap`() {
        val personfnr = FodselsnummerMother.generateRandomFnr(40)
        val person = lagPerson(personfnr).copy(statsborgerskap = listOf(Statsborgerskap("NOR", LocalDate.of(2000, 10, 1), LocalDate.of(2300, 10, 1), LagPDLPerson.mockMeta())))

        doReturn("NO")
            .doReturn("NO")
            .doReturn("NO")
            .whenever(kodeverkClient).finnLandkode2("NOR")

        val bruker = prefillPDLNav.createBruker(person, null, null)

        assertEquals(bruker!!.person!!.statsborgerskap!!.size, 1)
        assertEquals(bruker.person!!.statsborgerskap!![0].land, "NO")
    }

    @Test
    fun `Gitt en person uten fdato skal benytte fnr for fdato så SED blir preutfylt`() {
        val personfnr = "01028143352"
        val person = lagPerson(personfnr).copy(foedsel = null)

        val bruker = prefillPDLNav.createBruker(person, null, null)

        assertEquals("1981-02-01", bruker?.person?.foedselsdato)

        val dnr = "41028143352"
        val personDnr = lagPerson(dnr).copy(foedsel = null)
        val brukerDnr = prefillPDLNav.createBruker(personDnr, null, null)

        assertEquals("1981-02-01", brukerDnr?.person?.foedselsdato)
    }

    companion object {
        private fun lagTomAdresse(): Adresse {
            return Adresse(
                gate = "",
                bygning = "",
                by = "",
                postnummer = "",
                land = "")
        }

        fun lagNavPerson(foreldersPin: String, fornavn: String, etternavn: String, fdato: String, someInstitutionId: String? = null, someIntitutionNavn: String? = null, kjoenn: String? = "M", foedsted: String? = "NO") =
            Person(
                pin = listOf(PinItem(
                    institusjonsnavn = someIntitutionNavn,
                    institusjonsid = someInstitutionId,
                    identifikator = foreldersPin,
                    land = "NO")
                ),
                statsborgerskap = listOf(StatsborgerskapItem(land = "NO")),
                etternavn = etternavn,
                fornavn = fornavn,
                kjoenn = kjoenn,
                foedselsdato = fdato,
                foedested = if (foedsted == null) null else Foedested("Unknown", foedsted, region = ""),
                )
    }

}