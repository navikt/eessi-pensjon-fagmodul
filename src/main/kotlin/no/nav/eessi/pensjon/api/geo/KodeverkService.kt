package no.nav.eessi.pensjon.api.geo

import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class KodeverkService(private val euxNavIdentRestTemplate: RestTemplate) {

    fun getLandkoderAkseptertAvRina(format: String? = null): String? {
        val url = "/landkoder/rina"

        val response: ResponseEntity<String> = euxNavIdentRestTemplate.exchange(
            if (format != null) "$url?format=$format" else url,
            HttpMethod.GET,
            HttpEntity<String>(HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }),
            String::class.java
        )

        return response.body
    }
}