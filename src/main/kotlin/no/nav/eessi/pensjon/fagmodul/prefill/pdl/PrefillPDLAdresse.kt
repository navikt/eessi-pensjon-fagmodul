package no.nav.eessi.pensjon.fagmodul.prefill.pdl

import no.nav.eessi.pensjon.fagmodul.sedmodel.Adresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.PostadresseIFrittFormat
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresseIFrittFormat
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
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

        logger.debug("              Sjekk for og preutfyll adresse")
        return sjekkOgPreutfyllAdresse(pdlperson)

    }

    private fun preutfullNorskBostedVegadresse(vegadresse: Vegadresse): Adresse {
        logger.info("              preutfyller bostedadresse land NO")
        val husnr = listOfNotNull(vegadresse.husnummer, vegadresse.husbokstav)
            .joinToString(separator = " ")
        return Adresse(
            postnummer = vegadresse.postnummer,
            gate = "${vegadresse.adressenavn} $husnr",
            by = postnummerService.finnPoststed(vegadresse.postnummer),
            land = "NO"
        )
    }

    private fun preutfyllNorskPostadresseIFrittFormat(postadresseIFrittFormat: PostadresseIFrittFormat): Adresse {
        logger.info("              preutfyller postadresseIFrittFormat land NO")
        return Adresse(
            gate = postadresseIFrittFormat.adresselinje1,
            bygning = postadresseIFrittFormat.adresselinje2,
            by = postadresseIFrittFormat.adresselinje3,
            land = "NO"
        )
    }


    private fun sjekkOgPreutfyllAdresse(pdlperson: PDLPerson): Adresse {
        //NOR
        val vegadresse = pdlperson.bostedsadresse?.vegadresse ?: pdlperson.oppholdsadresse?.vegadresse
        val postadresseIFrittFormat = pdlperson.kontaktadresse?.postadresseIFrittFormat

        //UTLAND
        val utenlandskAdresse = pdlperson.oppholdsadresse?.utenlandskAdresse ?: pdlperson.kontaktadresse?.utenlandskAdresse
        //sjekke vinning på utfylling av utlandskadresse
        val preutfyllUtenlandskAdresse = sjekkForGydligUtlandAdresse(utenlandskAdresse)
        val utenlandskAdresseIFrittFormat = pdlperson.kontaktadresse?.utenlandskAdresseIFrittFormat

        //Doedsbo
        val doedsboadresse = preutfyllDoedsboAdresseHvisFinnes(pdlperson)

        return when {
            doedsboadresse != null -> doedsboadresse
            vegadresse != null -> preutfullNorskBostedVegadresse(vegadresse)
            postadresseIFrittFormat != null -> preutfyllNorskPostadresseIFrittFormat(postadresseIFrittFormat)
            preutfyllUtenlandskAdresse -> preutfyllUtlandsAdresse(utenlandskAdresse)
            utenlandskAdresseIFrittFormat != null -> preutfyllUtenlandskAdresseIFrittFormat(utenlandskAdresseIFrittFormat)
            else -> tomAdresse()
        }
    }

    private fun preutfyllDoedsboAdresseHvisFinnes(pdlperson: PDLPerson): Adresse? {
        return if (pdlperson.erDoed()) {
            logger.info("              person er død. sjekker kontaktinformasjonForDoedsbo")
            val adresse = pdlperson.kontaktinformasjonForDoedsbo?.adresse ?: return null
            logger.info("              preutfyller kontaktinformasjonForDoedsbo")
            Adresse(
                gate = adresse.adresselinje1,
                bygning = adresse.adresselinje2,
                by = adresse.poststedsnavn,
                postnummer = adresse.postnummer,
                land = hentLandkode(adresse.landkode)
            )
        } else null
    }

    private fun preutfyllUtlandsAdresse(utlandsAdresse: UtenlandskAdresse?): Adresse {
        logger.info("              preutfyller strukturert utlandsAdresse")
        if (utlandsAdresse == null) return tomAdresse()
        return Adresse(
            postnummer = utlandsAdresse.postkode,
            gate = utlandsAdresse.adressenavnNummer,
            by = utlandsAdresse.bySted,
            land = hentLandkode(utlandsAdresse.landkode)
        )

    }

    private fun sjekkForGydligUtlandAdresse(utlandsAdresse: UtenlandskAdresse?): Boolean {
        if (utlandsAdresse == null) return false
        var antallAdrlinjer = 0
        if (utlandsAdresse.adressenavnNummer?.isNotBlank() == true) antallAdrlinjer ++
        if (utlandsAdresse.bySted?.isNotBlank() == true) antallAdrlinjer ++
        if (utlandsAdresse.postkode?.isNotBlank() == true) antallAdrlinjer ++
        if (antallAdrlinjer >= 2) {
            return true
        }
        return false
    }

    private fun preutfyllUtenlandskAdresseIFrittFormat(utenlandskAdresseIFrittFormat: UtenlandskAdresseIFrittFormat) : Adresse {
        logger.info("              preutfyller utenlandskAdresseIFrittFormat")
        return Adresse(
            gate = utenlandskAdresseIFrittFormat.adresselinje1,
            bygning = utenlandskAdresseIFrittFormat.adresselinje2,
            by = utenlandskAdresseIFrittFormat.adresselinje3,
            land = hentLandkode(utenlandskAdresseIFrittFormat.landkode)
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
        logger.info("             Tom adresse")
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
