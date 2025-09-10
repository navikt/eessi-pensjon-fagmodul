package no.nav.eessi.pensjon.fagmodul.pesys

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.sed.P5000
import org.junit.jupiter.api.Assertions.assertEquals
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HentTrygdeTidTest {

    @MockK
    private lateinit var euxInnhentingService: EuxInnhentingService

    @MockK
    private lateinit var kodeverkClient: KodeverkClient

    private lateinit var hentTrygdeTid : HentTrygdeTid

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        // kodeverk
        every { kodeverkClient.finnLandkode(eq("DK")) } returns "DNK"
        every { kodeverkClient.finnLandkode(eq("NO")) } returns "NOK"

        hentTrygdeTid = HentTrygdeTid(euxInnhentingService, kodeverkClient)
    }

    @Test
    fun `hent trygdetid for buc med kun en medlemsperiode`() {
        // buc
        val buc01Json = mapJsonToAny<Buc>(javaClass.getResource("/json/trygdetid/P_BUC_01_1452582_en_periode.json")!!.readText())
        every { euxInnhentingService.getBucAsSystemuser(any()) } returns buc01Json

        // sed
        val sed1 = mapJsonToAny<P5000>(javaClass.getResource("/json/trygdetid/p5000_78a8b06bcce8406697e06c56fd28f795.json")!!.readText())
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1452582", "78a8b06bcce8406697e06c56fd28f795") } returns sed1

        hentTrygdeTid = HentTrygdeTid(euxInnhentingService, kodeverkClient)

        val result = hentTrygdeTid.hentBucFraEux(1452582, "12345678901")
        val forventetTrygdetid = javaClass.getResource("/json/trygdetid/trygdetid_enkelt_medlemskap_452582.json")!!.readText()
        println(forventetTrygdetid)
        assertEquals(forventetTrygdetid, result?.toJson())
    }

    @Test
    fun `hent trygdetid med flere medlemsperioder`() {
        // buc
        val buc01Json = mapJsonToAny<Buc>(javaClass.getResource("/json/trygdetid/P_BUC_01_1442897_flere_perioder.json")!!.readText())
        every { euxInnhentingService.getBucAsSystemuser(any()) } returns buc01Json

        // sed
        val sed1 = mapJsonToAny<P5000>(javaClass.getResource("/json/trygdetid/p5000_eced18dddec1401789c1d59b1c969602.json")!!.readText())
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1442897", "9826099264d04ea780a9b2ee06683b79") } returns sed1

        val result = hentTrygdeTid.hentBucFraEux(1442897, "12345678901")
        val forventetTrygdetid = javaClass.getResource("/json/trygdetid/trygdetid_flere_medlemskap_1442897.json")!!.readText()
        println(forventetTrygdetid)
        assertEquals(forventetTrygdetid, result?.toJson())
    }

}