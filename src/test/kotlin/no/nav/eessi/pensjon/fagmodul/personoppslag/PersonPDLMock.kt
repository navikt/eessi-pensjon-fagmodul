package no.nav.eessi.pensjon.fagmodul.personoppslag

import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall
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
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import java.time.LocalDate
import java.time.LocalDateTime


object PersonPDLMock {
    internal fun createWith(landkoder: Boolean = true, fornavn: String = "Test", etternavn: String = "Testesen", fnr: String = "3123", aktoerid: String = "3213", erDod: Boolean? = false) :Person? {
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
}