package no.nav.eessi.eessifagmodul.controllers

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjonsinformasjonService
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never


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

        controller.hentPensjonSakType(sakId, aktoerId)

        verify(pensjonsinformasjonService).hentKunSakType(sakId, fnrForAktoerID)
    }

    @Test
    fun `hentPensjonSakType | gitt et FNR I aktoerId-feltet saa hent sakstype direkte`() {
        val aktoerIdSomFaktiskErEtFnr = "23037328392" // 11 sifre! - Dette skyldes en feil hos klienten som kaller oss

        controller.hentPensjonSakType(sakId, aktoerIdSomFaktiskErEtFnr)

        verify(pensjonsinformasjonService).hentKunSakType(sakId, aktoerIdSomFaktiskErEtFnr)

        verify(aktoerregisterService, never()).hentGjeldendeNorskIdentForAktorId(any())
    }
}