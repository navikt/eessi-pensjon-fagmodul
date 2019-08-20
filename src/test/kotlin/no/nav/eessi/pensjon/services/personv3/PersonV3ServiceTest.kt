package no.nav.eessi.pensjon.services.personv3

import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.whenever
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.eessi.pensjon.metrics.TimingService
import no.nav.eessi.pensjon.security.sts.STSClientConfig
import no.nav.eessi.pensjon.security.sts.configureRequestSamlToken
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

//@RunWith(MockitoJUnitRunner::class)
class PersonV3ServiceTest {

    @MockK
    private lateinit var personV3Mock: PersonV3

    @MockK
    lateinit var timingServiceMock: TimingService

    @InjectMockKs
    lateinit var personV3Service: PersonV3Service

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        personV3Service = PersonV3Service(personV3Mock, timingServiceMock)
        mockkStatic(STSClientConfig::class)
        every {  configureRequestSamlToken(personV3Mock) } returns mockk()
    }

    @Test
    fun hentPersonPing() {
        assertTrue(personV3Service.hentPersonPing())
    }

    @Test
    fun hentPerson() {
        val fnr = "18128126178"
       //whenever(configureRequestSamlToken(personV3Mock)).
        whenever(personV3Service.hentPerson(fnr)).thenThrow(PersonV3IkkeFunnetException("EXPECTED"))

        val response = personV3Service.hentPerson(fnr)
    }

    @After
    fun tearDown() {
    }
}