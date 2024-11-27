package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ValidBucAndSedTest {

    @Test
    fun `Calling euxService getAvailableSEDonBuc returns BuC lists`() {
        var buc = P_BUC_01.name
        var expectedResponse = listOf(SEDTYPE_P2000)
        var generatedResponse = ValidBucAndSed.getAvailableSedOnBuc (buc)
        Assertions.assertEquals(generatedResponse, expectedResponse)

        buc = P_BUC_06.name
        expectedResponse = listOf(SEDTYPE_P5000, SEDTYPE_P6000, SEDTYPE_P7000, SEDTYPE_P10000)
        generatedResponse = ValidBucAndSed.getAvailableSedOnBuc(buc)
        Assertions.assertEquals(generatedResponse, expectedResponse)
    }

    @Test
    fun `Calling euxService getAvailableSedOnBuc no input, return`() {
        val expected = """
            [ "P2000", "P2100", "P2200", "P8000", "P5000", "P6000", "P7000", "P10000", "P14000", "P15000" ]
        """.trimIndent()
        val actual = ValidBucAndSed.getAvailableSedOnBuc(null)
        Assertions.assertEquals(expected, actual.toJson())
    }

    @Test
    fun `Calling getAvailableBucForGjenny gives a list of available bucs for gjenny`() {
        val expected = """[ "P_BUC_02", "P_BUC_04", "P_BUC_05", "P_BUC_06", "P_BUC_07", "P_BUC_08", "P_BUC_09", "P_BUC_10" ]""".trimIndent()
        val actual = ValidBucAndSed.pensjonsBucerForGjenny()
        Assertions.assertEquals(expected, actual.toJson())
    }
}