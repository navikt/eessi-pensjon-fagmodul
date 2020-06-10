package no.nav.eessi.pensjon.vedlegg.client

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

internal class EuxVedleggClientTest {

    @Test
    fun leggTilVedleggPaaDokument_happyDay() {

        val mockRestTemplate: RestTemplate = mockk()
        val client = EuxVedleggClient(mockRestTemplate)
        client.initMetrics()

        every {
            mockRestTemplate.exchange("/buc/someRinaSakId/sed/someRinaDokumentId/vedlegg?Filtype=someFiltype&synkron=true",
                    HttpMethod.POST,
                    any(),
                    String::class.java)
        }.returns(ResponseEntity.ok().build())

        client.leggTilVedleggPaaDokument("someAktorId", "someRinaSakId", "someRinaDokumentId", "someFilInnhold", "someFileName", "someFiltype" )

        //Then
        // Skal requesten vår se riktig ut

    }
}