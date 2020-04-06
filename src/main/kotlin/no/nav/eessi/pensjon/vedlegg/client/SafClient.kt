package no.nav.eessi.pensjon.vedlegg.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class SafClient(private val safGraphQlOidcRestTemplate: RestTemplate,
                private val safRestOidcRestTemplate: RestTemplate,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(SafClient::class.java)
    private val mapper = jacksonObjectMapper()
    private final val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"


    // Vi trenger denne konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(RestTemplate(), RestTemplate())

    fun hentDokumentMetadata(aktoerId: String) : HentMetadataResponse {
        logger.info("Henter dokument metadata for aktørid: $aktoerId")

        return metricsHelper.measure(MetricsHelper.MeterName.HentDokumentMetadata) {
            try {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val httpEntity = HttpEntity(genererQuery(aktoerId), headers)
                val response = safGraphQlOidcRestTemplate.exchange("/",
                        HttpMethod.POST,
                        httpEntity,
                        String::class.java)

            val mappedResponse = mapper.readValue(response.body!!, HentMetadataResponse::class.java)
            mappedResponse

            } catch (ce: HttpClientErrorException) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: ${ce.responseBodyAsString}")
                throw SafException("En feil oppstod under henting av dokument metadata fra SAF: ${ce.responseBodyAsString}", ce.statusCode)
            } catch (se: HttpServerErrorException) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: ${se.responseBodyAsString}", se)
                throw SafException("En feil oppstod under henting av dokument metadata fra SAF: ${se.responseBodyAsString}", se.statusCode)
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: $ex")
                throw SafException("En feil oppstod under henting av dokument metadata fra SAF: $ex", HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
    }

    fun hentDokumentInnhold(journalpostId: String,
                            dokumentInfoId: String,
                            variantFormat: VariantFormat) : HentdokumentInnholdResponse {

        return metricsHelper.measure(MetricsHelper.MeterName.HentDokumentInnhold) {
            try {
                logger.info("Henter dokumentinnhold for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId, variantformat: $variantFormat")
                val path = "/$journalpostId/$dokumentInfoId/$variantFormat"
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
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: ${ce.responseBodyAsString}", ce)
                throw SafException("En feil oppstod under henting av dokumentInnhold fra SAF: ${ce.responseBodyAsString}", ce.statusCode)
            } catch (se: HttpServerErrorException) {
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: ${se.responseBodyAsString}", se)
                throw SafException("En feil oppstod under henting av dokumentInnhold fra SAF: ${se.responseBodyAsString}", se.statusCode)
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
                throw SafException("En feil oppstod under henting av dokumentinnhold fra SAF", HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
    }

    private fun genererQuery(aktoerId: String): String {
        val request = SafRequest(variables = Variables(BrukerId(aktoerId, BrukerIdType.AKTOERID), 10000))
        return request.toJson()
    }

    /**
     * Returnerer en distinct liste av rinaSakIDer basert på tilleggsinformasjon i journalposter for en aktør
     * @param metadata journalpostmetadata fra JOARK datamodellen
     */
    fun hentRinaSakIderFraDokumentMetadata(aktoerId: String): List<String> {
        return metricsHelper.measure(MetricsHelper.MeterName.HentRinaSakIderFraDokumentMetadata) {
            val metadata = hentDokumentMetadata(aktoerId)
            val rinaSakIder = mutableListOf<String>()
            metadata.data.dokumentoversiktBruker.journalposter.forEach { journalpost ->
                journalpost.tilleggsopplysninger.forEach { tilleggsopplysning ->
                    if (tilleggsopplysning["nokkel"].equals(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY)) {
                        rinaSakIder.add(tilleggsopplysning["verdi"].toString())
                    }
                }
            }
            rinaSakIder.distinct()
        }
    }
}


