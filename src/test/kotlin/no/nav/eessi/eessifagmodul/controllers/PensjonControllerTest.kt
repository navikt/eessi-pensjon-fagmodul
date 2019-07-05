package no.nav.eessi.eessifagmodul.controllers

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.person.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.IkkeFunnetException
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.Pensjontype
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

@RunWith(MockitoJUnitRunner::class)
class PensjonControllerTest {

    private val pensjonsinformasjonService: PensjonsinformasjonService = mock()

    private val aktoerregisterService: AktoerregisterService = mock()

    private val controller = PensjonController(pensjonsinformasjonService, aktoerregisterService)

    private val sakId = "Some sakId"

    @Test
    fun `hentPensjonSakType | gitt en aktoerId saa slaa opp fnr og hent deretter sakstype`() {
        val aktoerId = "1234567890123" // 13 sifre
        val fnrForAktoerID = "23037328392" // 11 sifre
        val sakId = "Some sakId"

        `when`(aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktoerId)).thenReturn(fnrForAktoerID)

        whenever(pensjonsinformasjonService.hentKunSakType(sakId, fnrForAktoerID)).thenReturn(Pensjontype(sakId, "Type"))

        controller.hentPensjonSakType(sakId, aktoerId)

        verify(pensjonsinformasjonService).hentKunSakType(sakId, fnrForAktoerID)
    }

    @Test
    fun `hentPensjonSakType | gitt et FNR I aktoerId-feltet saa hent sakstype direkte`() {
        val aktoerIdSomFaktiskErEtFnr = "23037328392" // 11 sifre! - Dette skyldes en feil hos klienten som kaller oss

        whenever(pensjonsinformasjonService.hentKunSakType(sakId, aktoerIdSomFaktiskErEtFnr)).thenReturn(Pensjontype(sakId, "Type"))

        controller.hentPensjonSakType(sakId, aktoerIdSomFaktiskErEtFnr)

        verify(pensjonsinformasjonService).hentKunSakType(sakId, aktoerIdSomFaktiskErEtFnr)
        verify(aktoerregisterService, never()).hentGjeldendeNorskIdentForAktorId(any())
    }

    @Test
    fun `hentPensjonSakType | gitt at det svar fra PESYS er tom`() {
        val aktoerId = "1234567890123" // 13 sifre
        val fnrForAktoerID = "23037328392" // 11 sifre
        val sakId = "Some sakId"

        `when`(aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktoerId)).thenReturn(fnrForAktoerID)

        whenever(pensjonsinformasjonService.hentKunSakType(sakId, fnrForAktoerID)).thenThrow(IkkeFunnetException("Saktype ikke funnet"))
        val response = controller.hentPensjonSakType(sakId, aktoerId)

        verify(pensjonsinformasjonService).hentKunSakType(sakId, fnrForAktoerID)
        assertEquals(HttpStatus.NOT_FOUND, response?.statusCode)

    }

    @Test
    fun `hentPensjonSakType | gitt at det svar feiler fra PESYS`() {
        val aktoerId = "1234567890123" // 13 sifre
        val fnrForAktoerID = "23037328392" // 11 sifre
        val sakId = "Some sakId"

        `when`(aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktoerId)).thenReturn(fnrForAktoerID)
        whenever(pensjonsinformasjonService.hentKunSakType(sakId, fnrForAktoerID)).thenThrow(PensjoninformasjonException("Ingen svar med PESYS"))
        val response = controller.hentPensjonSakType(sakId, aktoerId)

        verify(pensjonsinformasjonService).hentKunSakType(sakId, fnrForAktoerID)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response?.statusCode)

    }


}