package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.helper

import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonVedtak
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClientMother.fraFil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefillPensjonVedtakTest {

    @Test
    fun `forventet createVedtakTypePensionWithRule verdi ALDER`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000-APUtland-301.xml"))

        val pendata = dataFromPESYS.hentMedVedtak("someVedtakId")

        assertEquals("01", PrefillPensjonVedtak.createVedtakTypePensionWithRule(pendata))
    }

    @Test
    fun `forventet createVedtakTypePensionWithRule verdi GJENLEVENDE`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000-GP-401.xml"))
        val pendata = dataFromPESYS.hentMedVedtak("someVedtakId")

        assertEquals("03", PrefillPensjonVedtak.createVedtakTypePensionWithRule(pendata))
    }

    @Test
    fun `forventet createVedtakTypePensionWithRule verdi UFØRE`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000-UT-201.xml"))
        val pendata = dataFromPESYS.hentMedVedtak("someVedtakId")

        assertEquals("02", PrefillPensjonVedtak.createVedtakTypePensionWithRule(pendata))
    }

}
