package no.nav.eessi.pensjon.fagmodul.api.vedlegg.client

import no.nav.eessi.pensjon.eux.klient.*
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.UnknownHttpStatusCodeException
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Paths
import java.util.Base64

@Component
class EuxVedleggClient(
    private val euxNavIdentRestTemplate: RestTemplate,
    private val euxNavIdentRestTemplateV2: RestTemplate,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(EuxVedleggClient::class.java)

    private var VedleggPaaDokument: MetricsHelper.Metric
    private var enkeltVedlegg: MetricsHelper.Metric

    init {
        VedleggPaaDokument = metricsHelper.init("VedleggPaaDokument")
        enkeltVedlegg = metricsHelper.init("enkeltVedlegg")
    }


    fun hentEnkeltVedlegg(rinaSakId: String, dokumentId: String, vedleggId: String): HentdokumentInnholdResponse {
        val queryUrl = UriComponentsBuilder
            .fromPath("/buc/")
            .path(rinaSakId)
            .path("/sed/")
            .path(dokumentId)
            .path("/vedlegg/")
            .path(vedleggId)
            .build().toUriString()

        logger.info("Henter vedlegg fra buc: $rinaSakId, sed: $dokumentId, vedlegg: $vedleggId")

        val responseFraEux: ResponseEntity<ByteArray> = restTemplateErrorhandler(
            {
                euxNavIdentRestTemplateV2.exchange(
                    queryUrl,
                    HttpMethod.GET,
                    null,
                    ByteArray::class.java)
            }
            , rinaSakId
            , VedleggPaaDokument
            ,"En feil opppstod under henting av vedlegg rinaid: $rinaSakId, sed: $dokumentId, vedlegg: $vedleggId"
        )

        val filnavn = responseFraEux.headers.contentDisposition.filename
        val contentType = responseFraEux.headers.contentType!!.toString()

        val dokumentInnholdBase64 = Base64.getEncoder().encodeToString(responseFraEux.body ?: throw RuntimeException("Vedlegg ikke funnet"))
        val hentdokumentInnholdResponse = HentdokumentInnholdResponse(dokumentInnholdBase64, filnavn!!, contentType)
        logger.info("Resulat fra vedlegg henting \n " + responseFraEux.toJson())
        return hentdokumentInnholdResponse
    }



    fun leggTilVedleggPaaDokument(aktoerId: String,
                                  rinaSakId: String,
                                  rinaDokumentId: String,
                                  dokumentInnholdBinary: ByteArray,
                                  fileName: String,
                                  filtype: String) {
        try {
            logger.info("Legger til vedlegg i buc: $rinaSakId, sed: $rinaDokumentId, aktoerId: $aktoerId, filType: $filtype, filnavn: $fileName")

            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA

            val disposition = ContentDisposition
                    .builder("form-data")
                    .filename("filename")
                    .name("file")
                    .build()

            val attachmentPart =  HttpEntity(dokumentInnholdBinary, HttpHeaders().apply {
                contentDisposition = disposition
            })

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

            logger.info("Oppdaterer $rinaSakId med vedlegg")
            val responseFraEux = restTemplateErrorhandler(
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
            logger.info("Resulat fra vedlegg oppdatering \n " + responseFraEux.toJson())

        } catch (ex: Exception) {
            logger.error("En feil opppstod under tilknytning av vedlegg, ${ex.message}", ex)
            throw ex
        } finally {
            val file = File(Paths.get("").toAbsolutePath().toString() + "/" + fileName)
            file.delete()
        }
    }

    fun <T : Any> restTemplateErrorhandler(restTemplateFunction: () -> ResponseEntity<T>, euxCaseId: String, metric: MetricsHelper.Metric, prefixErrorMessage: String): ResponseEntity<T> {
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
                    HttpStatus.INTERNAL_SERVER_ERROR -> throw EuxRinaServerException("Serverfeil, kan også skyldes ugyldig input, $errorBody")
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
