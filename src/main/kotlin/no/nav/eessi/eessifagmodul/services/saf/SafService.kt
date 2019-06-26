package no.nav.eessi.eessifagmodul.services.saf

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.eessifagmodul.services.EessiServiceException
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

private val logger = LoggerFactory.getLogger(SafService::class.java)

@Service
class SafService(var safGraphQlOidcRestTemplate: RestTemplate,
                var safRestOidcRestTemplate: RestTemplate) {

    private val hentDokumentMetadata_teller_navn = "eessipensjon_fagmodul.hentDokumentMetadata"
    private val hentDokumentMetadata_teller_type_vellykkede = counter(hentDokumentMetadata_teller_navn, "vellykkede")
    private val hentDokumentMetadata_teller_type_feilede = counter(hentDokumentMetadata_teller_navn, "feilede")
    private val hentDokumentInnhold_teller_navn = "eessipensjon_fagmodul.hentDokumentInnhold"
    private val hentDokumentInnhold_teller_type_vellykkede = counter(hentDokumentInnhold_teller_navn, "vellykkede")
    private val hentDokumentInnhold_teller_type_feilede = counter(hentDokumentInnhold_teller_navn, "feilede")
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
                 hentDokumentMetadata_teller_type_vellykkede.increment()
                 return mappedResponse
             } else {
                 hentDokumentMetadata_teller_type_feilede.increment()
                 throw EessiServiceException("En feil oppstod under henting av dokument metadata fra SAF: ${response.statusCode}", response.statusCode)
             }
         } catch(ex: EessiServiceException) {
             logger.error("En feil oppstod under henting av dokument metadata fra SAF: $ex")
             hentDokumentMetadata_teller_type_feilede.increment()
             throw ex
         } catch(ex: Exception) {
             logger.error("En feil oppstod under henting av dokument metadata fra SAF: $ex")
             hentDokumentMetadata_teller_type_feilede.increment()
             throw EessiServiceException("En feil oppstod under henting av dokument metadata fra SAF: $ex", HttpStatus.INTERNAL_SERVER_ERROR)
         }
    }

    fun hentDokumentInnhold(journalpostId: String,
                            dokumentInfoId: String,
                            variantFormat: VariantFormat) : HentdokumentInnholdResponse {
        try {
            logger.info("Henter dokumentinnhold for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId, variantformat: $variantFormat")
            val path = "/$journalpostId/$dokumentInfoId/$variantFormat"
            val response = safRestOidcRestTemplate.exchange(path,
                    HttpMethod.GET,
                    HttpEntity("/"),
                    String::class.java)
            if (response.statusCode.is2xxSuccessful) {
                hentDokumentMetadata_teller_type_vellykkede.increment()
                val filnavn = response.headers.contentDisposition.filename
                val contentType = response.headers.contentType!!.toString()
                val base64innhold = Base64.getEncoder().encodeToString(response.body!!.toByteArray())

                hentDokumentInnhold_teller_type_vellykkede.increment()
                return HentdokumentInnholdResponse(base64innhold, filnavn!!, contentType)
            } else {
                throw EessiServiceException("En feil oppstod under henting av dokumentinnhold fra SAF: ${response.statusCode}", response.statusCode)
            }
        } catch(ex: EessiServiceException) {
            logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
            hentDokumentInnhold_teller_type_feilede.increment()
            throw ex
        } catch(ex: Exception) {
            logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
            hentDokumentInnhold_teller_type_feilede.increment()
            throw EessiServiceException("En feil oppstod under henting av dokumentinnhold fra SAF", HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private fun genererQuery(aktoerId: String): String {
        val request = SafRequest(variables = Variables(BrukerId(aktoerId, BrukerIdType.AKTOERID), 10000))
        return request.toJson()
    }
}


