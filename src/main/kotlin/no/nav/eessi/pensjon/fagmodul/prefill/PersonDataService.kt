package no.nav.eessi.pensjon.fagmodul.prefill

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P2000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P2001
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P2100
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P2200
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P6000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.valueOf
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstand
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class PersonDataService(private val personService: PersonService,
                        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger: Logger = LoggerFactory.getLogger(PersonDataService::class.java)

    private lateinit var HentPerson: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        HentPerson = metricsHelper.init("HentPerson")
    }

    fun <T : IdentType, R : IdentType> hentIdent(identTypeWanted: R, ident: Ident<T>): Ident<R> {
        return personService.hentIdent(identTypeWanted, ident)
    }

    fun hentPersonData(prefillData: PrefillDataModel) : PersonDataCollection {
        val sedType = valueOf(prefillData.sedType)

        return when (sedType) {
            //alle med barn
            P2001, P2000, P2200, P2100, P6000 -> hentPersonerMedBarn(prefillData)
            //alle uten barn
            else -> hentPersoner(prefillData)
        }
    }

    private fun hentPersonerMedBarn(prefillData: PrefillDataModel) = hentPersoner(prefillData, true)

    //Henter inn alle personer fra ep-personoppslag  først før preutfylling
    private fun hentPersoner(prefillData: PrefillDataModel, fyllUtBarnListe: Boolean = false): PersonDataCollection {
        return HentPerson.measure {
            logger.info("Henter hovedperson/forsikret/gjenlevende")
            val forsikretPerson = personService.hentPerson(NorskIdent(prefillData.bruker.norskIdent))

            val gjenlevendeEllerAvdod = if (prefillData.avdod != null) {
                logger.info("Henter avød person/forsikret")
                //personV3Service.hentBruker(prefillData.avdod.norskIdent)
                personService.hentPerson(NorskIdent(prefillData.avdod.norskIdent))
            } else {
                logger.info("Ingen avdød så settes til forsikretPerson")
                forsikretPerson
            }

            val sivilstand = filterEktefelleRelasjon(forsikretPerson)
            val sivilstandType = sivilstand?.type
            logger.info("Henter ektefelle/partner (ekteType: ${sivilstand?.type})")


            val ektefelleBruker = sivilstand?.relatertVedSivilstand?.let { personService.hentPerson(NorskIdent(it)) }
            val ektefellePerson = ektefelleBruker?.takeUnless { it.erDoed() }

            logger.info("Henter barn")
            val barnPerson = if (forsikretPerson == null || !fyllUtBarnListe) emptyList() else hentBarn(forsikretPerson)

            logger.debug("gjenlevendeEllerAvdod: ${gjenlevendeEllerAvdod?.navn?.sammensattNavn }, forsikretPerson: ${forsikretPerson?.navn?.sammensattNavn }")

            PersonDataCollection(gjenlevendeEllerAvdod = gjenlevendeEllerAvdod, forsikretPerson = forsikretPerson!!, ektefellePerson = ektefellePerson,  sivilstandstype =  sivilstandType, barnPersonList = barnPerson)
        }
    }

    private fun hentBarn(hovedPerson: Person): List<Person> {
        logger.info("henter ut relasjon BARN")
        val barnepinListe = hovedPerson.familierelasjoner
            .filter { it.relatertPersonsRolle == Familierelasjonsrolle.BARN }
            .map { it.relatertPersonsIdent }
            .filter { barnPin -> NavFodselsnummer(barnPin).isUnder18Year() }
        logger.info("prøver å hente ut alle barn (filtrert) på hovedperson: " + barnepinListe.size )

        return barnepinListe
            .mapNotNull { barnPin -> personService.hentPerson(NorskIdent(barnPin)) }
            .filterNot{ barn -> barn.erDoed() }
            .onEach {barn ->
                logger.debug("Hentet følgende barn fra PDL aktoerid: ${barn.identer.firstOrNull { it.gruppe == IdentGruppe.AKTORID }}")
            }
    }

    private fun filterEktefelleRelasjon(forsikretPerson: Person?): Sivilstand? {
        val validRelasjoner = listOf(Sivilstandstype.GIFT, Sivilstandstype.PARTNER)
        return forsikretPerson?.sivilstand
            ?.filter { validRelasjoner.contains(it.type) }
            ?.maxBy { it.gyldigFraOgMed!! }
    }

}