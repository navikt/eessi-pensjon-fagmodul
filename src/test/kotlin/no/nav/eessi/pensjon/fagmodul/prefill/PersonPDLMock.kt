package no.nav.eessi.pensjon.fagmodul.prefill

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedsel
import no.nav.eessi.pensjon.personoppslag.pdl.model.GeografiskTilknytning
import no.nav.eessi.pensjon.personoppslag.pdl.model.GtType
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstand
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Statsborgerskap
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import java.time.LocalDate
import java.time.LocalDateTime

object PersonPDLMock {
    internal fun createWith(landkoder: Boolean = true, fornavn: String = "Test", etternavn: String = "Testesen", fnr: String = "3123", aktoerid: String = "3213", erDod: Boolean? = false) :Person {
            val fdatoaar =  if (erDod != null && erDod == true) LocalDate.of(1921, 7, 12) else LocalDate.of(1988, 7, 12)
            val doeadfall = if (erDod != null && erDod == true) Doedsfall(LocalDate.of(2020, 10, 1), null) else null
            val kommuneLandkode = when(landkoder) {
                true -> "026123"
                else -> null
            }
            return Person(
                listOf(
                    IdentInformasjon(
                        fnr,
                        IdentGruppe.FOLKEREGISTERIDENT
                    ),
                    IdentInformasjon(
                        aktoerid,
                        IdentGruppe.AKTORID
                    )
                ),
                Navn(fornavn, null, etternavn),
                emptyList(),
                Bostedsadresse(
                    LocalDateTime.of(1980, 10, 1, 10, 10, 10),
                    LocalDateTime.of(2300, 10, 1, 10, 10, 10),
                    Vegadresse(
                        "Oppoverbakken",
                        "66",
                        null,
                        "1920"
                    ),
                    null
                ),
                null,
                listOf(Statsborgerskap(
                    "NOR",
                    LocalDate.of(1980, 10 , 1),
                    LocalDate.of(2300, 10, 1))
                ),
                Foedsel(
                    fdatoaar,
                    null,
                    null,
                    null
                ),
                GeografiskTilknytning(
                    GtType.KOMMUNE,
                    kommuneLandkode,
                    null,
                    "NOR"
                ),
                Kjoenn(
                    KjoennType.MANN,
                    null
                ),
                doeadfall,
                emptyList(),
                listOf(
                    Sivilstand(
                        Sivilstandstype.UGIFT,
                        LocalDate.of(2000, 10, 1),
                        null
                    )
                )
            )
    }
    internal fun Person.medFodsel(date: LocalDate, land: String? = "NORR")  = this.copy(foedsel = Foedsel(date,land,null, null))

    internal fun Person.medKjoenn(type: KjoennType) = this.copy(kjoenn = Kjoenn(type, null))

    internal fun Person.medBeskyttelse(gradering: AdressebeskyttelseGradering) = this.copy(adressebeskyttelse = listOf(gradering))

    internal fun Person.medForeldre(foreldre: Person): Person {
        val foreldreRolle = familieRolle(foreldre)
        val foreldrefnr = foreldre.identer.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
        val list = mutableListOf<Familierelasjon>()
        list.addAll(this.familierelasjoner)
        list.add(
            Familierelasjon(
            relatertPersonsIdent = foreldrefnr!!,
            relatertPersonsRolle = foreldreRolle,
            minRolleForPerson = Familierelasjonsrolle.BARN)
        )
        return this.copy(familierelasjoner = list)
    }
    //subsitutt for tps testfiler Person-20000.json... .enke med barn
    internal fun createEnkeWithBarn(enkefnr: String, barn1fnr: String, barn2fnr: String? = null): PersonDataCollection {
        val enkePerson = createWith(true, "JESSINE TORDNU", "BOUWMANS", enkefnr, "212")
            .medFodsel(LocalDate.of(1967, 5,3))
            .medKjoenn(KjoennType.KVINNE)

        val barn1 = createWith(true, "TOPPI DOTTO",  "BOUWMANS", barn1fnr)
            .medForeldre(enkePerson)
            .medFodsel(LocalDate.of(1990, 5, 3))
            .medKjoenn(KjoennType.KVINNE)

        val barn2 = barn2fnr?.let { bfnr ->
            createWith(true,"EGIDIJS MASKOT", "BOUWMANS", bfnr)
                .medForeldre(enkePerson)
                .medKjoenn(KjoennType.KVINNE)
                .medFodsel(LocalDate.of(1988,5,3))
        }

        println("disse barna  barn1: ${barn1.navn?.sammensattNavn}   barn2: ${barn2?.navn?.sammensattNavn}")

        return PersonDataCollection(
            forsikretPerson = enkePerson,
            ektefellePerson = null,
            sivilstandstype = Sivilstandstype.ENKE_ELLER_ENKEMANN,
            gjenlevendeEllerAvdod = enkePerson,
            barnPersonList = listOfNotNull(
                barn1,
                barn2
            ))
    }

    private fun familieRolle(person: Person) : Familierelasjonsrolle {
        return when(person.kjoenn?.kjoenn) {
            KjoennType.MANN -> Familierelasjonsrolle.FAR
            KjoennType.KVINNE -> Familierelasjonsrolle.MOR
            else -> Familierelasjonsrolle.MEDMOR
        }
    }

    internal fun Person.medAdresse(gate: String, postnummer: String, husnr: String? = "") = this.copy(bostedsadresse = Bostedsadresse(
        gyldigFraOgMed = LocalDateTime.of(2000, 10, 1, 10, 10, 10),
        gyldigTilOgMed = LocalDateTime.of(2300, 10, 1, 10 , 10, 10),
        vegadresse = Vegadresse(
            adressenavn = gate,
            husnummer = husnr,
            husbokstav = null,
            postnummer = postnummer
        ),
        utenlandskAdresse = null)
    )

    internal fun Person.medUtlandAdresse(gateOgnr: String, postnummer: String, landkode: String) = this.copy(bostedsadresse = Bostedsadresse(
        gyldigFraOgMed = LocalDateTime.of(2000, 10, 1, 10, 10, 10),
        gyldigTilOgMed = LocalDateTime.of(2300, 10, 1, 10 , 10, 10),
        vegadresse = null,
        utenlandskAdresse = UtenlandskAdresse(
            landkode = landkode,
            adressenavnNummer = gateOgnr,
            postkode = postnummer,
            bySted = "UTLANDBY",
            bygningEtasjeLeilighet = null,
            postboksNummerNavn = null,
            regionDistriktOmraade = null)
        )
    )

    //subsitutt for tps-11000-gift og ekte
    internal fun createEnkelFamilie(personfnr: String, ektefellefnr: String) : PersonDataCollection {
        val person = createWith(fornavn = "ODIN ETTØYE", etternavn = "BALDER", fnr = personfnr)
            .medAdresse("FORUSBEEN", "0010", "2294")
        val ekte = createWith(fornavn = "THOR-DOPAPIR", etternavn = "RAGNAROK", fnr = ektefellefnr)

        return PersonDataCollection(
            forsikretPerson = person,
            sivilstandstype = Sivilstandstype.GIFT,
            gjenlevendeEllerAvdod = person,
            ektefellePerson = ekte
        )
    }

    //subsitutt for tps-30000-gift og ekte
    internal fun createAvdodFamilie(gjenlevFnr: String, avdodEktefelleFnr: String) : PersonDataCollection {
        val person = createWith(fornavn = "BAMSE ULUR", etternavn = "DOLLY", fnr = gjenlevFnr)
            .medAdresse("FORUSBEEN", "0010", "2294")
            .medKjoenn(KjoennType.KVINNE)
        val avdod = createWith(fornavn = "BAMSE LUR", etternavn = "MOMBALO", fnr = avdodEktefelleFnr, erDod =  true)

        return PersonDataCollection(
            forsikretPerson = person,
            sivilstandstype = Sivilstandstype.ENKE_ELLER_ENKEMANN,
            gjenlevendeEllerAvdod = avdod
        )
    }


}