package no.nav.eessi.pensjon.services.pensjonsinformasjon


import com.google.common.base.Preconditions
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.io.StringReader
import javax.annotation.PostConstruct
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
            logger.info("Søker brukersSakerListe etter sakId: $sakId")
            val v1saklist = pendata.brukersSakerListe.brukersSakerListe

            return v1saklist.firstOrNull { sak -> "${sak.sakId}" == sakId  }
        }
    }

    private lateinit var pensjoninformasjonHentKunSakType: MetricsHelper.Metric
    private lateinit var pensjoninformasjonHentAltPaaIdent: MetricsHelper.Metric
    private lateinit var pensjoninformasjonAltPaaVedtak: MetricsHelper.Metric
    private lateinit var pensjoninformasjonHentAltPaaIdentRequester: MetricsHelper.Metric
    private lateinit var pensjoninformasjonAltPaaVedtakRequester: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        pensjoninformasjonHentKunSakType = metricsHelper.init("PensjoninformasjonHentKunSakType")
        pensjoninformasjonHentAltPaaIdent = metricsHelper.init("PensjoninformasjonHentAltPaaIdent")
        pensjoninformasjonAltPaaVedtak = metricsHelper.init("PensjoninformasjonAltPaaVedtak")
        pensjoninformasjonHentAltPaaIdentRequester = metricsHelper.init("PensjoninformasjonHentAltPaaIdentRequester")
        pensjoninformasjonAltPaaVedtakRequester = metricsHelper.init("PensjoninformasjonAltPaaVedtakRequester")
    }

    @Throws(IkkeFunnetException::class)
    fun hentKunSakType(sakId: String, aktoerid: String): Pensjontype {
        return pensjoninformasjonHentKunSakType.measure {
            val sak = finnSak(sakId, hentAltPaaAktoerId(aktoerid)) ?: throw IkkeFunnetException("Saktype for $sakId ikke funnet")
            Pensjontype(sakId, sak.sakType)
        }
    }

    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun hentAltPaaAktoerId(aktoerId: String): Pensjonsinformasjon {
        Preconditions.checkArgument(aktoerId.isNotBlank(), "AktoerId kan ikke være blank/tom")

        //APIet skal ha urlen {host}:{port}/pensjon-ws/api/pensjonsinformasjon/v1/{ressurs}?sakId=123+fom=2018-01-01+tom=2018-28-02.

        return pensjoninformasjonHentAltPaaIdent.measure {

            val requestBody = requestBuilder.requestBodyForSakslisteFromAString()

            logger.debug("Requestbody:\n$requestBody")
            logger.info("Henter pensjonsinformasjon for aktor: $aktoerId")

            val xmlResponse = doRequest("/aktor/", aktoerId, requestBody, pensjoninformasjonHentAltPaaIdentRequester)
            transform(xmlResponse)
        }
    }



    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun hentAltPaaVedtak(vedtaksId: String): Pensjonsinformasjon {

        return pensjoninformasjonAltPaaVedtak.measure {

            val requestBody = requestBuilder.requestBodyForVedtakFromAString()
            logger.debug("Requestbody:\n$requestBody")
            logger.info("Henter pensjonsinformasjon for vedtaksid: $vedtaksId")

            val xmlResponse = doRequest("/vedtak", vedtaksId, requestBody, pensjoninformasjonAltPaaVedtakRequester)
            transform(xmlResponse)
        }
    }


    fun hentKravDato(aktorId: String, sakId: String) : String? {
        val pendata = hentAltPaaAktoerId(aktorId)
        if (pendata.brukersSakerListe == null) {
            logger.warn("Ingen gyldig brukerSakerListe funnet")
            throw PensjoninformasjonException("Ingen gyldig brukerSakerListe, mangler data fra pesys")
        }

        val sak = finnSak(sakId, pendata) ?: return null

        val v1KravHistorikk = KravHistorikkHelper.hentKravhistorikkForGjenlevende(sak.kravHistorikkListe)
            ?: when (sak.status) {
                Kravstatus.TIL_BEHANDLING.name -> KravHistorikkHelper.hentKravHistorikkMedKravStatusTilBehandling(
                    sak.kravHistorikkListe
                )
                Kravstatus.AVSL.name -> KravHistorikkHelper.hentKravHistorikkMedKravStatusAvslag(sak.kravHistorikkListe)
                else -> KravHistorikkHelper.hentKravHistorikkForsteGangsBehandlingUtlandEllerForsteGang(
                    sak.kravHistorikkListe,
                    sak.sakType
                )
            }
        return v1KravHistorikk.mottattDato?.simpleFormat()
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
    private fun doRequest(path: String, id: String, requestBody: String, metric: MetricsHelper.Metric): String {

        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        val requestEntity = HttpEntity(requestBody, headers)

        val uriBuilder = UriComponentsBuilder.fromPath(path).pathSegment(id)

        return metric.measure {
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

class IngenSakFunnetException(reason: String): ResponseStatusException(HttpStatus.NOT_FOUND, reason)

class IkkeFunnetException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)

class PensjoninformasjonException(message: String) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)

class PensjoninformasjonProcessingException(message: String) : ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message)
