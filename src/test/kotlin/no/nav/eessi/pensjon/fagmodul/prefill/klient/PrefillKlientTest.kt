package no.nav.eessi.pensjon.fagmodul.prefill.klient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefillKlientTest {

    @Test
    fun `Gitt at saksbehandler forsoker aa opprette alderspensjonskrav i en uforetrygdsak saa skal gis melding om at dette ikke er mulig`() {
       val error = """
           {"timestamp":"2022-01-25T11:52:07.979+00:00","status":400,"error":"Bad Request","message":"Du kan ikke opprette alderspensjonskrav i en uføretrygdsak (PESYS-saksnr: 22953438 har sakstype UFOREP)","path":"/sed/prefill"} 
       """.trimIndent()
        val data = PrefillKlient.ResponseErrorData.fromJson(error)
        assertEquals("Du kan ikke opprette alderspensjonskrav i en uføretrygdsak (PESYS-saksnr: 22953438 har sakstype UFOREP)", data.message)

    }

}