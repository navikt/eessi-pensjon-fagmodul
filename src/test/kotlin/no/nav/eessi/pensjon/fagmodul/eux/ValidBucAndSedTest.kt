package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ValidBucAndSedTest {

    lateinit var validBucAndSed: ValidBucAndSed

    @BeforeEach
    fun startUpItAll() {
        validBucAndSed = ValidBucAndSed()
    }


    @Test
    fun `Calling euxService getAvailableSEDonBuc returns BuC lists`() {
        var buc = "P_BUC_01"
        var expectedResponse = listOf(SedType.P2000)
        var generatedResponse = validBucAndSed.getAvailableSedOnBuc (buc)
        Assertions.assertEquals(generatedResponse, expectedResponse)

        buc = "P_BUC_06"
        expectedResponse = listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000)
        generatedResponse = validBucAndSed.getAvailableSedOnBuc(buc)
        Assertions.assertEquals(generatedResponse, expectedResponse)
    }

    @Test
    fun `Calling euxService getAvailableSedOnBuc no input, return`() {
        val expected = "[ \"P2000\", \"P2100\", \"P2200\", \"P8000\", \"P5000\", \"P6000\", \"P7000\", \"P10000\", \"P14000\", \"P15000\" ]"
        val actual = validBucAndSed.getAvailableSedOnBuc(null)
        Assertions.assertEquals(expected, actual.toJson())
    }

}