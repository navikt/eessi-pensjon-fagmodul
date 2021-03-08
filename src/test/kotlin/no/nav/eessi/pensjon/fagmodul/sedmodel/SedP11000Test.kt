package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SedP11000Test {

    @Test
    fun `compare SED P11000`() {
        val sedJson = getTestJsonFile("P11000_Fixed-NAV.json")

        val p11000sed = SED.fromJson(sedJson)

        Assertions.assertEquals(null, p11000sed.pensjon?.pensjoninfotillegg?.opphoraarsak)

        Assertions.assertEquals("2018-12-31", p11000sed.pensjon?.requestForPensionAmount?.fixedPeriodEndDate)
        Assertions.assertEquals("7.1. List of attachments", p11000sed.pensjon?.requestForPensionAmount?.listOfAttachments)
        Assertions.assertEquals("01", p11000sed.pensjon?.requestForPensionAmount?.requestForAmountType)

        JSONAssert.assertEquals(sedJson, p11000sed.toJsonSkipEmpty(), false)
    }


}