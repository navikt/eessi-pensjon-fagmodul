package no.nav.eessi.eessifagmodul.utils

import no.nav.eessi.eessifagmodul.prefill.person.PersonDataFromTPS.Companion.generateRandomFnr
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.assertFalse


class NavFodselsnummerTest {

    @Test
    fun `valid check for age`() {
        val fnr = generateRandomFnr(48)
        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)
        assertEquals(48, navfnr.getAge())
        assertEquals(false, navfnr.getValidPentionAge())
    }

    @Test
    fun `valid check for old age`() {
        val fnr = generateRandomFnr(72)
        println("RandomFnr: $fnr")

        val navfnr = NavFodselsnummer(fnr)
        assertEquals(72, navfnr.getAge())
        assertEquals(true, navfnr.getValidPentionAge())
    }


    @Test
    fun `valid pention age`() {
        val fnr = generateRandomFnr(67)
        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)

        assertEquals(67, navfnr.getAge())
        assertEquals(true, navfnr.getValidPentionAge())
    }

    @Test
    fun `not valid pention age`() {
        val fnr = generateRandomFnr(66)
        println("RandomFnr: $fnr")

        val navfnr = NavFodselsnummer(fnr)
        assertEquals(false, navfnr.getValidPentionAge())
    }

    @Test
    fun `Test på bruker fnr 20år`() {
        val fnr = generateRandomFnr(20)
        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)

        assertEquals(20, navfnr.getAge())
        assertEquals(false, navfnr.isUnder18Year())
        assertEquals(false, navfnr.getValidPentionAge())
    }

    @Test
    fun `not valid pention very young age`() {
        val fnr = generateRandomFnr(10)
        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)
        assertEquals(10, navfnr.getAge())
        assertEquals(true, navfnr.isUnder18Year())
        assertEquals(false, navfnr.getValidPentionAge())
    }

    @Test
    fun `not valid pention age young age`() {
        val fnr = generateRandomFnr(25)
        println("RandomFnr: $fnr")

        val navfnr = NavFodselsnummer(fnr)
        assertEquals(false, navfnr.getValidPentionAge())
    }

    @Test
    fun `not valid pention age young age with DNR`() {
        val fnr = generateRandomFnr(25)
        val newfnr = mockDnr(fnr)

        val navfnr = NavFodselsnummer(newfnr)
        assertEquals(25, navfnr.getAge())
        assertEquals(false, navfnr.getValidPentionAge())
    }

    @Test
    fun `valid pention age with DNR`() {
        val fnr = generateRandomFnr(68)
        val newfnr = mockDnr(fnr)

        val navfnr = NavFodselsnummer(newfnr)
        assertEquals(true, navfnr.isDNumber())
    }

    @Test
    fun `Test av NavFodselsnummer med test av fnr`() {
        val navfnr = NavFodselsnummer("09034921435")
        assertEquals("09034921435", navfnr.fnr())

    }

    @Test
    fun `Test på NavFodselsnr med genereert fnr`() {
        val fnr = generateRandomFnr(69)
        val navfnr = NavFodselsnummer(fnr)

        assertFalse(navfnr.isDNumber())

        assertEquals(69, navfnr.getAge())
        assertEquals(true, navfnr.getValidPentionAge())

    }


    @Test
    fun `not valid pention age young age2`() {
        val fnr = generateRandomFnr(25)
        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)

        assertEquals(25, navfnr.getAge())
        assertEquals(false, navfnr.getValidPentionAge())
    }

    @Test
    fun `Is 17 year of age is under 18year`() {
        val fnr = generateRandomFnr(17)
        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)

        assertEquals(17, navfnr.getAge())
        assertEquals(true, navfnr.isUnder18Year())
    }

    @Test
    fun `Is 16 year of age is under 18year`() {
        val fnr = generateRandomFnr(16)
//        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)

        assertEquals(16, navfnr.getAge())
        assertEquals(true, navfnr.isUnder18Year())
    }

    @Test
    fun `Is 18 year of age is NOT under 18year`() {
        val fnr = generateRandomFnr(18)
//        println("RandomFnr: $fnr")
        val navfnr = NavFodselsnummer(fnr)

        assertEquals(18, navfnr.getAge())
        assertEquals(false, navfnr.isUnder18Year())
    }

    @Test
    fun `Is 19 year of age is NOT under 18year`() {
        val fnr = generateRandomFnr(19)
        val navfnr = NavFodselsnummer(fnr)
        assertEquals(19, navfnr.getAge())
        assertEquals(false, navfnr.isUnder18Year())
    }


    @Test
    fun `Dette er ikke et gyldig fodselsnummer`() {
        val fnrfeil = "08045500000"
        val navfnr = NavFodselsnummer(fnrfeil)
        assertEquals(false, navfnr.validate())
    }

    @Test
    fun `Dette er heller ikke et gyldig fodselsnummer`() {
        val fnrfeil = "20124200000"
        val navfnr = NavFodselsnummer(fnrfeil)
        assertEquals(false, navfnr.validate())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Dette er iallefall ikke et gyldig fodselsnummer da noen taster tegn inn`() {
        val fnrfeil = "201242ABBA2"
        NavFodselsnummer(fnrfeil)
    }


    @Test
    fun `Dette er et gyldig fodselsnummer`() {
        val fnrfeil = generateRandomFnr(67)
        val navfnr = NavFodselsnummer(fnrfeil)
        val check = navfnr.validate()
        assertEquals(true, check)
        assertEquals(false, navfnr.isUnder18Year())
        assertEquals(67, navfnr.getAge())
        val yearnow = LocalDate.now().year
        val bdate = yearnow - navfnr.getAge()
        assertEquals("" + bdate, navfnr.get4DigitBirthYear())
    }

    @Test
    fun `finne dato for 5 eller 10 eller 25år siden`() {
        val nowdate = LocalDate.of(2020, 5, 30)
        //val nowdate =LocalDate.now()
        val fiveyears = nowdate.minusYears(5)
        val tenyears = nowdate.minusYears(10)
        val tventyfiveeyears = nowdate.minusYears(25)
        val fiftyyears = nowdate.minusYears(50)

        println(nowdate)
        println(fiveyears)
        println(tenyears)
        println(tventyfiveeyears)
        println(fiftyyears)

        assertEquals("2020-05-30", nowdate.toString())
        assertEquals("2010-05-30", tenyears.toString())
        assertEquals("1970-05-30", fiftyyears.toString())
        println("--------------------------------")

        val freakdate = LocalDate.of(2012, 2, 29)

        println(freakdate)
        println(freakdate.plus(-1, ChronoUnit.YEARS))
        println(freakdate.plus(-1, ChronoUnit.YEARS).plus(1, ChronoUnit.YEARS))
        println(freakdate.plus(-1, ChronoUnit.YEARS).plus(1, ChronoUnit.YEARS).plusDays(1))
        println(freakdate.plusDays(1))

        assertEquals("2012-02-29", freakdate.toString())
        assertEquals("2012-03-01", freakdate.plusDays(1).toString())
    }

    //    private fun generateRandomFnr(yearsToSubtract: Int, indivdnr: Int = 496): String {
//        val fnrdate = LocalDate.now().minusYears(yearsToSubtract.toLong())
//        val y = fnrdate.year.toString()
//        val day = fixDigits(fnrdate.dayOfMonth.toString())
//        val month = fixDigits(fnrdate.month.value.toString())
//        val fixedyear = y.substring(2, y.length)
//        println(day + month + fixedyear + indivdnr + "52")
//        return day + month + fixedyear + indivdnr + "52" //43352
//    }
//
    private fun mockDnr(strFnr: String): String {
        val nvf = NavFodselsnummer(strFnr)
        val fdig = nvf.getFirstDigit()
        return when (fdig) {
            0 -> "4" + strFnr.substring(1, strFnr.length)
            1 -> "5" + strFnr.substring(1, strFnr.length)
            2 -> "6" + strFnr.substring(1, strFnr.length)
            3 -> "7" + strFnr.substring(1, strFnr.length)
            else -> strFnr
        }
    }

//    private fun generateRandomFnr(yearsToSubtract: Int): String {
//        val fnrdate = LocalDate.now().minusYears(yearsToSubtract.toLong())
//        val y = fnrdate.year.toString()
//        val day = fixDigits(fnrdate.dayOfMonth.toString())
//        val month = fixDigits(fnrdate.month.value.toString())
//        val fixedyear = y.substring(2, y.length)
//        val fnr = day + month + fixedyear + 43352
//        return fnr
//    }
//
//    private fun fixDigits(str: String): String {
//        if (str.length == 1) {
//            return "0$str"
//        }
//        return str
//    }


}