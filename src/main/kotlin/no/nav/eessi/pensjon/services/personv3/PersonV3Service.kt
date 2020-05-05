package no.nav.eessi.pensjon.services.personv3

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.security.sts.configureRequestSamlToken
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Informasjonsbehov
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import javax.annotation.PostConstruct

@Component
class PersonV3Service(private val service: PersonV3,
                      private val auditLogger: AuditLogger,
                      @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonV3Service::class.java) }

    private lateinit var HentPersonV3: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        HentPersonV3 = metricsHelper.init("HentPersonV3")
    }

    final fun counter(name: String, type: String): Counter {
        return Metrics.counter(name, "type", type)
    }

    fun hentPersonPing(): Boolean {
        logger.info("Ping PersonV3Service")
        configureRequestSamlToken(service)
        return try {
            service.ping()
            true
        } catch (ex: Exception) {
            logger.error("Får ikke kontakt med tjeneste PersonV3 $ex")
            throw ex
        }
    }

    @Throws(PersonV3IkkeFunnetException::class, PersonV3SikkerhetsbegrensningException::class)
    fun hentPerson(fnr: String): HentPersonResponse {
        auditLogger.logBorger("PersonV3Service.hentPerson", fnr)
        logger.info("Henter person fra PersonV3Service")
        configureRequestSamlToken(service)

        val request = HentPersonRequest().apply {
            withAktoer(PersonIdent().withIdent(
                    NorskIdent().withIdent(fnr)))

            withInformasjonsbehov(listOf(
                    Informasjonsbehov.ADRESSE,
                    Informasjonsbehov.FAMILIERELASJONER
            ))
        }

        return HentPersonV3.measure {
            return@measure try {
                logger.info("Kaller PersonV3.hentPerson service")
                val resp = service.hentPerson(request)
                resp
            } catch (personIkkefunnet: HentPersonPersonIkkeFunnet) {
                logger.error("Kaller PersonV3.hentPerson service Feilet: $personIkkefunnet")
                throw PersonV3IkkeFunnetException(personIkkefunnet.message!!)
            } catch (personSikkerhetsbegrensning: HentPersonSikkerhetsbegrensning) {
                auditLogger.logBorgerErr("PersonV3.hentPerson", fnr, personSikkerhetsbegrensning.message!!)
                logger.error("Kaller PersonV3.hentPerson service Feilet $personSikkerhetsbegrensning")
                throw PersonV3SikkerhetsbegrensningException(personSikkerhetsbegrensning.message!!)
            } catch (ex: Exception) {
                logger.error("Ukejnt feil i PersonV3, ${ex.message}", ex)
                throw PersonV3IkkeFunnetException("Ukent feil ved PersonV3")
            }
        }
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class PersonV3IkkeFunnetException(message: String) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.FORBIDDEN)
class PersonV3SikkerhetsbegrensningException(message: String) : RuntimeException(message)
