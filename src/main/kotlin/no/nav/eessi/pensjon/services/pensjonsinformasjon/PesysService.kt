package no.nav.eessi.pensjon.services.pensjonsinformasjon

import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiFellesDto.EessiAvdodDto
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EessiFellesDto.EessiUfoeretidspunktDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import kotlin.collections.firstOrNull

@Service
class PesysService(
    private val pesysClientRestTemplate: RestTemplate
) {

    private val logger: Logger = LoggerFactory.getLogger(PesysService::class.java)

    fun hentAvdod(vedtakId: String?): EessiAvdodDto? =
        getWithHeaders<EessiAvdodDto>(
            "/vedtak/$vedtakId/avdoed"
        ).also { logger.debug("Henter avdod: $it") }

    fun hentKravdato(kravId: String?): LocalDate? =
        getWithHeaders(
            "/krav/$kravId/mottattDato"
        )

    fun hentSaktype(sakId: String?): EessiFellesDto.EessiSakType? =
        getWithHeaders(
            "/sak/$sakId/saktype",
        )

    fun hentSakListe(fnr: String?): List<EessiPensjonSak> {
        val response = getWithHeaders<Any>(
            "/bruker/sakliste",
            "fnr" to fnr,
        ) ?: return emptyList()
        logger.info("Henter sakliste: $response")

        return when (response) {
            is List<*> -> response.mapNotNull {
                when (it) {
                    is EessiPensjonSak -> it
                    is Map<*, *> -> ObjectMapper().convertValue(it, EessiPensjonSak::class.java)
                    else -> null
                }
            }
            else -> emptyList()
        }.also { logger.info("HentSakListe: $it") }
    }

    fun hentUfoeretidspunktOnVedtak(sakId: String?): EessiUfoeretidspunktDto? =
        getWithHeaders("/vedtak/$sakId/ufoeretidspunkt")


    fun List<EessiUfoeretidspunktDto>.sortUfore(): List<EessiUfoeretidspunktDto> =
        sortedWith(
            compareBy<EessiUfoeretidspunktDto> { it.uforetidspunkt == null }
                .thenBy { it.uforetidspunkt }
                .thenBy { it.virkningstidspunkt }
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

        logger.info("Henter pesys informasjon for: $path ")

        val entity = HttpEntity<Void>(httpHeaders)
        return pesysClientRestTemplate
            .exchange(path, HttpMethod.GET, entity, T::class.java).body
            .also { logger.debug("Pesys response: $it") }
    }

    fun hentGyldigAvdod(avdod: EessiAvdodDto?) : List<String>? {
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

