package no.nav.eessi.eessifagmodul.services.bucbucket

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.eessifagmodul.models.SED
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

private val logger = LoggerFactory.getLogger(BucBucketService::class.java)

// TODO: Work-in-progress. Venter på at grensesnittet dokumenteres og implementeres i eux-bucbucket
//@Service
@Deprecated(replaceWith = ReplaceWith("Nothing"), level = DeprecationLevel.WARNING, message = "Utgår")
class BucBucketService(val bucBucketOidcRestTemplate: RestTemplate) {

    private val BUCBUCKET_TELLER_NAVN = "eessipensjon_fagmodul.bucbucket"
    private val BUCBUCKET_TELLER_TYPE_VELLYKKEDE = counter(BUCBUCKET_TELLER_NAVN, "vellykkede")
    private val BUCBUCKET_TELLER_TYPE_FEILEDE = counter(BUCBUCKET_TELLER_NAVN, "feilede")

    final fun counter(name: String, type: String): Counter {
        return Metrics.counter(name, "type", type)
    }

    fun queryDocuments(queryParams: Map<QueryParameters, String>): List<QueryResult> {
        val responseEntity = doRequest("/seds", queryParams)

        return jacksonObjectMapper().readValue<List<QueryResult>>(responseEntity.body!!)
    }

    fun getDocument(correlationId: String): SED {
        val response = doRequest("/documents", mapOf(QueryParameters.CORRELATION_ID to correlationId))
        return jacksonObjectMapper().readValue(response.body!!)
    }

    private fun doRequest(path: String, queryParams: Map<QueryParameters, String>): ResponseEntity<String> {
        val uriBuilder = UriComponentsBuilder.fromPath(path)
        queryParams.forEach {
            uriBuilder.queryParam(it.key.paramName(), it.value)
        }

        val responseEntity = bucBucketOidcRestTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, null, String::class.java)

        if (responseEntity.statusCode.isError) {
            logger.error("Received ${responseEntity.statusCode} from bucbucket")
            BUCBUCKET_TELLER_TYPE_FEILEDE.increment()
            if (responseEntity.hasBody()) {
                logger.error(responseEntity.body.toString())
            }
            throw RuntimeException("Received ${responseEntity.statusCode} ${responseEntity.statusCode.reasonPhrase} from aktørregisteret")
        }
        BUCBUCKET_TELLER_TYPE_VELLYKKEDE.increment()
        return responseEntity
    }
}
