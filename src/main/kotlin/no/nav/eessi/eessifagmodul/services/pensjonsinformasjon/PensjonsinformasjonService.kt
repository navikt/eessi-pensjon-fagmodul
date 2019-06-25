package no.nav.eessi.eessifagmodul.services.pensjonsinformasjon


import com.google.common.base.Preconditions
import no.nav.eessi.eessifagmodul.models.IkkeFunnetException
import no.nav.eessi.eessifagmodul.models.PensjoninformasjonException
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.aspectj.weaver.tools.cache.SimpleCacheFactory.path
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.io.StringReader
import javax.xml.bind.JAXBContext
import javax.xml.transform.stream.StreamSource
import kotlin.reflect.jvm.internal.impl.types.checker.TypeCheckerContext


@Service
class PensjonsinformasjonService(val pensjonsinformasjonOidcRestTemplate: RestTemplate, val requestBuilder: RequestBuilder) {

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonService::class.java)

    fun hentAltPaaSak(sakId: String, pendata: Pensjonsinformasjon): V1Sak? {
        logger.debug("Søker brukersSakerListe etter sakId: $sakId")

        val v1saklist = pendata.brukersSakerListe.brukersSakerListe

        v1saklist.forEach {
            logger.debug("Itererer brukersakliste sakType: ${it.sakType} sakid: ${it.sakId}")
            if (sakId.equals(it.sakId.toString())) {
                logger.debug("Fant sakid på brukersakliste, returnerer kun V1Sak på sakid: ${it.sakId}\"")
                    return it
            }
        }
        logger.warn("Fant ingen sakid på brukersakliste, returnerer null")
        return null
    }

    @Throws(IkkeFunnetException::class)
    fun hentKunSakType(sakId: String, fnr: String): Pensjontype {

        try {
            val sak = hentAltPaaSak(sakId, hentAltPaaFnr(fnr)) ?: throw IkkeFunnetException("Saktype ikke funnet")

            return Pensjontype(
                    sakId,
                    sak.sakType)

        } catch (ex: Exception) {
            logger.warn("Saktype ikke funnet, mangler kravhode, ${ex.message}", ex)
            throw IkkeFunnetException("Saktype ikke funnet")
        }

    }

    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun hentAltPaaFnr(fnr: String): Pensjonsinformasjon {
        //APIet skal ha urlen {host}:{port}/pensjon-ws/api/pensjonsinformasjon/v1/{ressurs}?sakId=123+fom=2018-01-01+tom=2018-28-02.

        val informationBlocks = listOf(
                InformasjonsType.BRUKERS_SAKER_LISTE
        )
        val document = requestBuilder.getBaseRequestDocument()

        informationBlocks.forEach {
            requestBuilder.addPensjonsinformasjonElement(document, it)
        }

        logger.info("Requestbody:\n${document.documentToString()}")

        val response = doRequest("/fnr/", fnr, document.documentToString())
        validateResponse(informationBlocks, response)
        logger.info("Response: $response")
        return response
    }

    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun hentAltPaaAktoerId(aktoerId: String): Pensjonsinformasjon {
        Preconditions.checkArgument(aktoerId.isNotBlank(), "AktoerId kan ikke være blank/tom")
        //APIet skal ha urlen {host}:{port}/pensjon-ws/api/pensjonsinformasjon/v1/{ressurs}?sakId=123+fom=2018-01-01+tom=2018-28-02.

        val informationBlocks = listOf(
                InformasjonsType.BRUKERS_SAKER_LISTE
        )
        val document = requestBuilder.getBaseRequestDocument()

        informationBlocks.forEach {
            requestBuilder.addPensjonsinformasjonElement(document, it)
        }

        logger.info("Requestbody:\n${document.documentToString()}")

        val response = doRequest("/aktor/", aktoerId, document.documentToString())
        validateResponse(informationBlocks, response)
        logger.info("Response: $response")
        return response
    }



    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    fun hentAltPaaVedtak(vedtaksId: String): Pensjonsinformasjon {

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
        logger.info("Requestbody:\n${document.documentToString()}")

        val response = doRequest("/vedtak", vedtaksId, document.documentToString())
        validateResponse(informationBlocks, response)
        logger.info("Response: $response")
        return response
    }

    private fun validateResponse(informationBlocks: List<InformasjonsType>, response: Pensjonsinformasjon) {
        // TODO: Hva skal vi egentlig validere? Skal vi validere noe mer enn at vi fikk en gyldig xml-response, som skjer ved JAXB-marshalling?
    }

    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
    private fun doRequest(path: String, id: String, requestBody: String): Pensjonsinformasjon {

        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        val requestEntity = HttpEntity(requestBody, headers)

        val uriBuilder = UriComponentsBuilder.fromPath(path).pathSegment(id)

        try {
                //val responseEntity = pensjonsinformasjonOidcRestTemplate.exchange(
            val responseEntity = pensjonsinformasjonOidcRestTemplate.exchange(
                        uriBuilder.toUriString(),
                        HttpMethod.POST,
                        requestEntity,
                        String::class.java)

            if (responseEntity.statusCode.isError) {
                logger.error("Received ${responseEntity.statusCode} from pensjonsinformasjon")
                if (responseEntity.hasBody()) {
                    logger.error(responseEntity.body.toString())
                }
                //throw RuntimeException("Received ${responseEntity.statusCode} ${responseEntity.statusCode.reasonPhrase} from pensjonsinformasjon")
                throw PensjoninformasjonException("Received ${responseEntity.statusCode} ${responseEntity.statusCode.reasonPhrase} from pensjonsinformasjon")
            }

            val context = JAXBContext.newInstance(Pensjonsinformasjon::class.java)
            val unmarshaller = context.createUnmarshaller()

            val res = unmarshaller.unmarshal(StreamSource(StringReader(responseEntity.body)), Pensjonsinformasjon::class.java)

            return res.value as Pensjonsinformasjon

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

