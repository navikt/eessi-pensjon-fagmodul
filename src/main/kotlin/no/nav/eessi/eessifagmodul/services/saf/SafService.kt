package no.nav.eessi.eessifagmodul.services.saf

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

private val logger = LoggerFactory.getLogger(SafService::class.java)

@Service
class SafService(val safGraphQlOidcRestTemplate: RestTemplate,
                val safRestOidcRestTemplate: RestTemplate) {

    private val saf_teller_navn = "eessipensjon_fagmodul.saf"
    private val saf_teller_type_vellykkede = counter(saf_teller_navn, "vellykkede")
    private val saf_teller_type_feilede = counter(saf_teller_navn, "feilede")
    private val mapper = jacksonObjectMapper()


    private final fun counter(name: String, type: String): Counter {
        return Metrics.counter(name, "type", type)
    }

    fun hentDokumentMetadata(aktoerId: String) : HentMetadataResponse {
         try {
             val headers = HttpHeaders()
             headers.contentType = MediaType.APPLICATION_JSON
             val httpEntity = HttpEntity(genererQuery(aktoerId), headers)
             val response = safGraphQlOidcRestTemplate.exchange("/",
                     HttpMethod.POST,
                     httpEntity,
                     String::class.java)
             if (response.statusCode.is2xxSuccessful) {
                 val mappedResponse = mapper.readValue(response.body!!, HentMetadataResponse::class.java)
                 saf_teller_type_vellykkede.increment()
                 return mappedResponse
             } else {
                 saf_teller_type_feilede.increment()
                 throw RuntimeException("En feil oppstod under henting av dokument metadata fra SAF: ${response.statusCode}")
             }
         } catch(ex: Exception) {
             logger.error("En feil oppstod under henting av dokument metadata fra SAF: $ex")
             throw ex
         }
    }

    fun hentDokumentInnhold(journalpostId: String, dokumentInfoId: String) : HentdokumentResponse {
        try {
            val path = "/$journalpostId/$dokumentInfoId/ARKIV"
            val response = safRestOidcRestTemplate.exchange(path,
                    HttpMethod.GET,
                    HttpEntity("/"),
                    String::class.java)
            if (response.statusCode.is2xxSuccessful) {
                saf_teller_type_vellykkede.increment()
                val filnavn = response.headers.contentDisposition.filename
                val contentType = response.headers.contentType!!.toString()
                val base64innhold = Base64.getEncoder().encodeToString(response.body!!.toByteArray())
                return HentdokumentResponse(base64innhold, filnavn!!, contentType)
            } else {
                saf_teller_type_feilede.increment()
                throw RuntimeException("En feil oppstod under henting av dokumentinnhold fra SAF: ${response.statusCode}")
            }
        } catch(ex: Exception) {
            logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
            throw ex
        }
    }

    private fun genererQuery(aktoerId: String): String {
        val request = SafRequest(variables = Variables(BrukerId(aktoerId, BrukerIdType.AKTOERID), 10000))
        return request.toJson()
    }
}


