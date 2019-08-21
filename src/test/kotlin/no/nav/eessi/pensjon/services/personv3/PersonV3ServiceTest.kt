package no.nav.eessi.pensjon.services.personv3

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import no.nav.eessi.pensjon.security.sts.configureRequestSamlToken
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.feil.PersonIkkeFunnet
import org.junit.After
import org.junit.Before
import org.junit.Test

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

    @Test(expected = PersonV3IkkeFunnetException::class)
    fun hentPerson() {
        val fnr = "18128126178"
        every { personV3Mock.hentPerson(any()) } throws HentPersonPersonIkkeFunnet("EXPECTED", PersonIkkeFunnet())
        val response = personV3Service.hentPerson(fnr)
    }

    @Test
    fun hentPersonPing() {
        personV3Service.hentPersonPing()
    }

    @After
    fun tearDown() {
    }
}