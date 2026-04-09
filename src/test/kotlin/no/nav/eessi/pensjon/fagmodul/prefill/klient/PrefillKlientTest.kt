package no.nav.eessi.pensjon.fagmodul.prefill.klient

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException

class PrefillKlientTest {

    private var metricsHelper = MetricsHelper.ForTest()
    private var restTemplate = mockk<RestTemplate>()

    private lateinit var request: ApiRequest
    private lateinit var klient: PrefillKlient

    @BeforeEach
    fun setup() {
        klient = PrefillKlient(restTemplate, metricsHelper)
        request = ApiRequest()
    }

    @Test
    fun `Gitt at saksbehandler forsoker aa opprette alderspensjonskrav i en uforetrygdsak saa skal gis melding om at dette ikke er mulig`() {
       val error = """
           {"timestamp":"2022-01-25T11:52:07.979+00:00","status":400,"error":"Bad Request","message":"Du kan ikke opprette alderspensjonskrav i en uføretrygdsak (PESYS-saksnr: 22953438 har sakstype UFOREP)","path":"/sed/prefill"} 
       """.trimIndent()
        val data = PrefillKlient.ResponseErrorData.fromJson(error)
        assertEquals("Du kan ikke opprette alderspensjonskrav i en uføretrygdsak (PESYS-saksnr: 22953438 har sakstype UFOREP)", data.message)

    }

    @Test
    fun `hentPreutfyltSed throws ResponseStatusException with correct message and status when bodd eller arbeidet i utlandet is not marked`() {

        val exception = ResponseStatusException(HttpStatus.BAD_REQUEST, "Det er ikke markert for bodd/arbeidet i utlandet. Krav SED P2000 blir ikke opprettet")
        every { restTemplate.exchange(eq("/sed/prefill"), HttpMethod.POST, any(), eq(String::class.java)) } throws exception

        val thrown = assertThrows(ResponseStatusException::class.java) {
            klient.hentPreutfyltSed(request)
        }
        assertEquals(HttpStatus.BAD_REQUEST, thrown.statusCode)
        assertEquals("Det er ikke markert for bodd/arbeidet i utlandet. Krav SED P2000 blir ikke opprettet", thrown.reason)
    }
}
