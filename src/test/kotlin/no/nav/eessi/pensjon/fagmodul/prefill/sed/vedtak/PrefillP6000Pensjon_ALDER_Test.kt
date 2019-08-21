package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjonMother.standardEessiInfo
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonServiceMother.fraFil
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.assertNotNull

@RunWith(MockitoJUnitRunner::class)
class PrefillP6000Pensjon_ALDER_Test {

    @Test
    fun `forventet korrekt utfylling av Pensjon objekt på Alderpensjon`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000-APUtland-301.xml"))

        val result = PrefillP6000Pensjon.createPensjon(
                dataFromPESYS = dataFromPESYS,
                gjenlevende = null,
                vedtakId = "12312312",
                andreinstitusjonerItem = standardEessiInfo().asAndreinstitusjonerItem())

        assertNotNull(result.vedtak)
        assertNotNull(result.sak)
        assertNotNull(result.tilleggsinformasjon)

        val vedtak = result.vedtak?.get(0)
        assertEquals("4.1.6  pensjon.vedtak[x].virkningsdato", "2017-05-01", vedtak?.virkningsdato)
        assertEquals("4.1.1 vedtak.type", "01", vedtak?.type)
        assertEquals("4.1.2 vedtak.basertPaa", "02", vedtak?.basertPaa)
        assertEquals("4.1.4 vedtak.resultat ", "01", vedtak?.resultat)
        assertEquals("4.1.8 vedtak.kjoeringsdato", "2017-05-21", vedtak?.kjoeringsdato)
        assertEquals("4.1.5 vedtak.artikkel (må fylles ut manuelt nå)", null, vedtak?.artikkel)

        assertEquals("4.1.10 vedtak?.grunnlag?.opptjening?.forsikredeAnnen", "01", vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
        assertEquals("4.1.10 vedtak?.grunnlag?.framtidigtrygdetid", "0", vedtak?.grunnlag?.framtidigtrygdetid)

        val beregning = vedtak?.beregning?.get(0)
        assertEquals("2017-05-01", beregning?.periode?.fom)
        assertEquals(null, beregning?.periode?.tom)
        assertEquals("NOK", beregning?.valuta)
        assertEquals("2017-05-01", beregning?.periode?.fom)
        assertEquals("03", beregning?.utbetalingshyppighet)

        assertEquals("11831", beregning?.beloepBrutto?.beloep)
        assertEquals("2719", beregning?.beloepBrutto?.ytelseskomponentGrunnpensjon)
        assertEquals("8996", beregning?.beloepBrutto?.ytelseskomponentTilleggspensjon)

        assertEquals("116", vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagBegrunnelse = vedtak?.avslagbegrunnelse?.get(0)
        assertEquals("4.1.13.1 vedtak?.avslagbegrunnelse?", null, avslagBegrunnelse?.begrunnelse)

        assertEquals("six weeks from the date the decision is received", result.sak?.kravtype?.get(0)?.datoFrist)

        assertEquals("2017-05-21", result.tilleggsinformasjon?.dato)

        assertEquals("NO:noinst002", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsid)
        assertEquals("NOINST002, NO INST002, NO", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsnavn)
        assertEquals("Postboks 6600 Etterstad TEST", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsadresse)
        assertEquals("0607", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.postnummer)
    }

    @Test(expected = IllegalStateException::class)
    fun `preutfylling P6000 feiler ved mangler av vedtakId`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000-APUtland-301.xml"))

        PrefillP6000Pensjon.createPensjon(dataFromPESYS, null,"", null)
    }

    @Test(expected = java.lang.IllegalStateException::class)
    fun `feiler ved boddArbeidetUtland ikke sann`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000-AP-101.xml"))

        PrefillP6000Pensjon.createPensjon(dataFromPESYS, null,"12312312", null)
    }
}
