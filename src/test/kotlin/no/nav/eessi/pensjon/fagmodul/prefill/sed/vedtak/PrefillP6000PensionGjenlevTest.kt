package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillVedtakTestHelper.eessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillVedtakTestHelper.generateFakePensjoninformasjonForKSAK
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillVedtakTestHelper.generatePrefillData
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillVedtakTestHelper.vedtakDataFromPENFraFil
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.trygdetid.V1Trygdetid
import no.nav.pensjon.v1.trygdetidliste.V1TrygdetidListe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(MockitoJUnitRunner::class)
class PrefillP6000PensionGjenlevTest {

    lateinit var dataFromPESYS: VedtakDataFromPEN
    lateinit var prefill: PrefillDataModel
    lateinit var pendata: Pensjonsinformasjon

    @Before
    fun setup() {
        prefill = PrefillDataModel()
        dataFromPESYS = vedtakDataFromPENFraFil("P6000-GP-401.xml")
        generatePrefillData(60, "P6000", prefill)
        pendata = dataFromPESYS.getPensjoninformasjonFraVedtak(prefill)
    }

    @Test
    fun `forventet korrekt utfylling av Pensjon objekt på Gjenlevendepensjon`() {
        prefill = generatePrefillData(66, "vedtak", prefill)
        eessiInformasjon.mapEssiInformasjonTilPrefillDataModel(prefill)

//        val dataFromPESYS1 = mockPrefillP6000PensionDataFromPESYS("P6000-GP-401.xml")
//        val result = dataFromPESYS1.prefill(prefill)

        val result = dataFromPESYS.prefill(prefill)

        //ekstra for å sjekke om Gjenlevepensjon finnes.
        val pendata = dataFromPESYS.getPensjoninformasjonFraVedtak(prefill)
        assertEquals("GJENLEV", pendata.sakAlder.sakType)
        assertEquals("12345678901", pendata.person.pid)

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

        val pendata1 = generateFakePensjoninformasjonForKSAK("GJENLEV")
        pendata1.vedtak.isBoddArbeidetUtland = true
        pendata1.trygdetidListe.trygdetidListe.clear()
        pendata1.vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSLAG"

        val result1 = dataFromPESYS.pensjonVedtak.createAvlsagsBegrunnelse(pendata1)
        assertEquals("01", result1)
    }


    @Test
    fun `forventet createVedtakTypePensionWithRule verdi`() {
        prefill = generatePrefillData(68, "P6000", prefill)
        //dataFromPESYS1.getPensjoninformasjonFraVedtak("23123123")
        val result = dataFromPESYS.pensjonVedtak.createVedtakTypePensionWithRule(pendata)
        assertEquals("03", result)
    }

    @Test
    fun `forventet korrekt utfylt P6000 gjenlevende ikke bosat utland (avdød bodd i utland)`() {
        prefill = generatePrefillData(66, "P6000", prefill)
        eessiInformasjon.mapEssiInformasjonTilPrefillDataModel(prefill)

        val dataFromPESYS1 = vedtakDataFromPENFraFil("P6000-GP-IkkeUtland.xml")

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

        assertEquals("NO:noinst002", tillegg?.andreinstitusjoner?.get(0)?.institusjonsid)
        assertEquals("NOINST002, NO INST002, NO", tillegg?.andreinstitusjoner?.get(0)?.institusjonsnavn)
        assertEquals("Postboks 6600 Etterstad TEST", tillegg?.andreinstitusjoner?.get(0)?.institusjonsadresse)
        assertEquals("0607", tillegg?.andreinstitusjoner?.get(0)?.postnummer)
    }
    @Test(expected = IllegalStateException::class)
    fun `preutfylling P6000 feiler ved mangler av vedtakId`() {
        prefill = generatePrefillData(68, "P6000", prefill)
        prefill.vedtakId = ""
        dataFromPESYS.prefill(prefill)

    }

    @Test
    fun `summerTrygdeTid forventet 10 dager, erTrygdeTid forventet til false`() {
        val ttid1 = V1Trygdetid()
        ttid1.fom = PrefillVedtakTestHelper.convertToXMLcal(LocalDate.now().minusDays(50))
        ttid1.tom = PrefillVedtakTestHelper.convertToXMLcal(LocalDate.now().minusDays(40))


        val trygdetidListe = V1TrygdetidListe()
        trygdetidListe.trygdetidListe.add(ttid1)

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)

        assertEquals(10, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe
        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod i utland mindre totalt 10dager en mer en mindre en 30 og mindre en 360
        assertEquals(false, bolresult)
    }

    @Test
    fun `summerTrygdeTid forventet 70 dager, erTrygdeTid forventet til true`() {
        val ttid1 = V1Trygdetid()
        ttid1.fom = PrefillVedtakTestHelper.convertToXMLcal(LocalDate.now().minusDays(170))
        ttid1.tom = PrefillVedtakTestHelper.convertToXMLcal(LocalDate.now().minusDays(100))

        val trygdetidListe = V1TrygdetidListe()
        trygdetidListe.trygdetidListe.add(ttid1)

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)
        assertEquals(70, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe
        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod i utland mindre en mer en 30 mindre en 360?
        assertEquals(true, bolresult)
    }

    @Test
    fun `summerTrygdeTid forventet 15 dager, erTrygdeTid forventet til false`() {
        val trygdetidListe = PrefillVedtakTestHelper.createTrygdelisteTid()

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)

        assertEquals(15, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe

        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod for lite i utland mindre en 30 dager?
        assertEquals(false, bolresult)
    }

    @Test
    fun `summerTrygdeTid forventet 500 dager, erTrygdeTid forventet til false`() {
        val ttid1 = V1Trygdetid()
        ttid1.fom = PrefillVedtakTestHelper.convertToXMLcal(LocalDate.now().minusDays(700))
        ttid1.tom = PrefillVedtakTestHelper.convertToXMLcal(LocalDate.now().minusDays(200))

        val trygdetidListe = V1TrygdetidListe()
        trygdetidListe.trygdetidListe.add(ttid1)

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)

        assertEquals(500, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe

        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod mye i utland mer en 360d.
        assertEquals(false, bolresult)
    }

    @Test
    fun `summerTrygdeTid forventet 0`() {
        val fom = LocalDate.now().minusDays(0)
        val tom = LocalDate.now().plusDays(0)
        val trygdetidListe = V1TrygdetidListe()
        val ttid1 = V1Trygdetid()
        ttid1.fom = PrefillVedtakTestHelper.convertToXMLcal(fom)
        ttid1.tom = PrefillVedtakTestHelper.convertToXMLcal(tom)
        trygdetidListe.trygdetidListe.add(ttid1)
        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)
        assertEquals(0, result)
    }

    @Test(expected = java.lang.IllegalStateException::class)
    fun `feiler ved boddArbeidetUtland ikke sann`() {
        prefill = generatePrefillData(66, "P6000", prefill)
        val resdata = vedtakDataFromPENFraFil("P6000-AP-101.xml")
        resdata.prefill(prefill)
    }

    @Test
    fun `forventer "07" på AvlsagsBegrunnelse IKKE_MOTTATT_DOK`() {

        val pendata = generateFakePensjoninformasjonForKSAK("ALDER")
        pendata.vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSLAG"
        pendata.vilkarsvurderingListe.vilkarsvurderingListe.get(0).avslagHovedytelse = "IKKE_MOTTATT_DOK"
        val result = dataFromPESYS.pensjonVedtak.createAvlsagsBegrunnelse(pendata)
        assertEquals("07", result)
    }
}
