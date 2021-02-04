package no.nav.eessi.pensjon.fagmodul.prefill.pdl

import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock.medBeskyttelse
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
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
                    "0123"
                ),
                utenlandskAdresse = null
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
}
