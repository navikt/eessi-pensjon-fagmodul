package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.tps.BrukerFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.services.pensjonsinformasjon.RequestBuilder
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.lenient
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate

object PrefillTestHelper {

    fun lesPensjonsdataFraFil(responseXMLfilename: String): PensjonsinformasjonHjelper {
        val pensjonsinformasjonRestTemplate = mock<RestTemplate>()
        lenient().`when`(pensjonsinformasjonRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(readXMLresponse(responseXMLfilename))

        val pensjonsinformasjonService = PensjonsinformasjonService(pensjonsinformasjonRestTemplate, RequestBuilder())

        return PensjonsinformasjonHjelper(pensjonsinformasjonService)
    }

    fun setupPersondataFraTPS(mockPersonDataFraTPS: Set<PersonDataFromTPS.MockTPS>): BrukerFromTPS {
        val datatps = PersonDataFromTPS(mockPersonDataFraTPS)
        return datatps.mockBrukerFromTPS()
    }

    fun readJsonResponse(file: String): String {
        return ResourceUtils.getFile("classpath:json/nav/$file").readText()
    }

    private fun readXMLresponse(file: String): ResponseEntity<String> {
        val resource = ResourceUtils.getFile("classpath:pensjonsinformasjon/krav/$file").readText()
        return ResponseEntity(resource, HttpStatus.OK)
    }

    fun createMockApiRequest(sedName: String, buc: String, payload: String, sakNr: String): ApiRequest {
        val items = listOf(InstitusjonItem(country = "NO", institution = "NAVT003"))
        return ApiRequest(
                institutions = items,
                sed = sedName,
                sakId = sakNr,
                euxCaseId = null,
                aktoerId = "1000060964183",
                buc = buc,
                subjectArea = "Pensjon",
                payload = payload
        )
    }

}
