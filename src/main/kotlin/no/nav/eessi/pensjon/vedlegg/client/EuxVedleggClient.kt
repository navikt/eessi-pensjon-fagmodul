package no.nav.eessi.pensjon.vedlegg.client

import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.UnknownHttpStatusCodeException
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Paths
import java.util.*
import javax.annotation.PostConstruct

@Component
class EuxVedleggClient(private val euxNavIdentRestTemplate: RestTemplate,
                       @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(EuxVedleggClient::class.java)

    private lateinit var VedleggPaaDokument: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        VedleggPaaDokument = metricsHelper.init("VedleggPaaDokument")
    }

    fun leggTilVedleggPaaDokument(aktoerId: String,
                                  rinaSakId: String,
                                  rinaDokumentId: String,
                                  filInnhold: String,
                                  fileName: String,
                                  filtype: String) {
        try {

            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA

            val disposition = ContentDisposition
                    .builder("form-data")
                    .filename("filename")
                    .name("file")
                    .build().toString()

            val attachmentMeta = LinkedMultiValueMap<String, String>()
            attachmentMeta.add(HttpHeaders.CONTENT_DISPOSITION, disposition)
            val dokumentInnholdBinary = Base64.getDecoder().decode(filInnhold)
            val attachmentPart = HttpEntity(dokumentInnholdBinary, attachmentMeta)

            val body = LinkedMultiValueMap<String, Any>()
            body.add("multipart", attachmentPart)

            val requestEntity = HttpEntity(body, headers)

            val queryUrl = UriComponentsBuilder
                    .fromPath("/buc/")
                    .path(rinaSakId)
                    .path("/sed/")
                    .path(rinaDokumentId)
                    .path("/vedlegg")
                    .queryParam("Filnavn", fileName.replaceAfterLast(".", "").removeSuffix("."))
                    .queryParam("Filtype", filtype)
                    .queryParam("synkron", true)
                    .build().toUriString()
            logger.info("Legger til vedlegg i buc: $rinaSakId, sed: $rinaDokumentId")

            restTemplateErrorhandler(
                    {
                        euxNavIdentRestTemplate.exchange(
                                queryUrl,
                                HttpMethod.POST,
                                requestEntity,
                                String::class.java)
                    }
                    , rinaSakId
                    , VedleggPaaDokument
                    ,"En feil opppstod under tilknytning av vedlegg rinaid: $rinaSakId, sed: $rinaDokumentId"
            )

        } catch (ex: Exception) {
            logger.error("En feil opppstod under tilknytning av vedlegg, ${ex.message}", ex)
            throw ex
        } finally {
            val file = File(Paths.get("").toAbsolutePath().toString() + "/" + fileName)
            file.delete()
        }
    }

    fun <T> restTemplateErrorhandler(restTemplateFunction: () -> ResponseEntity<T>, euxCaseId: String, metric: MetricsHelper.Metric, prefixErrorMessage: String): ResponseEntity<T> {
        return metric.measure {
            return@measure try {
                val response = retryHelper( func = { restTemplateFunction.invoke() } )
                response
            } catch (hcee: HttpClientErrorException) {
                val errorBody = hcee.responseBodyAsString
                logger.error("$prefixErrorMessage, HttpClientError med euxCaseID: $euxCaseId, body: $errorBody", hcee)
                when (hcee.statusCode) {
                    HttpStatus.UNAUTHORIZED -> throw RinaIkkeAutorisertBrukerException("Authorization token required for Rina,")
                    HttpStatus.FORBIDDEN -> throw ForbiddenException("Forbidden, Ikke tilgang")
                    HttpStatus.NOT_FOUND -> throw IkkeFunnetException("Ikke funnet")
                    else -> throw GenericUnprocessableEntity("En feil har oppstått")
                }
            } catch (hsee: HttpServerErrorException) {
                val errorBody = hsee.responseBodyAsString
                logger.error("$prefixErrorMessage, HttpServerError med euxCaseID: $euxCaseId, feilkode body: $errorBody", hsee)
                when (hsee.statusCode) {
                    HttpStatus.INTERNAL_SERVER_ERROR -> throw EuxRinaServerException("Rina serverfeil, kan også skyldes ugyldig input, $errorBody")
                    HttpStatus.GATEWAY_TIMEOUT -> throw GatewayTimeoutException("Venting på respons fra Rina resulterte i en timeout, $errorBody")
                    else -> throw GenericUnprocessableEntity("En feil har oppstått")
                }
            } catch (uhsce: UnknownHttpStatusCodeException) {
                val errorBody = uhsce.responseBodyAsString
                logger.error("$prefixErrorMessage, med euxCaseID: $euxCaseId errmessage: $errorBody", uhsce)
                throw GenericUnprocessableEntity("Ukjent statusfeil, $errorBody")
            } catch (ex: Exception) {
                logger.error("$prefixErrorMessage, med euxCaseID: $euxCaseId", ex)
                throw ServerException("Ukjent Feil oppstod euxCaseId: $euxCaseId,  ${ex.message}")
            }
        }
    }

    @Throws(Throwable::class)
    fun <T> retryHelper(func: () -> T, maxAttempts: Int = 3, waitTimes: Long = 1000L): T {
        var failException: Throwable? = null
        var count = 0
        while (count < maxAttempts) {
            try {
                return func.invoke()
            } catch (ex: Throwable) {
                count++
                logger.warn("feiled å kontakte eux prøver på nytt. nr.: $count, feilmelding: ${ex.message}")
                failException = ex
                Thread.sleep(waitTimes)
            }
        }
        logger.error("Feilet å kontakte eux melding: ${failException?.message}", failException)
        throw failException!!
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class IkkeFunnetException(message: String) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.UNAUTHORIZED)
class RinaIkkeAutorisertBrukerException(message: String?) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.FORBIDDEN)
class ForbiddenException(message: String?) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class EuxRinaServerException(message: String?) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
class GenericUnprocessableEntity(message: String) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.GATEWAY_TIMEOUT)
class GatewayTimeoutException(message: String?) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class ServerException(message: String?) : RuntimeException(message)
