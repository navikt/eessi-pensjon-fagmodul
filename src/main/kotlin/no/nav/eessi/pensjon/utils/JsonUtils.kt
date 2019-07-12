package no.nav.eessi.pensjon.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClientException

inline fun <reified T : Any> typeRef(): ParameterizedTypeReference<T> = object : ParameterizedTypeReference<T>() {}
inline fun <reified T : Any> typeRefs(): TypeReference<T> = object : TypeReference<T>() {}


fun datatClazzToMap(clazz: Any): Map<String, String> {
    return ObjectMapper().convertValue(clazz, object : TypeReference<Map<String, Any>>() {})
}

inline fun <reified T : Any> mapJsonToAny(json: String, objec: TypeReference<T>, failonunknown: Boolean = false): T {
    if (validateJson(json)) {
        try {
            return jacksonObjectMapper()
//                .registerModule(JavaTimeModule())
//                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failonunknown)
                    .readValue(json, objec)
        } catch (mex: MismatchedInputException) {
            mex.printStackTrace()
            throw mex
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        }
    } else {
        throw IllegalArgumentException("Not valid json format")
    }
}

fun mapAnyToJson(data: Any): String {
    return jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(data)
}

fun mapAnyToJson(data: Any, nonempty: Boolean = false): String {
    return if (nonempty) {
        jacksonObjectMapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY)
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data)
    } else {
        mapAnyToJson(data)
    }
}

fun validateJson(json: String): Boolean {
    return try {
        jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .readTree(json)
        true
    } catch (ex: Exception) {
        ex.printStackTrace()
        false
    }
}

fun createErrorMessage(responseBody: String): RestClientException {
    return mapJsonToAny(responseBody, typeRefs())
}

fun errorBody(error: String, uuid: String = "no-uuid"): String {
    return "{\"success\": false, \n \"error\": \"$error\", \"uuid\": \"$uuid\"}"
}

fun successBody(): String {
    return "{\"success\": true}"
}



