package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class LocalDateDeserializer protected constructor() : StdDeserializer<LocalDate>(LocalDate::class.java) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(LocalDateDeserializer::class.java) }

    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): LocalDate {
        val strDate = jp.readValueAs(String::class.java)
        val date = LocalDate.parse(strDate)
        logger.debug("konverter datotekst: $strDate til $date")
        return date
    }

    companion object {
        private const val serialVersionUID = 1L
    }

}


class LocalDateSerializer : StdSerializer<LocalDate>(LocalDate::class.java) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(LocalDateSerializer::class.java) }

    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(value: LocalDate, gen: JsonGenerator, sp: SerializerProvider) {

        val strDate = value.format(DateTimeFormatter.ISO_LOCAL_DATE)
        logger.debug("parse dato: $value til $strDate")
        gen.writeString(strDate)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}