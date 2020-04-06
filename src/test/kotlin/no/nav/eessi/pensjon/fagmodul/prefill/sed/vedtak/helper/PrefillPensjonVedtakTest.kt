package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.helper

import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PensjonsinformasjonMother
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonVedtak
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClientMother.fraFil
import no.nav.eessi.pensjon.utils.mapAnyToJson
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

    @Test
    fun `forventer "01" på AvlsagsBegrunnelse for Alderpensjon TrygdleListeTom `() {
        val pendata = PensjonsinformasjonMother.pensjoninformasjonForSakstype("ALDER").apply {
            vedtak.isBoddArbeidetUtland = true
            trygdetidListe.trygdetidListe.clear()
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"
        }

        assertEquals("01", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata))
    }

    @Test
    fun `forventer "01" på AvlsagsBegrunnelse for Gjenlevende TrygdleListeTom `() {
        val pendata1 = PensjonsinformasjonMother.pensjoninformasjonForSakstype("GJENLEV").apply {
            vedtak.isBoddArbeidetUtland = true
            trygdetidListe.trygdetidListe.clear()
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"

        }
        assertEquals("01", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata1))
    }

    @Test
    fun `forventer "03" på AvlsagsBegrunnelse for AlderPensjon TrygdleListeTom, LAVT_TIDLIG_UTTAK`() {
        val pendata = PensjonsinformasjonMother.pensjoninformasjonForSakstype("ALDER").apply {
            vedtak.isBoddArbeidetUtland = true
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).avslagHovedytelse = "LAVT_TIDLIG_UTTAK"
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"
        }

        assertEquals("03", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata))
    }

    @Test
    fun `forventer "06" på AvlsagsBegrunnelse AlderPensjon TrygdleListeTom, UNDER_62`() {
        val pendata = PensjonsinformasjonMother.pensjoninformasjonForSakstype("ALDER").apply {
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).avslagHovedytelse = "UNDER_62"
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"
        }
        assertEquals("06", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata))
    }

    @Test
    fun `forventer "07" på AvlsagsBegrunnelse IKKE_MOTTATT_DOK`() {
        val pendata = PensjonsinformasjonMother.pensjoninformasjonForSakstype("ALDER").apply {
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).avslagHovedytelse = "IKKE_MOTTATT_DOK"
        }

        assertEquals("07", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata))
    }

    @Test
    fun `forventer "01" på AvlsagsBegrunnelse Gjenlevendepensjon, TrygdleListeTom`() {
        val pendata = PensjonsinformasjonMother.pensjoninformasjonForSakstype("GJENLEV").apply {
            vedtak.isBoddArbeidetUtland = true
            trygdetidListe.trygdetidListe.clear()
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"
        }

        assertEquals("01", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata))
    }

    @Test
    fun `forventer "03" på AvlsagsBegrunnelse Uførepensjon, TrygdleListeTom`() {
        val pendata = PensjonsinformasjonMother.pensjoninformasjonForSakstype("UFOREP").apply {
            vedtak.isBoddArbeidetUtland = true
            trygdetidListe.trygdetidListe.clear()
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"
        }

        assertEquals("03", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata))
    }

    @Test
    fun `forventet "08" ved  AvlsagsBegrunnelse på Uførepensjon ved TrygdleListeTom, HENSIKTSMESSIG_BEH`() {
        val pendata = PensjonsinformasjonMother.pensjoninformasjonForSakstype("UFOREP").apply {
            vedtak.isBoddArbeidetUtland = true
            trygdetidListe.trygdetidListe.clear()
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).vilkarsvurderingUforetrygd.hensiktsmessigBehandling = "HENSIKTSMESSIG_BEH"
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSL"
            vilkarsvurderingListe.vilkarsvurderingListe.get(0).vilkarsvurderingUforetrygd.alder = ""
        }

        assertEquals("08", PrefillPensjonVedtak.createAvlsagsBegrunnelse(pendata))
    }

    @Test
    fun `forventet korrekt utregnet ytelsePrMnd på Uforep hvor UT_ORDINER`() {
        val dataFromPESYS = PensjonsinformasjonHjelper(fraFil("P6000-UT-220.xml"))
        val pendata = dataFromPESYS.hentMedVedtak("someVedtakId")

        val result = PrefillPensjonVedtak.createBeregningItemList(pendata)

        mapAnyToJson(result, true)

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
}
