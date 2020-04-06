package no.nav.eessi.pensjon.services.pensjonsinformasjon


import com.google.common.base.Preconditions
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.io.StringReader
import javax.xml.bind.JAXBContext
import javax.xml.transform.stream.StreamSource


@Component
class PensjonsinformasjonClient(
        private val pensjonsinformasjonOidcRestTemplate: RestTemplate,
        private val requestBuilder: RequestBuilder,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    companion object {
        private val logger = LoggerFactory.getLogger(PensjonsinformasjonClient::class.java)

        fun finnSak(sakId: String, pendata: Pensjonsinformasjon): V1Sak? {

            logger.debug("Søker brukersSakerListe etter sakId: $sakId")

            val v1saklist = pendata.brukersSakerListe.brukersSakerListe

            v1saklist.forEach {
                logger.debug("Itererer brukersakliste sakType: ${it.sakType} sakid: ${it.sakId}")
                if (sakId == it.sakId.toString()) {
                    logger.debug("Fant sakid på brukersakliste, returnerer kun V1Sak på sakid: ${it.sakId}\"")
                    return it
                }
            }
            logger.warn("Fant ingen sakid på brukersakliste, returnerer null")
            return null
        }
    }
    @Throws(IkkeFunnetException::class)
    fun hentKunSakType(sakId: String, aktoerid: String): Pensjontype {
        return metricsHelper.measure(MetricsHelper.MeterName.PensjoninformasjonHentKunSakType) {
            return@measure try {
                val sak = finnSak(sakId, hentAltPaaAktoerId(aktoerid)) ?: throw IkkeFunnetException("Sak ikke funnet")
                Pensjontype(sakId, sak.sakType)
            } catch (ex: Exception) {
                logger.warn("Saktype ikke funnet, mangler kravhode, ${ex.message}", ex)
                throw IkkeFunnetException("Saktype ikke funnet")
            }
        }
    }

    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun hentAltPaaAktoerId(aktoerId: String): Pensjonsinformasjon {
        Preconditions.checkArgument(aktoerId.isNotBlank(), "AktoerId kan ikke være blank/tom")

        //APIet skal ha urlen {host}:{port}/pensjon-ws/api/pensjonsinformasjon/v1/{ressurs}?sakId=123+fom=2018-01-01+tom=2018-28-02.

        return metricsHelper.measure(MetricsHelper.MeterName.PensjoninformasjonHentAltPaaIdent) {
            val informationBlocks = listOf(
                    InformasjonsType.BRUKERS_SAKER_LISTE
            )
            val document = requestBuilder.getBaseRequestDocument()

            informationBlocks.forEach {
                requestBuilder.addPensjonsinformasjonElement(document, it)
            }

            logger.debug("Requestbody:\n${document.documentToString()}")
            logger.info("Henter pensjonsinformasjon for aktor: $aktoerId")

            val xmlResponse = doRequest("/aktor/", aktoerId, document.documentToString(), MetricsHelper.MeterName.PensjoninformasjonHentAltPaaIdentRequester)
            transform(xmlResponse)
        }
    }


    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun hentAltPaaVedtak(vedtaksId: String): Pensjonsinformasjon {

        return metricsHelper.measure(MetricsHelper.MeterName.PensjoninformasjonAltPaaVedtak) {

            val informationBlocks = listOf(
                    InformasjonsType.AVDOD,
                    InformasjonsType.INNGANG_OG_EXPORT,
                    InformasjonsType.PERSON,
                    InformasjonsType.SAKALDER,
                    InformasjonsType.TRYGDEAVTALE,
                    InformasjonsType.TRYGDETID_AVDOD_FAR_LISTE,
                    InformasjonsType.TRYGDETID_AVDOD_LISTE,
                    InformasjonsType.TRYGDETID_AVDOD_MOR_LISTE,
                    InformasjonsType.TRYGDETID_LISTE,
                    InformasjonsType.VEDTAK,
                    InformasjonsType.VILKARSVURDERING_LISTE,
                    InformasjonsType.YTELSE_PR_MAANED_LISTE
            )

            val document = requestBuilder.getBaseRequestDocument()

            informationBlocks.forEach {
                requestBuilder.addPensjonsinformasjonElement(document, it)
            }
            logger.info("Henter pensjonsinformasjon for vedtaksid: $vedtaksId")
            logger.debug("Requestbody:\n${document.documentToString()}")

            val xmlResponse = doRequest("/vedtak", vedtaksId, document.documentToString(), MetricsHelper.MeterName.PensjoninformasjonAltPaaVedtakRequester)
            transform(xmlResponse)
        }
    }

    //transform xmlString til Pensjoninformasjon object
    fun transform(xmlString: String) : Pensjonsinformasjon {
        return try {

            val context = JAXBContext.newInstance(Pensjonsinformasjon::class.java)
            val unmarshaller = context.createUnmarshaller()

            logger.debug("Pensjonsinformasjon xml: $xmlString")
            val res = unmarshaller.unmarshal(StreamSource(StringReader(xmlString)), Pensjonsinformasjon::class.java)

            res.value as Pensjonsinformasjon

        } catch (ex: Exception) {
            logger.error("Feiler med xml transformering til Pensjoninformasjon")
            throw PensjoninformasjonProcessingException("Feiler med xml transformering til Pensjoninformasjon: ${ex.message}")
        }
    }

    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    private fun doRequest(path: String, id: String, requestBody: String, metricName: MetricsHelper.MeterName): String {

        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        val requestEntity = HttpEntity(requestBody, headers)

        val uriBuilder = UriComponentsBuilder.fromPath(path).pathSegment(id)

        return metricsHelper.measure(metricName) {
             return@measure try {
                val responseEntity = pensjonsinformasjonOidcRestTemplate.exchange(
                            uriBuilder.toUriString(),
                            HttpMethod.POST,
                            requestEntity,
                            String::class.java)

                 responseEntity.body!!

            } catch (hsee: HttpServerErrorException) {
                val errorBody = hsee.responseBodyAsString
                logger.error("PensjoninformasjonService feiler med HttpServerError body: $errorBody", hsee)
                throw hsee
            } catch (hcee: HttpClientErrorException) {
                val errorBody = hcee.responseBodyAsString
                logger.error("PensjoninformasjonService feiler med HttpClientError body: $errorBody", hcee)
                throw hcee
            } catch (ex: Exception) {
                logger.error("PensjoninformasjonService feiler med kontakt til PESYS pensjoninformajson, ${ex.message}", ex)
                throw PensjoninformasjonException("PensjoninformasjonService feiler med ukjent feil mot PESYS. melding: ${ex.message}")
            }
        }
    }

    //selftest
    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun doPing(): Boolean {
        val uriBuilder = UriComponentsBuilder.fromPath("/ping")
        try {
            val responseEntity = pensjonsinformasjonOidcRestTemplate.exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)

            if (responseEntity.statusCode.is2xxSuccessful) {
                val response = responseEntity.body
                return response == "Service online!"
            } else {
                logger.error("Received ${responseEntity.statusCode} from pensjonsinformasjon")
                throw PensjoninformasjonException("Received feil-ping from pensjonsinformasjon")
            }
        } catch (se: HttpServerErrorException) {
            logger.error("Feiler ved Serverfeil mot PESYS", se)
            throw se
        } catch (ce: HttpClientErrorException) {
            logger.error("Feiler ved Clientfeil mot PESYS", ce)
            throw ce
        } catch (ex: Exception) {
            logger.error("Feil med kontakt til PESYS pensjoninformajson, ${ex.message}")
            throw PensjoninformasjonException("Feil med kontakt til PESYS pensjoninformajson. melding; ${ex.message}")
        }

    }


}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class IkkeFunnetException(message: String) : IllegalArgumentException(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class PensjoninformasjonException(message: String) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
class PensjoninformasjonProcessingException(message: String) : RuntimeException(message)
