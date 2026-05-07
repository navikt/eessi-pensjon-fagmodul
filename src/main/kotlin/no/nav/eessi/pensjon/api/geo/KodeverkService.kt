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
        val url = "/landkoder/rina${format?.let { "?format=$it" } ?: ""}"
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

    fun getLandOgValutakoderAkseptertAvRina(format: String? = null): LandOgValutakodeMerKorrektFormat? {
        val url = "/landogvalutakoder/rina${format?.let { "?format=$it" } ?: ""}"
        logger.debug("KodeverkService getLandOgValutakoderAkseptertAvRina: $url")

        return try {
            val response = euxNavIdentRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }),
                String::class.java
            )
            logger.debug("Hent land- og valutakode API: response: ${response.toJson()}".trimMargin())
            response.body?.let {
                mapJsonToAny<LandOgValutakodeMerKorrektFormat>(it)
            } ?: throw RuntimeException("Response body er null")
        } catch (e: HttpStatusCodeException) {
            logger.error("HttpStatusCodeException oppstod under henting av land- og valutakoder: ${e.message}")
            throw RuntimeException(e)
        } catch (e: Exception) {
            logger.error("En feil oppstod under henting av land- og valutakoder: ${e.message}")
            throw RuntimeException(e)
        }
    }
}

data class LandkodeMerKorrektFormat(
    @JsonProperty("v4.2")
    val v42 : LandkodeRinaFormat? = null,
    @JsonProperty("v4.3")
    val v43 : LandkodeRinaFormat? = null,
    @JsonProperty("v4.4")
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

data class LandOgValutakodeMerKorrektFormat(
    @JsonProperty("v4.2")
    val v42 : LandOgValutakodeRinaFormat? = null,
    @JsonProperty("v4.3")
    val v43 : LandOgValutakodeRinaFormat? = null,
    @JsonProperty("v4.4")
    val v44 : LandOgValutakodeRinaFormat? = null
)

data class LandOgValutakodeRinaFormat(
    val euEftaLand: List<LandkodeFraRina>? = null,
    val verdensLand: List<LandkodeFraRina>? = null,
    val statsborgerskap: List<LandkodeFraRina>? = null,
    val verdensLandHistorisk: List<LandkodeFraRina>? = null,
    val euEftaValuta: List<ValutakodeFraRina>? = null,
    val verdensValuta: List<ValutakodeFraRina>? = null
)

data class ValutakodeFraRina(
    val valutakode: String? = null,
    val valutanavn: String? = null,
)