package no.nav.eessi.pensjon.services.kodeverk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import java.util.*
import javax.annotation.PostConstruct

@Component
@CacheConfig(cacheNames = ["kodeVerk"])
class KodeverkClient(private val kodeRestTemplate: RestTemplate,
                     @Value("\${NAIS_APP_NAME}") private val appName: String,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(KodeverkClient::class.java)

    private lateinit var KodeverkHentLandKode: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        KodeverkHentLandKode = metricsHelper.init("KodeverkHentLandKode")
    }

    fun hentAlleLandkoder() = hentLandKoder().toJson()

    fun hentLandkoderAlpha2() = hentLandKoder().map { it.landkode2 }

    @Cacheable
    fun hentLandKoder(): List<Landkode> {
        return KodeverkHentLandKode.measure {
            val tmpLandkoder = hentHierarki("LandkoderSammensattISO2")

            val rootNode = jacksonObjectMapper().readTree(tmpLandkoder)
            val noder = rootNode.at("/noder").toList()

            noder.map { node ->
                Landkode(node.at("/kode").textValue(),
                        node.at("/undernoder").findPath("kode").textValue())
            }.sortedBy { (sorting, _) -> sorting }.toList()
        }
    }

    @Cacheable("landkoder")
    fun finnLandkode(landkode: String): String? {

        if(landkode.isNullOrEmpty() || landkode.length !in 2..3){
            throw LandkodeException("Ugyldig landkode: $landkode")
        }
        return when(landkode.length){
            2 -> hentLandKoder().firstOrNull { it.landkode2 ==  landkode }?.landkode3
            3 -> hentLandKoder().firstOrNull { it.landkode3 ==  landkode }?.landkode2
            else -> throw LandkodeException("Ugyldig landkode: $landkode")
        }
    }

    private fun doRequest(builder: UriComponents) : String {
        try {
            val headers = HttpHeaders()
            headers["Nav-Consumer-Id"] = appName
            headers["Nav-Call-Id"] = UUID.randomUUID().toString()
            val requestEntity = HttpEntity<String>(headers)
            logger.debug("Header: $requestEntity")
            val response = kodeRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    String::class.java)

            return response.body ?: throw KodeverkException("Feil ved konvetering av jsondata fra kodeverk")

        } catch (ce: HttpClientErrorException) {
            logger.error(ce.message, ce)
            throw KodeverkException(ce.message!!)
        } catch (se: HttpServerErrorException) {
            logger.error(se.message, se)
            throw KodeverkException(se.message!!)
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            throw KodeverkException(ex.message!!)
        }
    }

    /**
     *  https://kodeverk.nais.adeo.no/api/v1/hierarki/LandkoderSammensattISO2/noder
     */
    private fun hentHierarki(hierarki: String) : String {
        val path = "/api/v1/hierarki/{hierarki}/noder"

        val uriParams = mapOf("hierarki" to hierarki)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        return doRequest(builder)
    }
}

data class Landkode (
        val landkode2: String, // SE
        val landkode3: String // SWE
)

class KodeverkException(message: String) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)
class LandkodeException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)

