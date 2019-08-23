package no.nav.eessi.pensjon.services.personv3

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import no.nav.eessi.pensjon.security.sts.configureRequestSamlToken
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.feil.PersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.feil.Sikkerhetsbegrensning
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.naming.ServiceUnavailableException

class PersonV3ServiceTest {

    @MockK
    private lateinit var personV3Mock: PersonV3

    @InjectMockKs
    lateinit var personV3Service: PersonV3Service

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic("no.nav.eessi.pensjon.security.sts.STSClientConfigKt")
        every { configureRequestSamlToken(personV3Mock) } returns Unit
    }

    @Test
    fun hentPersonPing() {
        every {personV3Mock.ping() } returns Unit
        assertTrue(personV3Service.hentPersonPing())
    }

    @Test(expected = Exception::class)
    fun hentPersonPingException() {
        every {personV3Service.hentPersonPing() } throws ServiceUnavailableException("Får ikke kontakt med tjeneste PersonV3")
        personV3Service.hentPersonPing()
    }

    @Test(expected = PersonV3IkkeFunnetException::class)
    fun hentPersonIkkeFunnet() {
        val fnr = "18128126178"
        every { personV3Mock.hentPerson(any()) } throws HentPersonPersonIkkeFunnet("Person ikke funnet", PersonIkkeFunnet())
        personV3Service.hentPerson(fnr)
    }

    @Test(expected = PersonV3SikkerhetsbegrensningException::class)
    fun hentPersonSikkerhetsbegrensning() {
        val fnr = "18128126178"
        every { personV3Mock.hentPerson(any()) } throws HentPersonSikkerhetsbegrensning("Sikkerhetsbegrensning", Sikkerhetsbegrensning())
        personV3Service.hentPerson(fnr)
    }
}