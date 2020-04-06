package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjonMother.standardEessiInfo
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClientMother.fraFil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class `P6000alderpensjon-avslagTest` {

    @Test
    fun `forventet korrekt utfylling av pensjon objekt på alderpensjon med avslag`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000vedtak-alderpensjon-avslag.xml"))

        val result = PrefillP6000Pensjon.createPensjon(
                dataFromPESYS = dataFromPESYS,
                gjenlevende = null,
                vedtakId = "12312312",
                andreinstitusjonerItem = standardEessiInfo().asAndreinstitusjonerItem())

        assertNotNull(result.vedtak)
        assertNotNull(result.sak)
        assertNotNull(result.tilleggsinformasjon)

        val vedtak = result.vedtak?.get(0)
        assertEquals("2019-06-01", vedtak?.virkningsdato, "vedtak.virkningsdato")
        assertEquals("01", vedtak?.type)
        assertEquals("99", vedtak?.basertPaa)
        assertEquals("02", vedtak?.resultat, "4.1.4 vedtak.resultat")
        assertEquals(null, vedtak?.kjoeringsdato)
        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")

        assertEquals(null, vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
        assertEquals(null, vedtak?.grunnlag?.framtidigtrygdetid)

        assertEquals(null, vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagBegrunnelse = vedtak?.avslagbegrunnelse?.get(0)
        assertEquals("03", avslagBegrunnelse?.begrunnelse, "4.1.13.1          AvlsagsBegrunnelse")

        assertEquals("six weeks from the date the decision is received", result.sak?.kravtype?.get(0)?.datoFrist)

        assertEquals("2019-11-11", result.tilleggsinformasjon?.dato)

        assertEquals("NO:noinst002", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsid)
        assertEquals("Postboks 6600 Etterstad TEST", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsadresse)
        assertEquals("0607", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.postnummer)

    }

    @Test
    fun `preutfylling P6000 feiler ved mangler av vedtakId`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000vedtak-alderpensjon-avslag.xml"))

        assertThrows<IllegalStateException> {
            PrefillP6000Pensjon.createPensjon(dataFromPESYS, null, "", null)
        }
    }
}
