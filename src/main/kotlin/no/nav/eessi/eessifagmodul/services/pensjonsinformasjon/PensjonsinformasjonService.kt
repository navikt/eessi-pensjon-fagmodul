package no.nav.eessi.eessifagmodul.services.pensjonsinformasjon


import no.nav.eessi.eessifagmodul.models.IkkeFunnet
import no.nav.eessi.eessifagmodul.models.PensjoninformasjonException
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.DefaultUriBuilderFactory
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.io.StringReader
import javax.xml.bind.JAXBContext
import javax.xml.transform.stream.StreamSource


@Service
class PensjonsinformasjonService(val pensjonsinformasjonOidcRestTemplate: RestTemplate, val requestBuilder: RequestBuilder) {

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonService::class.java)

    @Value("\${FASIT_ENVIRONMENT_NAME}")
    lateinit var fasitenv: String

    lateinit var responseEntity: ResponseEntity<String?>

    val pesysq5url = "https://wasapp-q5.adeo.no/pensjon-ws/api/pensjonsinformasjon/v1"

    fun hentAltPaaSak(sakId: String = "", pendata: Pensjonsinformasjon): V1Sak? {
        logger.info("Pendata: $pendata")
        if (sakId.isNotBlank()) {
            pendata.brukersSakerListe.brukersSakerListe.forEach {
                if (sakId == it.sakId.toString())
                    return it
            }
        }
        return null
    }

    @Throws(IkkeFunnet::class)
    fun hentKunSakType(sakId: String, fnr: String): Pensjontype {
        val sak = hentAltPaaSak(sakId, hentAltPaaFnr(fnr)) ?: throw IkkeFunnet("Saktype ikke funnet")
        return Pensjontype(
                sakId,
                sak.sakType)
    }

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

    @Throws(PensjoninformasjonException::class)
    private fun doRequest(path: String, id: String, requestBody: String): Pensjonsinformasjon {
        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        val requestEntity = HttpEntity(requestBody, headers)

        val uriBuilder = UriComponentsBuilder.fromPath(path).pathSegment(id)

        try {
            try {
                //val responseEntity = pensjonsinformasjonOidcRestTemplate.exchange(
                responseEntity = pensjonsinformasjonOidcRestTemplate.exchange(
                        uriBuilder.toUriString(),
                        HttpMethod.POST,
                        requestEntity,
                        String::class.java)

            } catch (iox: IOException) {
                if (fasitenv == "q1") {
                    try {
                        logger.debug("Feiler mot PESYS, prøver å kontakte PESYS på : $pesysq5url")
                        responseEntity = pensjonInformasjonExtraQRestTemplateCall(uriBuilder, requestEntity, pesysq5url)
                    } catch (ex: Exception) {
                        logger.error("Feiler å kontakte PESYS backup", ex)
                        throw ex
                    }
                } else {
                    logger.error("Feiler ved IOfeil mot PESYS", iox)
                    throw iox
                }
            } catch (ise: HttpServerErrorException) {
                if (fasitenv == "q1") {
                    try {
                        logger.debug("Feiler mot PESYS, prøver å kontakte PESYS på : $pesysq5url")
                        responseEntity = pensjonInformasjonExtraQRestTemplateCall(uriBuilder, requestEntity, pesysq5url)
                    } catch (ex: Exception) {
                        logger.error("Feiler å kontakte PESYS backup", ex)
                        throw ex
                    }
                } else {
                    logger.error("Feiler ved Serverfeil mot PESYS", ise)
                    throw ise
                }
            }

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

        } catch (ex: Exception) {
            logger.error("Feil med kontakt til PESYS pensjoninformajson, ${ex.message}")
            throw PensjoninformasjonException("Feil med kontakt til PESYS pensjoninformajson. melding; ${ex.message}")
        }

    }

    fun pensjonInformasjonExtraQRestTemplateCall(uriBuilder: UriComponentsBuilder, requestEntity: HttpEntity<String>, pesysurl: String): ResponseEntity<String?> {
        pensjonsinformasjonOidcRestTemplate.uriTemplateHandler = PensjoninformasjonUriHandler(pesysurl)
        return pensjonsinformasjonOidcRestTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.POST,
                requestEntity,
                String::class.java)
    }

}

class PensjoninformasjonUriHandler(baseUriTemplate: String) : DefaultUriBuilderFactory(baseUriTemplate)

