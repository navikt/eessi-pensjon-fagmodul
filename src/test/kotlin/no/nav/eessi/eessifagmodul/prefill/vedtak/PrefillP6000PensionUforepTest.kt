package no.nav.eessi.eessifagmodul.prefill.vedtak

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.simpleFormat
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PrefillP6000PensionUforepTest : AbstractMockVedtakPensionHelper("P6000-UT-201.xml") {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000PensionUforepTest::class.java) }

    @Test
    fun `forventet korrekt utfylling av Pensjon objekt på Uførepensjon`() {
        prefill = generatePrefillData(66, "P6000")

        val result = dataFromPESYS.prefill(prefill)

        //debugPrintFinalResult(result)

        val vedtaklst = result.vedtak
        val sak = result.sak
        val tillegg = result.tilleggsinformasjon
        assertNotNull(vedtaklst)
        assertNotNull(sak)
        assertNotNull(tillegg)

        val vedtak = vedtaklst?.get(0)
        assertEquals("2017-04-11" , vedtak?.virkningsdato, "vedtak.virkningsdato")
        assertEquals("02", vedtak?.type)
        assertEquals("02", vedtak?.basertPaa)
        assertEquals("03", vedtak?.resultat, "vedtak.resultat")
        assertEquals("2017-05-21", vedtak?.kjoeringsdato)
        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")

        assertEquals("01", vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
        assertEquals("0", vedtak?.grunnlag?.framtidigtrygdetid)

        val bergen = vedtak?.beregning?.get(0)
        assertEquals("2017-05-01", bergen?.periode?.fom)
        assertEquals(null, bergen?.periode?.tom)
        assertEquals("NOK", bergen?.valuta)
        assertEquals("03", bergen?.utbetalingshyppighet)

        assertEquals("2482", bergen?.beloepBrutto?.beloep)
        assertEquals(null, bergen?.beloepBrutto?.ytelseskomponentGrunnpensjon)
        assertEquals(null, bergen?.beloepBrutto?.ytelseskomponentTilleggspensjon)

        assertEquals(null, vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagbrg = vedtak?.avslagbegrunnelse?.get(0)
        assertEquals(null, avslagbrg?.begrunnelse)

        val dataof = sak?.kravtype?.get(0)?.datoFrist
        assertEquals("six weeks from the date the decision is received", dataof)

        assertEquals("2017-05-21", tillegg?.dato)

        assertEquals("NO:noinst002", tillegg?.andreinstitusjoner?.get(0)?.institusjonsid)
        assertEquals("NOINST002, NO INST002, NO", tillegg?.andreinstitusjoner?.get(0)?.institusjonsnavn)
        assertEquals("Postboks 6600 Etterstad TEST", tillegg?.andreinstitusjoner?.get(0)?.institusjonsadresse)
        assertEquals("0607", tillegg?.andreinstitusjoner?.get(0)?.postnummer)

    }

    @Test
    fun `forventet "08" ved  AvlsagsBegrunnelse på Uførepensjon ved TrygdleListeTom, HENSIKTSMESSIG_BEH`() {
        val pendata = generateFakePensjoninformasjonForUFOREP()
        pendata.vedtak.isBoddArbeidetUtland = true
        pendata.trygdetidListe.trygdetidListe.clear()
        pendata.vilkarsvurderingListe.vilkarsvurderingListe.get(0).vilkarsvurderingUforetrygd.hensiktsmessigBehandling = "HENSIKTSMESSIG_BEH"
        pendata.vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSLAG"
        pendata.vilkarsvurderingListe.vilkarsvurderingListe.get(0).vilkarsvurderingUforetrygd.alder = ""
        val result = dataFromPESYS.pensjonVedtak.createAvlsagsBegrunnelse(pendata)
        assertEquals("08", result)
    }

    @Test
    fun `forventer "03" på AvlsagsBegrunnelse Gjenlevendepensjon, TrygdleListeTom`() {

        val pendata1 = generateFakePensjoninformasjonForUFOREP()
        pendata1.vedtak.isBoddArbeidetUtland = true
        pendata1.trygdetidListe.trygdetidListe.clear()
        pendata1.vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSLAG"
        val result1 = dataFromPESYS.pensjonVedtak.createAvlsagsBegrunnelse(pendata1)
        assertEquals("03", result1)
    }

    @Test
    fun `forventer at ytelseprMaaned er siste i listen`() {
        val dataFromPESYS1 = mockPrefillPensionDataFromPESYS("P6000-UT-220.xml")

        prefill.vedtakId = "123456789"
        val pendata = dataFromPESYS1.getPensjoninformasjonFraVedtak(prefill)

        val sisteprmnd = dataFromPESYS1.hentSisteYtelsePerMaaned(pendata)

        assertEquals("2017-05-01", sisteprmnd.fom.simpleFormat())
        assertEquals("7191", sisteprmnd.belop.toString())
        assertEquals(false, dataFromPESYS1.isMottarMinstePensjonsniva(pendata))
        assertEquals("7191", dataFromPESYS1.hentYtelseBelop(pendata))

        assertEquals(false, dataFromPESYS1.hentVurdertBeregningsmetodeNordisk(pendata))
        assertEquals("EOS", dataFromPESYS1.hentVinnendeBergeningsMetode(pendata))
    }

    @Test
    fun `forventet korrekt utregnet ytelsePrMnd på Uforep hvor UT_ORDINER`() {
        val dataFromPESYS1 = mockPrefillPensionDataFromPESYS("P6000-UT-220.xml")
        prefill.vedtakId = "123456789"
        val pendata = dataFromPESYS1.getPensjoninformasjonFraVedtak(prefill)

        val result = dataFromPESYS1.pensjonVedtak.createBeregningItemList(pendata)

        val json = mapAnyToJson(result, true)
        logger.info("----------------------------------------------------------------")
        logger.info(json)
        logger.info("----------------------------------------------------------------")

        assertEquals(6, result.size)

        val ytelsePerMaaned1 = result[0]
        assertEquals("6917", ytelsePerMaaned1.beloepBrutto?.beloep)
        assertEquals("2015-12-01", ytelsePerMaaned1.periode?.fom)
        assertEquals("2015-12-31", ytelsePerMaaned1.periode?.tom)

        val ytelsePerMaaned2 = result[1]
        assertEquals("6917", ytelsePerMaaned2.beloepBrutto?.beloep)
        assertEquals("2016-01-01", ytelsePerMaaned2.periode?.fom)
        assertEquals("2016-04-30", ytelsePerMaaned2.periode?.tom)

        val ytelsePerMaaned3 = result[2]
        assertEquals("7110", ytelsePerMaaned3.beloepBrutto?.beloep)
        assertEquals("2016-05-01", ytelsePerMaaned3.periode?.fom)
        assertEquals("2016-08-31", ytelsePerMaaned3.periode?.tom)

    }

    @Test
    fun `forventet createVedtakTypePensionWithRule verdi`() {
        prefill = generatePrefillData(68, "P6000")
        val result = dataFromPESYS.pensjonVedtak.createVedtakTypePensionWithRule(pendata)
        assertEquals("02", result)
    }


}