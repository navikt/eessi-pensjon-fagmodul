package no.nav.eessi.pensjon.vedlegg.client

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.Resource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.util.*
import javax.annotation.PostConstruct

@Component
class SafClient(private val safGraphQlOidcRestTemplate: RestTemplate,
                private val safRestOidcRestTemplate: RestTemplate,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(SafClient::class.java)
    private val mapper = jacksonObjectMapper()

    private lateinit var HentDokumentMetadata: MetricsHelper.Metric
    private lateinit var HentDokumentInnhold: MetricsHelper.Metric
    private lateinit var HentRinaSakIderFraDokumentMetadata: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        HentDokumentMetadata = metricsHelper.init("HentDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        HentDokumentInnhold = metricsHelper.init("HentDokumentInnhold", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        HentRinaSakIderFraDokumentMetadata = metricsHelper.init("HentRinaSakIderFraDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

/*    // Vi trenger denne konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(RestTemplate(), RestTemplate())*/

    fun hentDokumentMetadata(aktoerId: String) : HentMetadataResponse {
        logger.info("Henter dokument metadata for aktørid: $aktoerId")

        return HentDokumentMetadata.measure {
            try {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val httpEntity = HttpEntity(genererQuery(aktoerId), headers)
                val response = safGraphQlOidcRestTemplate.exchange("/",
                        HttpMethod.POST,
                        httpEntity,
                        String::class.java)

                mapper.readValue(response.body!!, HentMetadataResponse::class.java)

            } catch (ce: HttpClientErrorException) {
                if(ce.rawStatusCode == 403) {
                    logger.error("En feil oppstod under henting av dokument metadata fra SAF for aktørID $aktoerId, ikke tilgang", ce)
                    throw HttpClientErrorException(ce.statusCode, "Du har ikke tilgang til dette dokument-temaet. Kontakt nærmeste leder for å få tilgang.")
                }
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: ${ce.responseBodyAsString}")
                throw HttpClientErrorException( ce.statusCode, "En feil oppstod under henting av dokument metadata fra SAF: ${ce.responseBodyAsString}")
            } catch (se: HttpServerErrorException) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: ${se.responseBodyAsString}", se)
                throw HttpServerErrorException(se.statusCode, "En feil oppstod under henting av dokument metadata fra SAF: ${se.responseBodyAsString}")
            } catch (mke: MissingKotlinParameterException) {
                logger.error("En feil oppstod ved mapping av metadata fra SAF" , mke)
                throw HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "En feil oppstod ved mapping av metadata fra SAF")
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: $ex")
                throw HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "En feil oppstod under henting av dokument metadata fra SAF")
            }
        }
    }

    fun hentDokumentInnhold(journalpostId: String,
                            dokumentInfoId: String,
                            variantFormat: String) : HentdokumentInnholdResponse {

        return HentDokumentInnhold.measure {
            try {
                logger.info("Henter dokumentinnhold for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId, variantformat: $variantFormat")
                val variantFormatEnum = VariantFormat.valueOf(variantFormat)

                val path = "/$journalpostId/$dokumentInfoId/$variantFormatEnum"
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_PDF

                val response = safRestOidcRestTemplate.exchange(path,
                        HttpMethod.GET,
                        HttpEntity("/", headers),
                        Resource::class.java)

                val filnavn = response.headers.contentDisposition.filename
                val contentType = response.headers.contentType!!.toString()

                val dokumentInnholdBase64 = String(Base64.getEncoder().encode(response.body!!.inputStream.readBytes()))
                HentdokumentInnholdResponse(dokumentInnholdBase64, filnavn!!, contentType)

            } catch (ce: HttpClientErrorException) {
                if(ce.rawStatusCode == 403) {
                    logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: for journalpostId: $journalpostId, dokumentInfoId $dokumentInfoId, ikke tilgang", ce)
                    throw HttpClientErrorException(ce.statusCode, "Du har ikke tilgang til dette dokument-temaet. Kontakt nærmeste leder for å få tilgang.")
                }
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: ${ce.responseBodyAsString}", ce)
                throw HttpClientErrorException( ce.statusCode, "En feil oppstod under henting av dokumentInnhold fra SAF: ${ce.responseBodyAsString}")
            } catch (se: HttpServerErrorException) {
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: ${se.responseBodyAsString}", se)
                throw HttpServerErrorException(se.statusCode, "En feil oppstod under henting av dokumentInnhold fra SAF: ${se.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
                throw HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "En feil oppstod under henting av dokumentinnhold fra SAF")
            }
        }
    }

    private fun genererQuery(aktoerId: String): String {
        val request = SafRequest(variables = Variables(BrukerId(aktoerId, BrukerIdType.AKTOERID), 10000))
        return request.toJson()
    }
}


