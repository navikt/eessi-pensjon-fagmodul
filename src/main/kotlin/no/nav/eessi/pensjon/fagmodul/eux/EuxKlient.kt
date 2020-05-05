package no.nav.eessi.pensjon.fagmodul.eux

import com.google.common.base.Preconditions
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonDetalj
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.security.sts.typeRef
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Description
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.UnknownHttpStatusCodeException
import org.springframework.web.util.UriComponentsBuilder
import java.util.*
import javax.annotation.PostConstruct

/**
 *   https://eux-app.nais.preprod.local/swagger-ui.html#/eux-cpi-service-controller/
 */
@Component
@Description("Service class for EuxBasis - eux-cpi-service-controller")
@CacheConfig(cacheNames = ["euxService"])
class EuxKlient(private val euxOidcRestTemplate: RestTemplate,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()),
                private val overrideWaitTimes: Long? = null) {

    // Vi trenger denne no arg konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(RestTemplate(), MetricsHelper(SimpleMeterRegistry()))

    private val logger = LoggerFactory.getLogger(EuxKlient::class.java)

    private lateinit var SEDByDocumentId: MetricsHelper.Metric
    private lateinit var GetBUC: MetricsHelper.Metric
    private lateinit var BUCDeltakere: MetricsHelper.Metric
    private lateinit var Institusjoner: MetricsHelper.Metric
    private lateinit var CreateBUC: MetricsHelper.Metric
    private lateinit var HentRinasaker: MetricsHelper.Metric
    private lateinit var PutMottaker: MetricsHelper.Metric
    private lateinit var PingEux: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        SEDByDocumentId = metricsHelper.init("SEDByDocumentId")
        GetBUC = metricsHelper.init("GetBUC")
        BUCDeltakere = metricsHelper.init("BUCDeltakere")
        Institusjoner = metricsHelper.init("Institusjoner")
        CreateBUC = metricsHelper.init("CreateBUC")
        HentRinasaker = metricsHelper.init("HentRinasaker")
        PutMottaker = metricsHelper.init("PutMottaker")
        PingEux = metricsHelper.init("PingEux")
    }

    //ny SED på ekisterende type eller ny svar SED på ekisternede rina
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSed(urlPath: String, navSEDjson: String, euxCaseId: String, metric: MetricsHelper.Metric, errorMessage: String, parentDocumentId: String?): BucSedResponse {

        val uriParams = mapOf("RinaSakId" to euxCaseId, "DokuemntId" to parentDocumentId).filter { it.value != null }
        val builder = UriComponentsBuilder.fromUriString(urlPath)
                .queryParam("KorrelasjonsId",  correlationId())
                .queryParam("ventePaAksjon", "false")
                .buildAndExpand(uriParams)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        //legger til navsed som json skipper nonemty felter dvs. null
        val httpEntity = HttpEntity(navSEDjson, headers)

        val response = restTemplateErrorhandler(
                {
                    euxOidcRestTemplate.exchange(builder.toUriString(),
                            HttpMethod.POST,
                            httpEntity,
                            String::class.java)
                }
                , euxCaseId
                , metric
                , errorMessage
                , waitTimes = 20000L
        )
        return BucSedResponse(euxCaseId, response.body!!)
    }

    //henter ut sed fra rina med bucid og documentid
    @Throws(EuxServerException::class, SedDokumentIkkeLestException::class)
    fun getSedOnBucByDocumentIdAsJson(euxCaseId: String, documentId: String): String {
        val path = "/buc/{RinaSakId}/sed/{DokumentId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId, "DokumentId" to documentId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        logger.info("Prøver å kontakte EUX /${builder.toUriString()}")

        val response = restTemplateErrorhandler(
                {
                    euxOidcRestTemplate.exchange(builder.toUriString(),
                            HttpMethod.GET,
                            null,
                            String::class.java)
                }
                , euxCaseId
                , SEDByDocumentId
                , "Feil ved henting av Sed med DocId: $documentId"
        )
        return response.body ?: {
            logger.error("Feiler ved lasting av navSed: ${builder.toUriString()}")
            throw SedDokumentIkkeLestException("Feiler ved lesing av navSED, feiler ved uthenting av SED")
        }()
    }

    fun getBucJson(euxCaseId: String): String {
        logger.info("euxCaseId: $euxCaseId")

        val path = "/buc/{RinaSakId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.info("Prøver å kontakte EUX /${builder.toUriString()}")

        val response = restTemplateErrorhandler(
                restTemplateFunction = {
                    euxOidcRestTemplate.exchange(
                                builder.toUriString(),
                                HttpMethod.GET,
                                null,
                                String::class.java)
                }
                , euxCaseId = euxCaseId
                , metric = GetBUC
                , prefixErrorMessage = "Feiler ved metode GetBuc. "
        )
        return response.body ?: throw ServerException("Feil ved henting av BUCdata ingen data, euxCaseId $euxCaseId")
    }

    fun getBucDeltakere(euxCaseId: String): List<ParticipantsItem> {
        logger.info("euxCaseId: $euxCaseId")

        val path = "/buc/{RinaSakId}/bucdeltakere"
        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.info("Prøver å kontakte EUX /${builder.toUriString()}")

        val response = restTemplateErrorhandler(
                restTemplateFunction = {
                    euxOidcRestTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            null,
                            typeRef<List<ParticipantsItem>>())
                }
                , euxCaseId = euxCaseId
                , metric = BUCDeltakere
                , prefixErrorMessage = "Feiler ved metode getDeltakerer. "
        )
        return  response.body ?: throw ServerException("Feil ved henting av BucDeltakere: ingen data, euxCaseId $euxCaseId")
    }

    /**
     * List all institutions connected to RINA.
     */
    @Cacheable
    fun getInstitutions(bucType: String, landkode: String? = ""): List<InstitusjonItem> {
        val builder = UriComponentsBuilder.fromPath("/institusjoner")
                .queryParam("BuCType", bucType)
                .queryParam("LandKode", landkode ?: "")

        val responseInstitution = restTemplateErrorhandler(
                {
                    euxOidcRestTemplate.exchange(builder.toUriString(),
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
                .map {  data ->
                        val bucs = data.tilegnetBucs?.asSequence()
                                ?.filter { it?.institusjonsrolle == "CounterParty" }
                                ?.map { it?.bucType ?: "" }
                                ?.sortedBy { it }
                                ?.toSet()
                    InstitusjonItem(data.landkode!!, data.id!!, data.akronym, bucs!!.toList() )
                }
                .sortedBy { sort-> sort.country }
                .sortedBy { sort -> sort.institution }
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
    fun getRinasaker(fnr: String?, euxCaseId: String?, bucType: String?, status: String?): List<Rinasak> {
        require(!(fnr == null && euxCaseId == null && bucType == null && status == null)) {
            "Minst et søkekriterie må fylles ut for å få et resultat fra Rinasaker"
        }

        val uriComponent = UriComponentsBuilder.fromPath("/rinasaker")
                .queryParam("fødselsnummer", fnr ?: "")
                .queryParam("rinasaksnummer", euxCaseId ?: "")
                .queryParam("buctype", bucType ?: "")
                .queryParam("status", status ?: "")
                .build()

        val response = restTemplateErrorhandler(
                {
                    euxOidcRestTemplate.exchange(uriComponent.toUriString(),
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

    fun createBuc(bucType: String): String {
        val correlationId = correlationId()
        val builder = UriComponentsBuilder.fromPath("/buc")
                .queryParam("BuCType", bucType)
                .queryParam("KorrelasjonsId", correlationId)
                .build()

        logger.info("Kontakter EUX for å prøve på opprette ny BUC med korrelasjonId: $correlationId")
        val response = restTemplateErrorhandler(
                {
                    euxOidcRestTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.POST,
                            null,
                            String::class.java)
                }
                , bucType
                , CreateBUC
                , "Opprett Buc, "
        )
        response.body?.let { return it } ?: {
            logger.error("Får ikke opprettet BUC på bucType: $bucType")
            throw IkkeFunnetException("Fant ikke noen euxCaseId på bucType: $bucType")
        }()
    }

    private fun correlationId() = MDC.get("x_request_id") ?: UUID.randomUUID().toString()

    fun convertListInstitusjonItemToString(deltakere: List<String>): String {
        val encodedList = mutableListOf<String>()
        deltakere.forEach { institusjon ->
            Preconditions.checkArgument(institusjon.contains(":"), "Ikke korrekt format på mottaker/institusjon... ")
            encodedList.add("&mottakere=${institusjon}")
        }
        return encodedList.joinToString(separator = "")
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
                    euxOidcRestTemplate.exchange(
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

    @Throws(EuxServerException::class)
    fun pingEux(): Boolean {

        val builder = UriComponentsBuilder.fromPath("/kodeverk")
                .queryParam("Kodeverk", "sedtyper")
                .build()

        val pingResult = restTemplateErrorhandler(
                {
                    euxOidcRestTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            null,
                            String::class.java)
                }
                , ""
                , PingEux
                , ""
        )
        return pingResult.statusCode == HttpStatus.OK
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

    fun <T> restTemplateErrorhandler(restTemplateFunction: () -> ResponseEntity<T>,
                                     euxCaseId: String,
                                     metric: MetricsHelper.Metric,
                                     prefixErrorMessage: String,
                                     waitTimes: Long = 1000L): ResponseEntity<T> {
        return metric.measure {
            return@measure try {
                val response = retryHelper( func = { restTemplateFunction.invoke() }, waitTimes = overrideWaitTimes ?: waitTimes)
                response
            } catch (hcee: HttpClientErrorException) {
                val errorBody = hcee.responseBodyAsString
                logger.error("$prefixErrorMessage, HttpClientError med euxCaseID: $euxCaseId, body: $errorBody", hcee)
                when (hcee.statusCode) {
                    HttpStatus.UNAUTHORIZED -> throw RinaIkkeAutorisertBrukerException("Authorization token required for Rina,")
                    HttpStatus.FORBIDDEN -> throw ForbiddenException("Forbidden, Ikke tilgang")
                    HttpStatus.NOT_FOUND -> throw IkkeFunnetException("Ikke funnet")
                    HttpStatus.CONFLICT -> throw EuxConflictException("En konflikt oppstod under kall til Rina")
                    else -> throw GenericUnprocessableEntity("Uoppdaget feil har oppstått!!, $errorBody")
                }
            } catch (hsee: HttpServerErrorException) {
                val errorBody = hsee.responseBodyAsString
                logger.error("$prefixErrorMessage, HttpServerError med euxCaseID: $euxCaseId, feilkode body: $errorBody", hsee)
                when (hsee.statusCode) {
                    HttpStatus.INTERNAL_SERVER_ERROR -> throw EuxRinaServerException("Rina serverfeil, kan også skyldes ugyldig input, $errorBody")
                    HttpStatus.GATEWAY_TIMEOUT -> throw GatewayTimeoutException("Venting på respons fra Rina resulterte i en timeout, $errorBody")
                    else -> throw GenericUnprocessableEntity("Uoppdaget feil har oppstått!!, $errorBody")
                }
            } catch (uhsce: UnknownHttpStatusCodeException) {
                val errorBody = uhsce.responseBodyAsString
                logger.error("$prefixErrorMessage, med euxCaseID: $euxCaseId errmessage: $errorBody", uhsce)
                throw GenericUnprocessableEntity("Ukjent statusefeil, $errorBody")
            } catch (ex: Exception) {
                logger.error("$prefixErrorMessage, med euxCaseID: $euxCaseId", ex)
                throw ServerException("Ukjent Feil oppstod euxCaseId: $euxCaseId,  ${ex.message}")
            }
        }
    }

}


//--- Disse er benyttet av restTemplateErrorhandler  -- start
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

@ResponseStatus(value = HttpStatus.CONFLICT)
class EuxConflictException(message: String?) : RuntimeException(message)

//--- Disse er benyttet av restTemplateErrorhandler  -- slutt


@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeOpprettetException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeLestException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class EuxGenericServerException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
class EuxServerException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class SedDokumentIkkeGyldigException(message: String?) : Exception(message)
