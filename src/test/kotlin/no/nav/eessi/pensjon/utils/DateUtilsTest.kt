package no.nav.eessi.pensjon.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.text.ParseException
import javax.xml.datatype.DatatypeFactory


class DateUtilsTest {

    @Test
    fun `check XML calendar to Rina Date`() {
        val xmlcal = DatatypeFactory.newInstance().newXMLGregorianCalendar()
        xmlcal.month = 5
        xmlcal.year = 2020
        xmlcal.day = 20

        val toRinaDate = xmlcal.simpleFormat()

        assertNotNull(toRinaDate)
        assertEquals("2020-05-20", toRinaDate)
    }

    @Test
    fun `check XML calendar to Rina Date TZ2`() {
        //<foedselsdato>1962-06-20+02:00</foedselsdato>
        val xmlcal = DatatypeFactory.newInstance().newXMLGregorianCalendar()
        xmlcal.month = 6
        xmlcal.year = 1962
        xmlcal.day = 20
        xmlcal.timezone = +2
        xmlcal.setTime(2, 0,0)
        val toRinaDate = xmlcal.simpleFormat()
        assertNotNull(toRinaDate)
        assertEquals("1962-06-20", toRinaDate)
    }

    @Test
    fun `check XML calendar to Rina Date TZ1`() {
        val xmlcal = DatatypeFactory.newInstance().newXMLGregorianCalendar()
        xmlcal.month = 6
        xmlcal.year = 1962
        xmlcal.day = 20
        xmlcal.timezone = +1
        xmlcal.setTime(2, 0,0)

        val toRinaDate = xmlcal.simpleFormat()

        assertNotNull(toRinaDate)
        assertEquals("1962-06-20", toRinaDate)
    }

    @Test
    fun checkXMLdatoNotflipWhenTimeZoneSet() {
        val xmlcal = createXMLCalendarFromString("1962-06-20+02:00")
        assertEquals("1962-06-20", xmlcal.simpleFormat())
    }


    @Test
    fun `verify XML date is still 2016-01-01 to simpleFormat`() {
        val xmlcal = DatatypeFactory.newInstance().newXMLGregorianCalendar()
        xmlcal.month = 1
        xmlcal.year = 2016
        xmlcal.day = 1
        xmlcal.hour = 0
        xmlcal.minute = 0
        xmlcal.second = 1
        val toRinaDate = xmlcal.simpleFormat()
        assertNotNull(toRinaDate)
        assertEquals("2016-01-01", toRinaDate)
    }

    @Test
    fun `Test av konvertere datotekst til xmlkalender`() {
        val result = createXMLCalendarFromString("2016-01-01")
        assertNotNull(result)
        assertEquals("2016-01-01T00:00:00.000+01:00", result.toString())
        assertEquals("2016-01-01", result.simpleFormat())

    }

    @Test
    fun `Test av konvertere datotekst til xmlkalender feiler`() {
        assertThrows<ParseException> {
            createXMLCalendarFromString("2016-Ø1-01")
        }
    }
}
