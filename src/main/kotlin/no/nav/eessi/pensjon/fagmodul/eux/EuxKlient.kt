package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.config.INSTITUTION_CACHE
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.PreviewPdf
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonDetalj
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Description
import org.springframework.core.io.Resource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.UnknownHttpStatusCodeException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import java.util.*
import javax.annotation.PostConstruct

/**
 *   https://eux-app.nais.preprod.local/swagger-ui.html#/eux-cpi-service-controller/
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Component("fagmodulEuxKlient")
@Description("Service class for EuxBasis - eux-cpi-service-controller")
class EuxKlient(
    private val euxNavIdentRestTemplate: RestTemplate,
    private val euxSystemRestTemplate: RestTemplate,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest(),
    private val overrideWaitTimes: Long? = 1000L
) {

    private val logger = LoggerFactory.getLogger(EuxKlient::class.java)

    private lateinit var SEDByDocumentId: MetricsHelper.Metric
    private lateinit var GetBUC: MetricsHelper.Metric
    private lateinit var BUCDeltakere: MetricsHelper.Metric
    private lateinit var GetKodeverk: MetricsHelper.Metric
    private lateinit var Institusjoner: MetricsHelper.Metric
    private lateinit var CreateBUC: MetricsHelper.Metric
    private lateinit var HentRinasaker: MetricsHelper.Metric
    private lateinit var PutMottaker: MetricsHelper.Metric
    private lateinit var PutDocument: MetricsHelper.Metric
    private lateinit var PingEux: MetricsHelper.Metric
    private lateinit var RinaUrl: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        SEDByDocumentId = metricsHelper.init("SEDByDocumentId", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        GetBUC = metricsHelper.init("GetBUC", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        BUCDeltakere = metricsHelper.init("BUCDeltakere", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        Institusjoner = metricsHelper.init("Institusjoner", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        CreateBUC = metricsHelper.init("CreateBUC", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        HentRinasaker = metricsHelper.init("HentRinasaker", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        PutMottaker = metricsHelper.init("PutMottaker", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        PutDocument = metricsHelper.init("PutDocument", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        PingEux = metricsHelper.init("PingEux")
        GetKodeverk = metricsHelper.init("GetKodeverk", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        RinaUrl = metricsHelper.init("RinaUrl")
    }


    //ny SED på ekisterende type eller ny svar SED på ekisternede rina
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSvarSed(navSEDjson: String,
                       euxCaseId: String,
                       parentDocumentId: String,
                       errorMessage: String,
                       metric: MetricsHelper.Metric
    ): BucSedResponse {
        val httpEntity = populerHttpEntity(navSEDjson)

        logger.debug("Kaller eux med json: $navSEDjson, euxCaseId: $euxCaseId, parentId: $parentDocumentId")
        val response = restTemplateErrorhandler(
            {
                euxNavIdentRestTemplate.postForEntity(
                    "/buc/$euxCaseId/sed/$parentDocumentId/svar",
                    httpEntity,
                    String::class.java
                )
            }, euxCaseId, metric, errorMessage, waitTimes = 20000L
        )
        return BucSedResponse(euxCaseId, response.body!!)
    }


    //ny SED på ekisterende type eller ny svar SED på ekisternede rina
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSed(navSEDjson: String,
                   euxCaseId: String,
                   metric: MetricsHelper.Metric,
                   errorMessage: String): BucSedResponse {

        val httpEntity = populerHttpEntity(navSEDjson)
        val ventePaAksjonVerdi = "false"

        logger.debug("Kaller eux med json: $navSEDjson, euxCaseId: $euxCaseId")
        val response = restTemplateErrorhandler(
                {
                    euxNavIdentRestTemplate.postForEntity(
                        "/buc/$euxCaseId/sed?ventePaAksjon=$ventePaAksjonVerdi",
                            httpEntity,
                            String::class.java)
                }
                , euxCaseId
                , metric
                , errorMessage
                , waitTimes = 9000L
        )
        return BucSedResponse(euxCaseId, response.body!!)
    }

    private fun populerHttpEntity(navSEDjson: String): HttpEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return HttpEntity(navSEDjson, headers)
    }

    fun getRinaUrl(): String {
        val rinaCallid = "-1-11-111" //hack rinaid/bucid for henting av rinaurl
        val path = "/url/buc/{RinaSakId}" //path
        val uriParams = mapOf("RinaSakId" to rinaCallid)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.debug("RinaUrl prøver å kontakte EUX ${builder.toUriString()}")
        val response = restTemplateErrorhandler(
            {
                euxNavIdentRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)
            }
            , ""
            , RinaUrl
            , "Feil ved henting av RinaURL"
        )
        val url =  response.body ?: run {
            logger.error("Feiler ved lasting av navSed: ${builder.toUriString()}")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feiler ved response av RinaURL")
        }
        return url.replace(rinaCallid, "").also { logger.info("Url til Rina: $it") }
    }

    fun getSedOnBucByDocumentIdAsJsonAndAsSystemuser(euxCaseId: String, documentId: String): String =
        getSedOnBucByDocumentIdWithRest(euxCaseId, documentId, euxSystemRestTemplate)

    fun getSedOnBucByDocumentIdAsJson(euxCaseId: String, documentId: String): String =
        getSedOnBucByDocumentIdWithRest(euxCaseId, documentId, euxNavIdentRestTemplate)

    private fun getSedOnBucByDocumentIdWithRest(euxCaseId: String, documentId: String, restTemplate: RestTemplate): String {
        val path = "/buc/{RinaSakId}/sed/{DokumentId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId, "DokumentId" to documentId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.debug("SedDocument prøver å kontakte EUX ${builder.toUriString()}")

        val response = restTemplateErrorhandler(
                {
                    restTemplate.exchange(builder.toUriString(),
                            HttpMethod.GET,
                            null,
                            String::class.java)
                }
                , euxCaseId
                , SEDByDocumentId
                , "Feil ved henting av Sed med DocId: $documentId"
        )
        return response.body ?: run {
            logger.error("Feiler ved lasting av navSed: ${builder.toUriString()}")
            throw SedDokumentIkkeLestException("Feiler ved lesing av navSED, feiler ved uthenting av SED")
        }
    }

    fun getBucJsonAsSystemuser(euxCaseId: String): String = getBucJsonWithRest(euxCaseId, euxSystemRestTemplate)

    fun getBucJson(euxCaseId: String): String = getBucJsonWithRest(euxCaseId, euxNavIdentRestTemplate)

    private fun getBucJsonWithRest(euxCaseId: String, restTemplate: RestTemplate): String {
        logger.info("get euxCaseId: $euxCaseId")

        val path = "/buc/{RinaSakId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.debug("BucJson prøver å kontakte EUX ${builder.toUriString()}")

        val response = restTemplateErrorhandler(
                restTemplateFunction = {
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            null,
                            String::class.java)
                }
                , euxCaseId = euxCaseId
                , metric = GetBUC
                , prefixErrorMessage = "Feiler ved metode GetBuc. "
                , maxAttempts = 3
                , waitTimes = 6000L
        )
        return response.body ?: throw ServerException("Feil ved henting av BUCdata ingen data, euxCaseId $euxCaseId")
    }

    fun getPdfJsonWithRest(euxCaseId: String, documentId: String): PreviewPdf {
        logger.info("get euxCaseId: $euxCaseId")

        val path = "/buc/${euxCaseId}/sed/${documentId}/pdf"
        val uriParams = mapOf("euxCaseId" to euxCaseId, "documentId" to documentId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.debug("BucJson prøver å kontakte EUX ${builder.toUriString()}")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF

        val response = restTemplateErrorhandler(
            restTemplateFunction = {
                euxNavIdentRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    HttpEntity("/", headers),
                    Resource::class.java)
            }
            , euxCaseId = euxCaseId
            , metric = GetBUC
            , prefixErrorMessage = "Feiler ved metode GetPdf. "
            , maxAttempts = 4
            , waitTimes = 10000L
        )
        val filnavn = response.headers.contentDisposition.filename
        val contentType = response.headers.contentType!!.toString()

        val dokumentInnholdBase64 = String(Base64.getEncoder().encode(response.body!!.inputStream.readBytes()))
        return PreviewPdf(dokumentInnholdBase64, filnavn!!, contentType)

    }

    fun getBucDeltakere(euxCaseId: String): List<ParticipantsItem> {
        logger.info("euxCaseId: $euxCaseId")

        val path = "/buc/{RinaSakId}/bucdeltakere"
        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.debug("BucDeltakere prøver å kontakte EUX /${builder.toUriString()}")

        val response = restTemplateErrorhandler(
                restTemplateFunction = {
                    euxNavIdentRestTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            null,
                            String::class.java
                    )
                }
                , euxCaseId = euxCaseId
                , metric = BUCDeltakere
                , prefixErrorMessage = "Feiler ved metode getDeltakerer. "
        )
        return try {
            mapJsonToAny(response.body!!, typeRefs())
        } catch (ex: Exception) {
            throw ServerException("Feil ved henting av BucDeltakere: ingen data, euxCaseId $euxCaseId")
       }
    }

    /**
     * List all institutions connected to RINA.
     */
    @Cacheable(cacheNames = [INSTITUTION_CACHE], cacheManager = "fagmodulCacheManager")
    fun getInstitutions(bucType: String, landkode: String? = ""): List<InstitusjonItem> {
        val url = "/institusjoner?BuCType=$bucType&LandKode=${landkode ?: ""}"

        val responseInstitution = restTemplateErrorhandler(
                {
                    euxNavIdentRestTemplate.exchange(
                        url,
                            HttpMethod.GET,
                            null,
                            String::class.java)
                }
                , ""
                , Institusjoner
                , "Feil ved innhenting av institusjoner"
        )
        val starttid = System.currentTimeMillis()
        val detaljList = mapJsonToAny(responseInstitution.body!!, typeRefs<List<InstitusjonDetalj>>())

        val institusjonListe = detaljList.asSequence()
                .filter { institusjon ->
                    institusjon.tilegnetBucs.any { tilegnetBucsItem ->
                        tilegnetBucsItem.institusjonsrolle == "CounterParty"
                                && tilegnetBucsItem.eessiklar
                                && tilegnetBucsItem.bucType == bucType
                    }
                }
                .map { institusjon ->
                    InstitusjonItem(institusjon.landkode, institusjon.id, institusjon.navn, institusjon.akronym)
                }
                .sortedBy { it.institution }
                .sortedBy { it.country }
                .toList()

        val slutttid = System.currentTimeMillis()
        val tidbrukt = slutttid - starttid
        logger.debug("Tid brukt på institusjonListe map: $tidbrukt ms")
        return institusjonListe
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
    fun getRinasaker(fnr: String? = null, euxCaseId: String? = null, bucType: String? = null, status: String? = null): List<Rinasak> {

        val uriComponent = getRinasakerUri(fnr, euxCaseId, bucType, status)

        logger.debug("** fnr: $fnr, eux: $euxCaseId, buc: $bucType, status: $status **")
        logger.debug("** rinaSaker Url: ${uriComponent.toUriString()} **")

        val response = restTemplateErrorhandler(
                {
                    euxNavIdentRestTemplate.exchange(uriComponent.toUriString(),
                            HttpMethod.GET,
                            null,
                            String::class.java)
                }
                , euxCaseId ?: ""
                , HentRinasaker
                , "Feil ved Rinasaker"
        )

        return mapJsonToAny(response.body!!, typeRefs())
    }

    companion object {

        fun getRinasakerUri(fnr: String? = null, euxCaseId: String? = null, bucType: String? = null, status: String? = null): UriComponents {
            require(!(fnr == null && euxCaseId == null && bucType == null && status == null)) {
                "Minst et søkekriterie må fylles ut for å få et resultat fra Rinasaker"
            }
//                logger.debug("** fnr: $fnr, eux: $euxCaseId, buc: $bucType, status: $status **")

            val uriComponent = if (euxCaseId != null && status != null && fnr == null) {
                UriComponentsBuilder.fromPath("/rinasaker")
                    .queryParam("rinasaksnummer", euxCaseId)
                    .queryParam("status", status)
                    .build()
            } else if (fnr != null && bucType != null && status != null && euxCaseId == null) {
                UriComponentsBuilder.fromPath("/rinasaker")
                    .queryParam("fødselsnummer", fnr)
                    .queryParam("buctype", bucType)
                    .queryParam("status", status)
                    .build()
            } else if (fnr != null && status != null && bucType == null && euxCaseId == null) {
                UriComponentsBuilder.fromPath("/rinasaker")
                    .queryParam("fødselsnummer", fnr)
                    .queryParam("status", status)
                    .build()
            } else {
                UriComponentsBuilder.fromPath("/rinasaker")
                    .queryParam("fødselsnummer", fnr ?: "")
                    .queryParam("rinasaksnummer", euxCaseId ?: "")
                    .queryParam("buctype", bucType ?: "")
                    .queryParam("status", status ?: "")
                    .build()
            }
            return uriComponent
        }
    }


    fun createBuc(bucType: String): String {
        val correlationId = correlationId()
        val builder = UriComponentsBuilder.fromPath("/buc")
                .queryParam("BuCType", bucType)
                .queryParam("KorrelasjonsId", correlationId)
                .build()

        logger.info("Kontakter EUX for å prøve på opprette ny BUC med korrelasjonId: $correlationId")
        val response = restTemplateErrorhandler(
                {
                    euxNavIdentRestTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.POST,
                            null,
                            String::class.java)
                }
                , bucType
                , CreateBUC
                , "Opprett Buc, "
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

    fun putBucMottakere(euxCaseId: String, institusjoner: List<String>): Boolean {
        val correlationId = correlationId()
        val builder = UriComponentsBuilder.fromPath("/buc/$euxCaseId/mottakere")
                .queryParam("KorrelasjonsId", correlationId)
                .build()
        val url = builder.toUriString() + convertListInstitusjonItemToString(institusjoner)

        logger.debug("Kontakter EUX for å legge til deltager: $institusjoner med korrelasjonId: $correlationId på type: $euxCaseId")

        val result = restTemplateErrorhandler(
                {
                    euxNavIdentRestTemplate.exchange(
                            url,
                            HttpMethod.PUT,
                            null,
                            String::class.java)
                }
                , euxCaseId
                , PutMottaker
                , "Feiler ved behandling. Får ikke lagt til mottaker på Buc. "
        )
        return result.statusCode == HttpStatus.OK

    }


    fun updateSedOnBuc(euxCaseId: String, dokumentId: String, sedPayload: String): Boolean {
        ///cpi/buc/{RinaSakId}/sed/{DokumentId}
        val ventePaAksjonVerdi = "false"
        val builder = UriComponentsBuilder.fromPath("/buc/$euxCaseId/sed/$dokumentId?ventePaAksjon=$ventePaAksjonVerdi").build()
        val url = builder.toUriString()

        logger.info("Oppdaterer document med euxCaseId: $euxCaseId og documentid: $dokumentId")
        val result = restTemplateErrorhandler(
            {
                euxNavIdentRestTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    populerHttpEntity(sedPayload),
                    String::class.java)
            }
            , euxCaseId
            , PutDocument
            , "Feiler ved behandling. Får ikke oppdatert document. "
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
                                     euxCaseId: String,
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

class EuxServerException(message: String?) : ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message)
