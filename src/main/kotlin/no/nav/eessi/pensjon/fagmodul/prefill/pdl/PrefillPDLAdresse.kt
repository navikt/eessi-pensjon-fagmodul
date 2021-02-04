package no.nav.eessi.pensjon.fagmodul.prefill.pdl

import no.nav.eessi.pensjon.fagmodul.sedmodel.Adresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.services.geo.PostnummerService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person as PDLPerson

@Component
class PrefillPDLAdresse (private val postnummerService: PostnummerService,
                         private val kodeverkClient: KodeverkClient) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPDLAdresse::class.java) }

    /**
     *  2.2.2 adresse informasjon
     */
    fun createPersonAdresse(pdlperson: PDLPerson): Adresse? {
        logger.debug("2.2.2         Adresse")

        if (sjekkForDiskresjonKodeAdresse(pdlperson)) {
            return null
        }
        val bostedsadresse = pdlperson.bostedsadresse ?: return tomAdresse()

        val vegadresse = bostedsadresse.vegadresse ?: return utlandsAdresse(pdlperson)

        val husnr = listOfNotNull(vegadresse.husnummer, vegadresse.husbokstav)
            .joinToString(separator = " ")
        return Adresse(
                postnummer = vegadresse.postnummer,
                gate = "${vegadresse.adressenavn} $husnr",
                land = "NO",
                by = postnummerService.finnPoststed(vegadresse.postnummer)
        )
    }

    protected fun utlandsAdresse(pdlperson: PDLPerson) : Adresse {
        val utlandsAdresse = pdlperson.bostedsadresse?.utenlandskAdresse
        return Adresse(
            postnummer = utlandsAdresse?.postkode,
            gate = utlandsAdresse?.adressenavnNummer,
            land = hentLandkode(utlandsAdresse?.landkode),
            by = utlandsAdresse?.bySted
        )
    }

    protected fun sjekkForDiskresjonKodeAdresse(pdlperson: PDLPerson): Boolean {
        logger.debug("diskresjonskode:  ${pdlperson.adressebeskyttelse}")
        logger.debug("2.2.2         Adresse, diskresjon ingen adresse?")
        return pdlperson.adressebeskyttelse.any {
            it == AdressebeskyttelseGradering.FORTROLIG || it == AdressebeskyttelseGradering.STRENGT_FORTROLIG
        }
    }

    /**
     *  2.2.2 tom
     *
     *  Returnerer en blank adresse
     *  Dette må så endres/rettes av saksbehendlaer i rina?
     */
    private fun tomAdresse(): Adresse {
        logger.debug("             Tom adresse")
        return Adresse(
                gate = "",
                bygning = "",
                by = "",
                postnummer = "",
                land = ""
        )
    }

    fun hentLandkode(landkode: String?): String? {
        return landkode?.let { kodeverkClient.finnLandkode2(it) }
    }
}
