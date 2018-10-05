package no.nav.eessi.eessifagmodul.services.pensjonsinformasjon


import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.io.StringReader
import javax.xml.bind.JAXBContext
import javax.xml.transform.stream.StreamSource

private val logger = LoggerFactory.getLogger(PensjonsinformasjonService::class.java)

@Service
class PensjonsinformasjonService(val pensjonsinformasjonOidcRestTemplate: RestTemplate, val requestBuilder: RequestBuilder) {

    fun henPersonInformasjonFraVedtak(vedtaksId: String): Pensjonsinformasjon {
        return henPersonInformasjon("/vedtak", vedtaksId)
    }
    fun henPersonInformasjonFraSak(saksnummer: String): Pensjonsinformasjon {
        return henPersonInformasjon("/sak", saksnummer)
    }

    private fun henPersonInformasjon(path: String, id: String): Pensjonsinformasjon {
        val informationBlocks = listOf(
                InformasjonsType.PERSON
        )
        val document = requestBuilder.getBaseRequestDocument()

        informationBlocks.forEach {
            requestBuilder.addPensjonsinformasjonElement(document, it)
        }
        logger.debug("Requestbody:\n${document.documentToString()}")
        val response = doRequest(path, id, document.documentToString())
        validateResponse(informationBlocks, response)
        return response

    }

    fun hentAlt(vedtaksId: String): Pensjonsinformasjon {

        val informationBlocks = listOf(
                InformasjonsType.AVDOD,
                InformasjonsType.INNGANG_OG_EXPORT,
                InformasjonsType.PERSON,
                InformasjonsType.SAK,
                InformasjonsType.TRYGDEAVTALE,
                InformasjonsType.TRYGDETID_AVDOD_FAR_LISTE,
                InformasjonsType.TRYGDETID_AVDOD_LISTE,
                InformasjonsType.TRYGDETID_AVDOD_MOR_LISTE,
                InformasjonsType.TRYGDETID_LISTE,
                InformasjonsType.VEDTAK,
//                InformasjonsType.VILKARSVURDERING_LISTE, // TODO: Ta med denne når pensjon-fss har implementert
                InformasjonsType.YTELSE_PR_MAANED_LISTE)

        val document = requestBuilder.getBaseRequestDocument()

        informationBlocks.forEach {
            requestBuilder.addPensjonsinformasjonElement(document, it)
        }

        logger.debug("Requestbody:\n${document.documentToString()}")
        val response = doRequest("/vedtak", vedtaksId, document.documentToString())
        validateResponse(informationBlocks, response)
        return response
    }

    private fun validateResponse(informationBlocks: List<InformasjonsType>, response: Pensjonsinformasjon) {
        // TODO: Hva skal vi egentlig validere? Skal vi validere noe mer enn at vi fikk en gyldig xml-response, som skjer ved JAXB-marshalling?
    }

    private fun doRequest(path: String, id: String, requestBody: String): Pensjonsinformasjon {
        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        val requestEntity = HttpEntity(requestBody, headers)

        val uriBuilder = UriComponentsBuilder.fromPath(path).pathSegment(id)

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
            throw RuntimeException("Received ${responseEntity.statusCode} ${responseEntity.statusCode.reasonPhrase} from pensjonsinformasjon")
        }
        logger.debug("Responsebody:\n${responseEntity.body}")

        val context = JAXBContext.newInstance(Pensjonsinformasjon::class.java)
        val unmarshaller = context.createUnmarshaller()

        val res = unmarshaller.unmarshal(StreamSource(StringReader(responseEntity.body)), Pensjonsinformasjon::class.java)
        return res.value as Pensjonsinformasjon
    }
}