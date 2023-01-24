package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.config.INSTITUTION_CACHE
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.PreviewPdf
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonDetalj
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Description
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.UnknownHttpStatusCodeException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

/**
 *   https://eux-app.nais.preprod.local/swagger-ui.html#/eux-cpi-service-controller/
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Component("fagmodulEuxKlient")
@Description("Service class for EuxBasis - eux-cpi-service-controller")
class EuxKlient(private val euxNavIdentRestTemplate: RestTemplate,
                private val euxSystemRestTemplate: RestTemplate,
                private val overrideWaitTimes: Long? = 1000L) {

    private val logger = LoggerFactory.getLogger(EuxKlient::class.java)

    //ny SED på ekisterende type eller ny svar SED på ekisternede rina
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSvarSed(navSEDjson: String,
                       euxCaseId: String,
                       parentDocumentId: String,
                       errorMessage: String,
                       metric: MetricsHelper.Metric ): BucSedResponse {
        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.postForEntity(
                    "/buc/$euxCaseId/sed/$parentDocumentId/svar",
                    HttpEntity(navSEDjson, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                    String::class.java
                )
            },
            euxCaseId =  euxCaseId,
            metric = metric,
            prefixErrorMessage = errorMessage,
            waitTimes = 20000L
        )
        return BucSedResponse(euxCaseId, response.body!!)
    }


    //ny SED på ekisterende type eller ny svar SED på ekisternede rina
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSed(navSEDjson: String,
                   euxCaseId: String,
                   metric: MetricsHelper.Metric,
                   errorMessage: String): BucSedResponse {

        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.postForEntity(
                    "/buc/$euxCaseId/sed?ventePaAksjon=false",
                    HttpEntity(navSEDjson, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                    String::class.java)
            },
            euxCaseId = euxCaseId,
            metric = metric,
            prefixErrorMessage =  errorMessage,
            waitTimes = 9000L
        )
        return BucSedResponse(euxCaseId, response.body!!)
    }

    fun getRinaUrl(RinaUrl: MetricsHelper.Metric): String {
        val rinaCallid = "-1-11-111"
        val path = "/url/buc/$rinaCallid"
        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(path, HttpMethod.GET, null, String::class.java)
            },
            metric = RinaUrl,
            prefixErrorMessage = "Feil ved henting av RinaURL"
        )
        val url =  response.body ?: run {
            logger.error("Feiler ved lasting av navSed: $path")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feiler ved response av RinaURL")
        }
        return url.replace(rinaCallid, "").also { logger.info("Url til Rina: $it") }
    }

    fun getSedOnBucByDocumentIdAsJsonAndAsSystemuser(euxCaseId: String, documentId: String, metric: MetricsHelper.Metric): String =
        getSedOnBucByDocumentId(euxCaseId, documentId, euxSystemRestTemplate, metric)

    fun getSedOnBucByDocumentIdAsJson(euxCaseId: String, documentId: String, metric: MetricsHelper.Metric): String =
        getSedOnBucByDocumentId(euxCaseId, documentId, euxNavIdentRestTemplate, metric)

    private fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String, restTemplate: RestTemplate, metric: MetricsHelper.Metric): String {
        val path = "/buc/$euxCaseId/sed/$documentId"

        val response = restTemplateErrorhandler(
            restTemplateFunction = { restTemplate.exchange(path,HttpMethod.GET,null, String::class.java) },
            euxCaseId= euxCaseId,
            metric = metric,
            prefixErrorMessage = "Feil ved henting av Sed med DocId: $documentId"
        )
        return response.body ?: run {
            logger.error("Feiler ved lasting av navSed: $path")
            throw SedDokumentIkkeLestException("Feiler ved lesing av navSED, feiler ved uthenting av SED")
        }
    }

    fun getBucJsonAsSystemuser(euxCaseId: String, metric: MetricsHelper.Metric): String = getBucJson(euxCaseId, euxSystemRestTemplate, metric)

    fun getBucJsonAsNavIdent(euxCaseId: String, metric: MetricsHelper.Metric): String = getBucJson(euxCaseId, euxNavIdentRestTemplate, metric)

    private fun getBucJson(euxCaseId: String, restTemplate: RestTemplate, metric: MetricsHelper.Metric): String {
        val path = "/buc/$euxCaseId"
        logger.info("getBucJsonWithRest prøver å kontakte EUX $path")

        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                restTemplate.exchange(path, HttpMethod.GET, null, String::class.java)
            },
            euxCaseId = euxCaseId,
            metric = metric,
            prefixErrorMessage = "Feiler ved metode getBucJsonWithRest",
            maxAttempts = 3,
            waitTimes = 6000L
        )
        return response.body ?: throw ServerException("Feil ved henting av BUCdata ingen data, euxCaseId $euxCaseId")
    }

    fun getPdfJson(euxCaseId: String, documentId: String, metric: MetricsHelper.Metric): PreviewPdf {

        val path = "/buc/${euxCaseId}/sed/${documentId}/pdf"
        logger.info("getPdfJsonWithRest prøver å kontakte EUX path")

        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(
                    path,
                    HttpMethod.GET,
                    HttpEntity("/", HttpHeaders().apply { contentType = MediaType.APPLICATION_PDF }),
                    Resource::class.java)
            },
            euxCaseId = euxCaseId,
            metric = metric,
            prefixErrorMessage = "Feiler ved metode GetPdf. ",
            maxAttempts = 4,
            waitTimes = 10000L
        )
        val filnavn = response.headers.contentDisposition.filename
        val contentType = response.headers.contentType!!.toString()

        val dokumentInnholdBase64 = String(Base64.getEncoder().encode(response.body!!.inputStream.readBytes()))
        return PreviewPdf(dokumentInnholdBase64, filnavn!!, contentType)

    }

    fun getBucDeltakere(euxCaseId: String, metric: MetricsHelper.Metric): List<ParticipantsItem> {
        logger.info("euxCaseId: $euxCaseId")

        val path = "/buc/$euxCaseId/bucdeltakere"
        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(path, HttpMethod.GET,null,String::class.java)
            },
            euxCaseId = euxCaseId,
            metric = metric,
            prefixErrorMessage = "Feiler ved metode getDeltakerer"
        )
        return mapJsonToAny(response.body!!)
    }

    /**
     * List all institutions connected to RINA.
     */
    @Cacheable(cacheNames = [INSTITUTION_CACHE], cacheManager = "fagmodulCacheManager")
    fun getInstitutions(bucType: String, landkode: String? = "", metric: MetricsHelper.Metric): List<InstitusjonDetalj> {
        val url = "/institusjoner?BuCType=$bucType&LandKode=${landkode ?: ""}"

        val responseInstitution = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(url, HttpMethod.GET, null, String::class.java)
            },
            metric = metric,
            prefixErrorMessage = "Feil ved innhenting av institusjoner"
        )
        return  mapJsonToAny(responseInstitution.body!!)
    }

    /**
     * Lister alle rinasaker på valgt fnr eller euxcaseid, eller bucType...
     * fnr er påkrved resten er fritt
     * @param fnr String, fødselsnummer
     * @param euxCaseId String, euxCaseid sak ID
     * @param bucType String, type buc
     * @param status String, status
     * @return List<Rinasak>
     */
    fun getRinasaker(fnr: String? = null, euxCaseId: String? = null, metric: MetricsHelper.Metric): List<Rinasak> {

        val uriComponent = getRinasakerUri(fnr, euxCaseId).also {  }

        logger.debug("** fnr: $fnr, eux: $euxCaseId, buc: NULL, status: OPEN **")
        logger.debug("** rinaSaker Url: ${uriComponent.toUriString()} **")

        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(uriComponent.toUriString(), HttpMethod.GET, null, String::class.java)
            },
            metric = metric,
            prefixErrorMessage = "Feil ved Rinasaker"
        )

        return mapJsonToAny(response.body!!)
    }

    fun createBuc(bucType: String, metric: MetricsHelper.Metric): String {
        val correlationId = correlationId()
        val builder = UriComponentsBuilder.fromPath("/buc")
                .queryParam("BuCType", bucType)
                .queryParam("KorrelasjonsId", correlationId)
                .build()

        logger.info("Kontakter EUX for å prøve på opprette ny BUC med korrelasjonId: $correlationId")
        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(builder.toUriString(), HttpMethod.POST, null, String::class.java)
            },
            euxCaseId = bucType,
            metric = metric,
            prefixErrorMessage = "Opprett Buc, "
        )
        response.body?.let { return it } ?: run {
            logger.error("Får ikke opprettet BUC på bucType: $bucType")
            throw IkkeFunnetException("Fant ikke noen euxCaseId på bucType: $bucType")
        }
    }

    private fun correlationId() = MDC.get("x_request_id") ?: UUID.randomUUID().toString()

    fun convertListInstitusjonItemToString(deltakere: List<String>): String {
        return deltakere.joinToString(separator = "") { deltaker ->
            require(deltaker.contains(":")) { "Ikke korrekt format på mottaker/institusjon... "}
            "&mottakere=${deltaker}"
        }
    }

    fun putBucMottakere(euxCaseId: String, institusjoner: List<String>, metric: MetricsHelper.Metric): Boolean {
        val correlationId = correlationId()
        val builder = UriComponentsBuilder.fromPath("/buc/$euxCaseId/mottakere")
                .queryParam("KorrelasjonsId", correlationId)
                .build()
        val url = builder.toUriString() + convertListInstitusjonItemToString(institusjoner)

        logger.debug("Kontakter EUX for å legge til deltager: $institusjoner med korrelasjonId: $correlationId på type: $euxCaseId")

        val result = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(url, HttpMethod.PUT, null, String::class.java)
            },
            euxCaseId = euxCaseId,
            metric = metric,
            prefixErrorMessage = "Feiler ved behandling. Får ikke lagt til mottaker på Buc. "
        )
        return result.statusCode == HttpStatus.OK
    }

    fun updateSedOnBuc(euxCaseId: String, dokumentId: String, sedPayload: String, metric: MetricsHelper.Metric): Boolean {
        val path = "/buc/$euxCaseId/sed/$dokumentId?ventePaAksjon=false"

        val result = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(
                    path,
                    HttpMethod.PUT,
                    HttpEntity(sedPayload, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                    String::class.java
                )
            },
            euxCaseId = euxCaseId,
            metric = metric,
            prefixErrorMessage = "Feiler ved behandling. Får ikke oppdatert document. "
        )
        return result.statusCode == HttpStatus.OK
    }

    @Throws(Throwable::class)
    fun <T> retryHelper(func: () -> T, maxAttempts: Int = 3, waitTimes: Long = 1000L, skipError: List<HttpStatus>): T {
        var failException: Throwable? = null
        var count = 0
        while (count < maxAttempts) {
            try {
                return func.invoke()
            } catch (ex: Throwable) {
                //magick sjekk...
                if (ex is HttpClientErrorException && skipError.isNotEmpty() && skipError.contains(ex.statusCode)) {
                    logger.warn("feilet å kontakte eux, feilmelding: ${ex.message}")
                    throw ex
                }
                count++
                logger.warn("feilet å kontakte eux prøver på nytt. nr.: $count, feilmelding: ${ex.message}")
                failException = ex
                Thread.sleep(waitTimes)
            }
        }
        logger.error("Feilet å kontakte eux melding: ${failException?.message}", failException)
        throw failException!!
    }

    fun <T> restTemplateErrorhandler(restTemplateFunction: () -> ResponseEntity<T>,
                                     euxCaseId: String? = null,
                                     metric: MetricsHelper.Metric,
                                     prefixErrorMessage: String,
                                     maxAttempts: Int = 3,
                                     waitTimes: Long = 1000L,
                                     skipError: List<HttpStatus> = listOf(HttpStatus.FORBIDDEN)
    ): ResponseEntity<T> {
        return metric.measure {
            return@measure try {
                val response = retryHelper(
                        func = { restTemplateFunction.invoke() },
                        maxAttempts = maxAttempts,
                        waitTimes = overrideWaitTimes ?: waitTimes,
                        skipError = skipError)
                response
            } catch (hcee: HttpClientErrorException) {
                val errorBody = hcee.responseBodyAsString
                logger.error("$prefixErrorMessage, HttpClientError med euxCaseID: $euxCaseId, body: $errorBody", hcee)
                when (hcee.statusCode) {
                    HttpStatus.UNAUTHORIZED -> throw RinaIkkeAutorisertBrukerException("Authorization token required for Rina.")
                    HttpStatus.FORBIDDEN -> throw ForbiddenException("Forbidden, Ikke tilgang")
                    HttpStatus.NOT_FOUND -> throw IkkeFunnetException("Ikke funnet")
                    HttpStatus.CONFLICT -> throw EuxConflictException("En konflikt oppstod under kall til Rina")
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
                throw GenericUnprocessableEntity("Ukjent statusfeil")
            } catch (ex: Exception) {
                logger.error("$prefixErrorMessage, med euxCaseID: $euxCaseId", ex)
                throw ServerException("Ukjent Feil oppstod euxCaseId: $euxCaseId,  ${ex.message}")
            }
        }
    }

    /**
     * eux-rina-api returnerer documentId ved oppretting av SED
     * Vi legger ved caseId (rinasaksnr).
     *
     * Skal bare endres dersom responsen fra EUX endrer seg.
     */
    data class BucSedResponse(
        val caseId: String,
        val documentId: String
    )

    /**
     * Respons fra eux-rina-api/cpi/rinasaker
     * Skal bare endres dersom responsen fra EUX endrer seg.
     */
    class Rinasak(
        val id: String? = null,
        val processDefinitionId: String? = null,
        val traits: Traits? = null,
        val applicationRoleId: String? = null,
        val properties: Properties? = null,
        val status: String? = null
    )

    /**
     * Del av respons fra eux-rina-api/cpi/rinasaker
     * Skal bare endres dersom responsen fra EUX endrer seg.
     */
    class Properties(
        val importance: String? = null,
        val criticality: String? = null
    )

    /**
     * Del av respons fra eux-rina-api/cpi/rinasaker
     * Skal bare endres dersom responsen fra EUX endrer seg.
     */
    class Traits(
        val birthday: String? = null,
        val localPin: String? = null,
        val surname: String? = null,
        val caseId: String? = null,
        val name: String? = null,
        val flowType: String? = null,
        val status: String? = null
    )

    companion object {

        fun getRinasakerUri(fnr: String? = null, euxCaseId: String? = null): UriComponents {
            require(!(fnr == null && euxCaseId == null)) {
                "Minst et søkekriterie må fylles ut for å få et resultat fra Rinasaker"
            }

            val uriComponent = if (euxCaseId != null && fnr == null) {
                UriComponentsBuilder.fromPath("/rinasaker")
                    .queryParam("rinasaksnummer", euxCaseId)
                    .queryParam("status", "\"open\"")
                    .build()
            } else if (euxCaseId == null && fnr != null) {
                UriComponentsBuilder.fromPath("/rinasaker")
                    .queryParam("fødselsnummer", fnr)
                    .queryParam("status", "\"open\"")
                    .build()
            } else {
                UriComponentsBuilder.fromPath("/rinasaker")
                    .queryParam("fødselsnummer", fnr ?: "")
                    .queryParam("rinasaksnummer", euxCaseId ?: "")
                    .queryParam("status","\"open\"")
                    .build()
            }
            return uriComponent
        }
    }
}

//--- Disse er benyttet av restTemplateErrorhandler  -- start
class IkkeFunnetException(message: String) : ResponseStatusException(HttpStatus.NOT_FOUND, message)

class RinaIkkeAutorisertBrukerException(message: String?) : ResponseStatusException(HttpStatus.UNAUTHORIZED, message)

class ForbiddenException(message: String?) : ResponseStatusException(HttpStatus.FORBIDDEN, message)

class EuxRinaServerException(message: String?) : ResponseStatusException(HttpStatus.NOT_FOUND, message)

class GenericUnprocessableEntity(message: String) : ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message)

class GatewayTimeoutException(message: String?) : ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, message)

class ServerException(message: String?) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)

class EuxConflictException(message: String?) : ResponseStatusException(HttpStatus.CONFLICT, message)

//--- Disse er benyttet av restTemplateErrorhandler  -- slutt

class SedDokumentIkkeOpprettetException(message: String) : ResponseStatusException(HttpStatus.NOT_FOUND, message)

class SedDokumentIkkeLestException(message: String?) : ResponseStatusException(HttpStatus.NOT_FOUND, message)

class EuxGenericServerException(message: String?) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)
