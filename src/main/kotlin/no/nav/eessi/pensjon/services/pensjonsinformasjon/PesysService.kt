package no.nav.eessi.pensjon.services.pensjonsinformasjon

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class PesysService(
    private val pesysClientRestTemplate: RestTemplate
) {

    private val logger: Logger = LoggerFactory.getLogger(PesysService::class.java)

    fun hentAvdod(vedtakId: String?): EessiAvdodDto? =
        getWithHeaders("/vedtak/{vedtakId}/avdoed",
            "vedtakId" to vedtakId,
        )

    private inline fun <reified T : Any> getWithHeaders(
        path: String,
        vararg headers: Pair<String, String?>
    ): T? {
        val httpHeaders = HttpHeaders().apply {
            headers
                .filter { !it.second.isNullOrBlank() }
                .forEach { (k, v) -> set(k, v) }
        }

        logger.debug("Henter pesys informasjon fra: $path (headers=${httpHeaders})")

        val entity = HttpEntity<Void>(httpHeaders)
        return pesysClientRestTemplate.exchange(path, HttpMethod.GET, entity, T::class.java).body
    }
}

