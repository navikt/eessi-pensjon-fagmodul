package no.nav.eessi.pensjon.api.geo

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
class KodeverkService(private val euxNavIdentRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(KodeverkService::class.java)

    fun getLandkoderAkseptertAvRina(format: String? = null): LandkodeMerKorrektFormat? {
        val url = "/cpi/landkoder/rina${format?.let { "?format=$it" } ?: ""}"
        logger.debug("KodeverkService getLandkoderAkseptertAvRina: $url")

        return try {
            val response = euxNavIdentRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }),
                String::class.java
            )
            logger.debug("Hent landkode API: response: ${response.toJson()}".trimMargin())
            response.body?.let {
                mapJsonToAny<LandkodeMerKorrektFormat>(it)
            } ?: throw RuntimeException("Response body er null")
        } catch (e: HttpStatusCodeException) {
            logger.error("HttpStatusCodeException oppstod under henting av landkoder: ${e.message}")
            throw RuntimeException(e)
        } catch (e: Exception) {
            logger.error("En feil oppstod under henting av landkoder: ${e.message}")
            throw RuntimeException(e)
        }
    }
}

data class LandkodeMerKorrektFormat(
    @JsonProperty("v4.2")
    val v43 : LandkodeRinaFormat? = null,
    @JsonProperty("v4.3")
    val v44 : LandkodeRinaFormat? = null
)

data class LandkodeRinaFormat(
    val euEftaLand: List<LandkodeFraRina>? = null,
    val verdensLand: List<LandkodeFraRina>? = null,
    val statsborgerskap: List<LandkodeFraRina>? = null,
    val verdensLandHistorisk: List<LandkodeFraRina>? = null)

data class LandkodeFraRina(
    val landkode: String? = null,
    val landnavn: String? = null,
)