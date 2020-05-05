package no.nav.eessi.pensjon.services.personv3

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.security.sts.configureRequestSamlToken
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.feil.PersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.feil.Sikkerhetsbegrensning
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.context.ActiveProfiles
import javax.naming.ServiceUnavailableException

@ExtendWith(MockitoExtension::class)
class PersonV3ServiceTest {

    @MockK
    private lateinit var personV3Mock: PersonV3

    @Spy
    private lateinit var auditLogger: AuditLogger

    lateinit var personV3Service: PersonV3Service


    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic("no.nav.eessi.pensjon.security.sts.STSClientConfigKt")
        every { configureRequestSamlToken(personV3Mock) } returns Unit
        personV3Service = PersonV3Service(personV3Mock, auditLogger)
        personV3Service.initMetrics()
    }

    @Test
    fun hentPersonPing() {
        every {personV3Mock.ping() } returns Unit
        assertTrue(personV3Service.hentPersonPing())
    }

    @Test
    fun hentPersonPingException() {
        every {personV3Service.hentPersonPing() } throws ServiceUnavailableException("Får ikke kontakt med tjeneste PersonV3")
        assertThrows<Exception> { personV3Service.hentPersonPing() }
    }

    @Test
    fun hentPersonIkkeFunnet() {
        val fnr = "18128126178"
        every { personV3Mock.hentPerson(any()) } throws HentPersonPersonIkkeFunnet("Person ikke funnet", PersonIkkeFunnet())
        assertThrows<PersonV3IkkeFunnetException> {  personV3Service.hentPerson(fnr) }
    }

    @Test
    fun hentPersonSikkerhetsbegrensning() {
        val fnr = "18128126178"
        every { personV3Mock.hentPerson(any()) } throws HentPersonSikkerhetsbegrensning("Sikkerhetsbegrensning", Sikkerhetsbegrensning())
        assertThrows<PersonV3SikkerhetsbegrensningException> { personV3Service.hentPerson(fnr) }
    }
}
