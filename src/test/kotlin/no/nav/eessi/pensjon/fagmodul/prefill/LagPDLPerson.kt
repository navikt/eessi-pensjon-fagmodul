package no.nav.eessi.pensjon.fagmodul.prefill

import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierlasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedsel
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstand
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Statsborgerskap
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import java.time.LocalDate
import java.time.LocalDateTime


class LagPDLPerson {
    companion object {
        fun lagPerson(norskIdent: String = FodselsnummerMother.generateRandomFnr(60), fornavn: String = "OLE", etternavn: String = "OLSEN", land: String = "NOR", kjoennType: KjoennType = KjoennType.MANN): Person {
            val personfnr = NavFodselsnummer(norskIdent)
            return Person(
                identer = listOf(IdentInformasjon(norskIdent, IdentGruppe.FOLKEREGISTERIDENT)),
                navn = Navn(fornavn, null, etternavn),
                adressebeskyttelse = emptyList(),
                bostedsadresse = null,
                oppholdsadresse = null,
                statsborgerskap = listOf(Statsborgerskap(land, LocalDate.of(2000, 10, 1), LocalDate.of(2300, 10, 1))),
                foedsel = Foedsel(personfnr.getBirthDate(), null, null, null),
                geografiskTilknytning = null,
                kjoenn = Kjoenn(kjoennType, null),
                doedsfall = null,
                familierelasjoner = mutableListOf(),
                sivilstand = emptyList()
            )
        }

        fun Person.medBarn(barnfnr: String): Person {
                val minRolle = when(this.kjoenn?.kjoenn) {
                    KjoennType.MANN -> Familierelasjonsrolle.FAR
                    KjoennType.KVINNE -> Familierelasjonsrolle.MOR
                    else -> Familierelasjonsrolle.MEDMOR
                }
                val list = mutableListOf<Familierlasjon>()
                list.addAll(this.familierelasjoner)
                list.add(Familierlasjon(
                    relatertPersonsIdent = barnfnr,
                    relatertPersonsRolle = Familierelasjonsrolle.BARN,
                    minRolleForPerson = minRolle)
                )
                return this.copy(familierelasjoner = list)
        }

        fun Person.medForeldre(foreldre: Person): Person {
            val foreldreRolle = when(foreldre.kjoenn?.kjoenn) {
                KjoennType.MANN -> Familierelasjonsrolle.FAR
                KjoennType.KVINNE -> Familierelasjonsrolle.MOR
                else -> Familierelasjonsrolle.MEDMOR
            }
            val foreldrefnr = foreldre.identer.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
            val list = mutableListOf<Familierlasjon>()
            list.addAll(this.familierelasjoner)
            list.add(Familierlasjon(
                relatertPersonsIdent = foreldrefnr!!,
                relatertPersonsRolle = foreldreRolle,
                minRolleForPerson = Familierelasjonsrolle.BARN)
            )
            return this.copy(familierelasjoner = list)
        }

        fun Person.medAdresse(gate: String?) = this.copy(bostedsadresse = Bostedsadresse(
                gyldigFraOgMed = LocalDateTime.of(2000, 10, 1, 10, 10, 10),
                gyldigTilOgMed = LocalDateTime.of(2300, 10, 1, 10 , 10, 10),
                vegadresse = Vegadresse(
                    adressenavn = gate,
                    husnummer = "12",
                    husbokstav = null,
                    postnummer = "0101"
                ),
                utenlandskAdresse = null)
        )

        fun createPersonMedEktefellePartner(personPersonnr: String, ektefellePersonnr: String, type: Sivilstandstype): Pair<Person, Person> {

            val person = lagPerson(personPersonnr, "Ola", "Testbruker")
            val ektefelle = lagPerson(ektefellePersonnr, "Jonna", "Dolla", kjoennType = KjoennType.KVINNE)

            val nyPerson = person.copy(sivilstand = listOf(Sivilstand(type, LocalDate.of(2000,10, 1), ektefellePersonnr)))
            val nyEktefell = ektefelle.copy(sivilstand = listOf(Sivilstand(type, LocalDate.of(2000,10, 1), personPersonnr)))

            return Pair(nyPerson, nyEktefell)
        }
    }
}