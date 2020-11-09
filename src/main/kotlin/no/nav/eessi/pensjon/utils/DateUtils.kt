package no.nav.eessi.pensjon.utils

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

private const val sdfPattern = "yyyy-MM-dd"

fun XMLGregorianCalendar.simpleFormat(): String {
    if (this.year > 2500) {
        return ""
    }
    val date = SimpleDateFormat(sdfPattern).parse(this.toString())
    return SimpleDateFormat(sdfPattern).format(date)
}

fun Date.simpleFormat(): String = SimpleDateFormat(sdfPattern).format(this)

fun createXMLCalendarFromString(dateStr: String): XMLGregorianCalendar {
    val date = SimpleDateFormat(sdfPattern).parse(dateStr)
    val gcal = GregorianCalendar()
    gcal.timeInMillis = date.time
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal)
}

fun convertToXMLocal(time: LocalDate): XMLGregorianCalendar {
    val gcal = GregorianCalendar()
    gcal.time = Date.from(time.atStartOfDay(ZoneId.systemDefault()).toInstant())
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal)
}
