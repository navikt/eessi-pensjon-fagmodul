package no.nav.eessi.eessifagmodul.person.aktoerregister

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

data class Identinfo(
        val ident: String,
        val identgruppe: String,
        val gjeldende: Boolean
)

data class IdentinfoForAktoer(
        val identer: List<Identinfo>?,
        val feilmelding: String?
)

@Service
class AktoerregisterService(private val aktoerregisterOidcRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(AktoerregisterService::class.java)

    private val aktoerregister_teller_navn = "eessipensjon_fagmodul.aktoerregister"
    private val aktoerregister_teller_type_vellykkede = counter(aktoerregister_teller_navn, "vellykkede")
    private val aktoerregister_teller_type_feilede = counter(aktoerregister_teller_navn, "feilede")

    final fun counter(name: String, type: String): Counter {
        return Metrics.counter(name, "type", type)
    }

    @Value("\${app.name}")
    lateinit var appName: String

    fun hentGjeldendeNorskIdentForAktorId(aktorid: String): String {
        val response = doRequest(aktorid, "NorskIdent")
        validateResponse(aktorid, response)
        return response[aktorid]!!.identer!![0].ident
    }

    fun hentGjeldendeAktorIdForNorskIdent(norskIdent: String): String {
        val response = doRequest(norskIdent, "AktoerId")
        validateResponse(norskIdent, response)
        return response[norskIdent]!!.identer!![0].ident
    }

    private fun validateResponse(aktorid: String, response: Map<String, IdentinfoForAktoer>) {
        if (response[aktorid] == null)
            throw AktoerregisterIkkeFunnetException("Ingen identinfo for $aktorid ble funnet")

        val identInfoForAktoer = response[aktorid]!!

        if (identInfoForAktoer.feilmelding != null)
            throw AktoerregisterException(identInfoForAktoer.feilmelding)

        if (identInfoForAktoer.identer == null || identInfoForAktoer.identer.isEmpty())
            throw AktoerregisterIkkeFunnetException("Ingen identer returnert for $aktorid")

        if (identInfoForAktoer.identer.size > 1) {
            logger.info("Identer returnert fra api:")
            identInfoForAktoer.identer.forEach {
                logger.info("ident: ${it.ident}, gjeldende: ${it.gjeldende}, identgruppe: ${it.identgruppe}")
            }
            throw AktoerregisterException("Forventet 1 ident, fant ${identInfoForAktoer.identer.size}")
        }
    }

    private fun doRequest(ident: String,
                          identGruppe: String,
                          gjeldende: Boolean = true): Map<String, IdentinfoForAktoer> {
        val headers = HttpHeaders()
        headers["Nav-Personidenter"] = ident
        headers["Nav-Consumer-Id"] = appName
        headers["Nav-Call-Id"] = UUID.randomUUID().toString()
        val requestEntity = HttpEntity<String>(headers)

        val uriBuilder = UriComponentsBuilder.fromPath("/identer")
                .queryParam("identgruppe", identGruppe)
                .queryParam("gjeldende", gjeldende)
        logger.info("Kaller aktørregisteret: /identer")

        val responseEntity = aktoerregisterOidcRestTemplate.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                String::class.java)

        if (responseEntity.statusCode.isError) {
            logger.error("Fikk ${responseEntity.statusCode} feil fra aktørregisteret")
            aktoerregister_teller_type_feilede.increment()
            if (responseEntity.hasBody()) {
                logger.error(responseEntity.body.toString())
            }
            throw AktoerregisterException("Received ${responseEntity.statusCode} ${responseEntity.statusCode.reasonPhrase} from aktørregisteret")
        }
        aktoerregister_teller_type_vellykkede.increment()
        return jacksonObjectMapper().readValue(responseEntity.body!!)
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class AktoerregisterIkkeFunnetException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class AktoerregisterException(message: String) : Exception(message)