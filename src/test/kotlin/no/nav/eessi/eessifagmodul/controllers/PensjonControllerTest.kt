package no.nav.eessi.eessifagmodul.controllers

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjonsinformasjonService
import org.junit.Test
import org.mockito.Mockito.`when`


class PensjonControllerTest {

    @Test
    fun `hentPensjonSakType | gitt En AktoerId I AktoerId Feltet Saa Oversett Til Fnr Og Kall Fnr Metoden`() {

        val aktoerId = "1234567890123"
        val fnrForAktoerID = "Fnr"
        val sakId = "Some sakId"

        val pensjonsinformasjonService: PensjonsinformasjonService = mock()
        val aktoerregisterService: AktoerregisterService = mock()
        val controller = PensjonController(pensjonsinformasjonService, aktoerregisterService)

        `when`(aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktoerId)).thenReturn(fnrForAktoerID)

        controller.hentPensjonSakType(sakId, aktoerId)

        verify(pensjonsinformasjonService, times(1)).hentKunSakType(sakId, fnrForAktoerID)
    }

    @Test
    fun `hentPensjonSakType | gitt ett FNR i AktoerId-feltet Saa Kall Fnr Metoden`() {

        val aktoerIdSomFaktiskErEtFnr = "23037328392"
        val sakId = "Some sakId"

        val pensjonsinformasjonService: PensjonsinformasjonService = mock()
        val aktoerregisterService: AktoerregisterService = mock()
        val controller = PensjonController(pensjonsinformasjonService, aktoerregisterService)

        controller.hentPensjonSakType(sakId, aktoerIdSomFaktiskErEtFnr)

        verify(aktoerregisterService, times(0)).hentGjeldendeNorskIdentForAktorId(any())
        verify(pensjonsinformasjonService, times(1)).hentKunSakType(sakId, aktoerIdSomFaktiskErEtFnr)
    }
}