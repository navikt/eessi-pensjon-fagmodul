package no.nav.eessi.pensjon.services.pensjonsinformasjon

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Service
class PesysService(
    private val pesysClientRestTemplate: RestTemplate
) {

    private val logger: Logger = LoggerFactory.getLogger(PesysService::class.java)

    fun hentAvdod(vedtakId: String?): EessiFellesDto.EessiAvdodDto? =
        getWithHeaders(
            "/vedtak/{vedtakId}/avdoed",
            "vedtakId" to vedtakId,
        )

    fun hentKravdato(kravId: String?): LocalDate? =
        getWithHeaders(
            "/krav/{kravId}/mottattDato",
            "kravId" to kravId,
        )

    fun hentSaktype(sakId: String?): EessiFellesDto.EessiSakType? =
        getWithHeaders(
            "/sak/{sakId}/saktype",
            "sakId" to sakId,
        )

    fun hentSakListe(fnr: String?): List<EessiFellesDto.PensjonSakDto> =
            getWithHeaders(
                "/bruker/sakliste",
                "fnr" to fnr,
            ) ?: emptyList()

    fun hentUfoeretidspunktOnVedtak(vedtakId: String?): EessiFellesDto.EessiUfoeretidspunktDto? =
        getWithHeaders("/vedtak/$vedtakId/ufoeretidspunkt")

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

    fun hentGyldigAvdod(avdod: EessiFellesDto.EessiAvdodDto?) : List<String>? {
        val avdodMor = avdod?.avdodMor
        val avdodFar = avdod?.avdodFar
        val annenAvdod = avdod?.avdod

        return when {
            annenAvdod != null && avdodFar == null && avdodMor == null -> listOf(annenAvdod)
            annenAvdod == null && avdodFar != null && avdodMor == null -> listOf(avdodFar)
            annenAvdod == null && avdodFar == null && avdodMor != null -> listOf(avdodMor)
            annenAvdod == null && avdodFar != null && avdodMor != null -> listOf(avdodFar, avdodMor)
            annenAvdod == null && avdodFar == null && avdodMor == null -> null
            else -> {
                logger.error("Ukjent feil ved henting av buc detaljer for gjenlevende")
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Ukjent feil ved henting av buc detaljer for gjenlevende")
            }
        }
    }
}

