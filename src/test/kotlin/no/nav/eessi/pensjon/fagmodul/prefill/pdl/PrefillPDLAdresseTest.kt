package no.nav.eessi.pensjon.fagmodul.prefill.pdl

import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock.medBeskyttelse
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktinformasjonForDoedsbo
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktinformasjonForDoedsboAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktinformasjonForDoedsboSkifteform
import no.nav.eessi.pensjon.personoppslag.pdl.model.PostadresseIFrittFormat
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import no.nav.eessi.pensjon.services.geo.PostnummerService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PrefillPDLAdresseTest{

    lateinit var prefillAdresse: PrefillPDLAdresse

    @Mock
    lateinit var kodeverkClient: KodeverkClient

    @BeforeEach
    fun beforeStart() {
        prefillAdresse = PrefillPDLAdresse(PostnummerService(), kodeverkClient)
    }

    @Test
    fun `create personAdresse`() {
        val person = PersonPDLMock.createWith()
            .copy(bostedsadresse = Bostedsadresse(
                LocalDateTime.of(2000, 9, 2, 4,3),
                LocalDateTime.of(2300, 9, 2, 4,3),
                Vegadresse(
                    "Kirkeveien",
                    "12",
                    null,
                    "0123",
                    null,
                    null
                ),
                utenlandskAdresse = null,
                metadata = no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata(emptyList(), false, "DOLLY", "Doll")
            ))

       val result = prefillAdresse.createPersonAdresse(person)!!

        assertNotNull(result)
        assertEquals("NO", result.land)
        assertEquals("Kirkeveien 12", result.gate)
        assertEquals("OSLO", result.by)
    }

    @Test
    fun adresseFeltDiskresjonFortroligPerson() {
        val person = PersonPDLMock.createWith()
            .medBeskyttelse(AdressebeskyttelseGradering.FORTROLIG)

        val acual = prefillAdresse.createPersonAdresse(person)

        assertEquals(null, acual)
    }

    @Test
    fun adresseFeltDiskresjonStrengtFortoligPerson() {
        val person = PersonPDLMock.createWith()
            .medBeskyttelse(AdressebeskyttelseGradering.STRENGT_FORTROLIG)

        val acual = prefillAdresse.createPersonAdresse(person)

        assertEquals(null, acual)
    }


    @Test
    fun `utfylling av doedsboAdresseUtenlinjeskift`() {

        val doedMeta = PersonPDLMock.mockMeta()

        val person = PersonPDLMock.createWith(
            landkoder = true,
            fnr = "123123123",
            aktoerid = "2312312",
            erDod = true
        ).copy(
            bostedsadresse = null,
            oppholdsadresse = null,
            kontaktinformasjonForDoedsbo = KontaktinformasjonForDoedsbo(
            adresse = KontaktinformasjonForDoedsboAdresse(
                adresselinje1 = "testlinej1 123\n23123 osloby",
                landkode = "NOR",
                postnummer = "1231",
                poststedsnavn = "osloby"
            ),
            LocalDate.of(2020, 10, 1),
            Folkeregistermetadata(null),
            skifteform = KontaktinformasjonForDoedsboSkifteform.ANNET,
            metadata = doedMeta,
        ))


        val actual = prefillAdresse.createPersonAdresse(person)

        assertNotNull(actual)
        assertEquals("testlinej1 123 23123 osloby", actual?.gate)
        assertEquals(null, actual?.bygning)
        assertEquals("osloby", actual?.by)
        assertEquals("1231", actual?.postnummer)

    }

    @Test
    fun `create Adresse med BostedAdresse og adresseIFrittFormat`() {
        val person = PersonPDLMock.createWith()
            .copy(bostedsadresse = Bostedsadresse(
                LocalDateTime.of(2000, 9, 2, 4,3),
                LocalDateTime.of(2300, 9, 2, 4,3),
                Vegadresse(
                    "Kirkeveien",
                    "12",
                    null,
                    "0123",
                    null,
                    null
                ),
                utenlandskAdresse = null,
                metadata = no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata(emptyList(), false, "DOLLY", "Doll")
            ), kontaktadresse = Kontaktadresse(
                    coAdressenavn = null,
                    type = KontaktadresseType.Innland,
                    postadresseIFrittFormat = PostadresseIFrittFormat(
                        adresselinje1 = "Kirkeveien",
                    ),
                    metadata = no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata(emptyList(), false, "DOLLY", "Doll")
                )
            )

        val result = prefillAdresse.createPersonAdresse(person)!!

        assertNotNull(result)
        assertEquals("NO", result.land)
        assertEquals("Kirkeveien 12", result.gate)
        assertEquals("0123", result.postnummer)
        assertEquals("OSLO", result.by)
    }
}
