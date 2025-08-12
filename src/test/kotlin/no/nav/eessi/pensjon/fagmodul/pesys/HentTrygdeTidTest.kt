package no.nav.eessi.pensjon.fagmodul.pesys

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class HentTrygdeTidTest {

    @MockK
    private lateinit var euxInnhentingService: EuxInnhentingService

    private lateinit var hentTrygdeTid : HentTrygdeTid

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        val buc01Json = mapJsonToAny<Buc>(javaClass.getResource("/json/buc/trygdetid/P_BUC_01_P5000.json")!!.readText())
        every { euxInnhentingService.getBucAsSystemuser(any()) } returns buc01Json

        val sed1 = mapJsonToAny<P5000>(javaClass.getResource("/json/buc/trygdetid/p5000_78a8b06bcce8406697e06c56fd28f795.json")!!.readText())
        val sed2 = mapJsonToAny<P5000>(javaClass.getResource("/json/buc/trygdetid/p5000_f66d2d4dd1724b55b9a3edfa5299e209.json")!!.readText())
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1452582", "78a8b06bcce8406697e06c56fd28f795") } returns sed1
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1452582", "f66d2d4dd1724b55b9a3edfa5299e209") } returns sed2

        hentTrygdeTid = HentTrygdeTid(euxInnhentingService)
    }

    @Test
    fun `hentBucFraEux should return null when buc is not found`() {
        val result = hentTrygdeTid.hentBucFraEux(1452582, "12345678901")
        println(result)
    }
}