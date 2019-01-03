package no.nav.eessi.eessifagmodul.prefill.vedtak

import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(MockitoJUnitRunner::class)
class PrefillP6000PensionGjenlevTest : AbstractMockVedtakPensionHelper("P6000-GP-401.xml") {

    @Test
    fun `forventet korrekt utfylling av Pensjon objekt på Gjenlevendepensjon`() {
        prefill = generatePrefillData(66, "vedtak")

//        val dataFromPESYS1 = mockPrefillP6000PensionDataFromPESYS("P6000-GP-401.xml")
//        val result = dataFromPESYS1.prefill(prefill)

        val result = dataFromPESYS.prefill(prefill)

        //ekstra for å sjekke om Gjenlevepensjon finnes.
        val pendata = dataFromPESYS.getPensjoninformasjonFraVedtak(prefill)
        assertEquals("GJENLEV", pendata.sakAlder.sakType)
        assertEquals("12345678901", pendata.person.pid)

        //debugPrintFinalResult(result)

        val vedtaklst = result.vedtak
        val sak = result.sak
        val tillegg = result.tilleggsinformasjon
        assertNotNull(vedtaklst)
        assertNotNull(sak)
        assertNotNull(tillegg)

        val vedtak = vedtaklst?.get(0)
        assertEquals("2018-05-01" , vedtak?.virkningsdato, "vedtak.virkningsdato")
        assertEquals("03", vedtak?.type, "vedtak.type")
        assertEquals("02", vedtak?.basertPaa, "vedtak.basertPaa")
        assertEquals("03", vedtak?.resultat, "vedtak.resultat")
        assertEquals("2018-05-26", vedtak?.kjoeringsdato)
        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")

        assertEquals("03", vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
        assertEquals("1", vedtak?.grunnlag?.framtidigtrygdetid)

        val bergen = vedtak?.beregning?.get(0)
        assertEquals("2018-05-01", bergen?.periode?.fom)
        assertEquals(null, bergen?.periode?.tom)
        assertEquals("NOK", bergen?.valuta)
        assertEquals("03", bergen?.utbetalingshyppighet)

        assertEquals("5248", bergen?.beloepBrutto?.beloep)
        assertEquals("3519", bergen?.beloepBrutto?.ytelseskomponentGrunnpensjon)
        assertEquals("1729", bergen?.beloepBrutto?.ytelseskomponentTilleggspensjon)

        assertEquals(null, vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagbrg = vedtak?.avslagbegrunnelse?.get(0)
        assertEquals(null, avslagbrg?.begrunnelse)

        val dataof = sak?.kravtype?.get(0)?.datoFrist
        assertEquals("six weeks from the date the decision is received", dataof)

        assertEquals("2018-05-26", tillegg?.dato)
        //assertEquals("NAV", tillegg?.andreinstitusjoner?.get(0)?.institusjonsid)

    }

    @Test
    fun `forventer "01" på AvlsagsBegrunnelse Gjenlevendepensjon, TrygdleListeTom`() {

        val pendata1 = generateFakePensjoninformasjonForGJENLEV()
        pendata1.vedtak.isBoddArbeidetUtland = true
        pendata1.trygdetidListe.trygdetidListe.clear()
        pendata1.vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSLAG"

        val result1 = dataFromPESYS.pensjonVedtak.createAvlsagsBegrunnelse(pendata1)
        assertEquals("01", result1)
    }


    @Test
    fun `forventet createVedtakTypePensionWithRule verdi`() {
        prefill = generatePrefillData(68, "P6000")
        //dataFromPESYS1.getPensjoninformasjonFraVedtak("23123123")
        val result = dataFromPESYS.pensjonVedtak.createVedtakTypePensionWithRule(pendata)
        assertEquals("03", result)
    }

    @Test
    fun `forventet korrekt utfylt P6000 gjenlevende ikke bosat utland (avdød bodd i utland)`() {
        prefill = generatePrefillData(66, "P6000")

        val dataFromPESYS1 = mockPrefillPensionDataFromPESYS("P6000-GP-IkkeUtland.xml")

        val result = dataFromPESYS1.prefill(prefill)

        val vedtaklst = result.vedtak
        val sak = result.sak
        val tillegg = result.tilleggsinformasjon
        assertNotNull(vedtaklst)
        assertNotNull(sak)
        assertNotNull(tillegg)

        val vedtak = vedtaklst?.get(0)
        assertEquals("2018-05-01", vedtak?.virkningsdato, "vedtak.virkningsdato")
        assertEquals("03", vedtak?.type, "vedtak.type")
        assertEquals("02", vedtak?.basertPaa, "vedtak.basertPaa")
        assertEquals("03", vedtak?.resultat, "vedtak.resultat")
        assertEquals("2018-05-26", vedtak?.kjoeringsdato)
        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")

        assertEquals("03", vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
        assertEquals("1", vedtak?.grunnlag?.framtidigtrygdetid)

        val bergen = vedtak?.beregning?.get(0)
        assertEquals("2018-05-01", bergen?.periode?.fom)
        assertEquals(null, bergen?.periode?.tom)
        assertEquals("NOK", bergen?.valuta)
        assertEquals("03", bergen?.utbetalingshyppighet)

        assertEquals("6766", bergen?.beloepBrutto?.beloep)
        assertEquals("4319", bergen?.beloepBrutto?.ytelseskomponentGrunnpensjon)
        assertEquals("2447", bergen?.beloepBrutto?.ytelseskomponentTilleggspensjon)

        assertEquals(null, vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagbrg = vedtak?.avslagbegrunnelse?.get(0)
        assertEquals(null, avslagbrg?.begrunnelse)

        val dataof = sak?.kravtype?.get(0)?.datoFrist
        assertEquals("six weeks from the date the decision is received", dataof)

        assertEquals("2018-05-26", tillegg?.dato)
        assertEquals("NO", tillegg?.andreinstitusjoner?.get(0)?.institusjonsid)
        assertEquals("[NO] NAV NORGE", tillegg?.andreinstitusjoner?.get(0)?.institusjonsnavn)
        assertEquals("Postboks 6600 Etterstad", tillegg?.andreinstitusjoner?.get(0)?.institusjonsadresse)
        assertEquals("0607", tillegg?.andreinstitusjoner?.get(0)?.postnummer)


    }

}