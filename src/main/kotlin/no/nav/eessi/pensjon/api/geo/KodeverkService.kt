package no.nav.eessi.pensjon.api.geo

import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException

@Service
class KodeverkService(private val euxSystemRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(KodeverkService::class.java)

    fun getLandkoderAkseptertAvRina(format: String? = null): String? {
        val url = "/cpi/landkoder/rina${format?.let { "?format=$it" } ?: ""}"
        logger.debug("KodeverkService getLandkoderAkseptertAvRina: $url")

        return try {
            val response = euxSystemRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }),
                String::class.java
            )
            logger.debug("Hent landkode API response: ${response.toJson()}".trimMargin())
            response.body
        } catch (e: HttpStatusCodeException) {
            logger.error("HttpStatusCodeException oppstod under henting av landkoder: ${e.message}")
            throw RuntimeException(e)
        } catch (e: Exception) {
            logger.error("En feil oppstod under henting av landkoder: ${e.message}")
            throw RuntimeException(e)
        }
    }
}