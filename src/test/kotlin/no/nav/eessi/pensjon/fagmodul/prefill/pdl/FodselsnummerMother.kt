package no.nav.eessi.pensjon.fagmodul.prefill.pdl

import java.time.LocalDate

object FodselsnummerMother {

    fun generateRandomFnr(alder: Int): String {
        val fnrdate = LocalDate.now().minusYears(alder.toLong())
        val y = fnrdate.year.toString()
        val day = "01" // fixDigits(fnrdate.dayOfMonth.toString())
        val month = withLeadingZero(fnrdate.month.value.toString())
        val fixedyear = y.substring(2, y.length)
        val indivdnr = indvididnr(fnrdate.year)
        val fnr = day + month + fixedyear + indivdnr + "52"
        NavFodselsnummer(fnr) // validation
        return fnr
    }

    private fun withLeadingZero(str: String) = if (str.length == 1) "0$str" else str

    private fun indvididnr(year: Int) =
            when (year) {
                in 1900..1999 -> "433"
                in 1940..1999 -> "954"
                in 2000..2039 -> "543"
                else -> "739"
            }
}
