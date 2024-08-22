package no.nav.eessi.pensjon.api.geo

import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class KodeverkService(private val euxNavIdentRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(KodeverkService::class.java)

    fun getLandkoderAkseptertAvRina(format: String? = null): String? {
        val url = "/cpi/landkoder/rina"

        val response: ResponseEntity<String> = euxNavIdentRestTemplate.exchange(
            if (format != null) "$url?format=$format" else url,
            HttpMethod.GET,
            HttpEntity<String>(HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }),
            String::class.java
        )
        logger.debug("""getLandkoderAkseptertAvRina response body:
            | url: $url
            | response: ${response.toJson()}""".trimMargin())
        return response.body
    }
}