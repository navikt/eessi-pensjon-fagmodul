package no.nav.eessi.pensjon.fagmodul.prefill.pdl

import no.nav.eessi.pensjon.fagmodul.sedmodel.Adresse
import no.nav.eessi.pensjon.services.geo.PostnummerService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bostedsadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Gateadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PrefillAdresse ( private val postnummerService: PostnummerService,
                       private val kodeverkClient: KodeverkClient) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillAdresse::class.java) }

    /**
     *  2.2.2 adresse informasjon
     */
    fun createPersonAdresse(personTPS: Person): Adresse? {
        logger.debug("2.2.2         Adresse")

        if (sjekkForDiskresjonKodeAdresse(personTPS)) {
            return null
        }

        val bostedsadresse: Bostedsadresse = personTPS.bostedsadresse ?: return tomAdresse()

        return if (bostedsadresse.strukturertAdresse !is Gateadresse) {
            // vi har observert forekomst av Matrikkeladresse men ignorerer den og andre typer adresser for nå
            logger.warn("Forventet en Gateadresse som bostedsadresse for ${(personTPS.aktoer as PersonIdent).ident.ident}, men fikk en ${bostedsadresse.strukturertAdresse::class.simpleName}")
            tomAdresse()
        } else {
            val gateAdresse = bostedsadresse.strukturertAdresse as Gateadresse
            val gate = gateAdresse.gatenavn
            val husnr = gateAdresse.husnummer
            Adresse(
                    postnummer = gateAdresse.poststed.value,
                    gate = "$gate $husnr",
                    land = hentLandkode(gateAdresse.landkode.value),
                    by = postnummerService.finnPoststed(gateAdresse.poststed.value)
            )
        }
    }

    protected fun sjekkForDiskresjonKodeAdresse(personTPS: Person): Boolean {
        logger.debug("diskresjonskode:  ${personTPS.diskresjonskode}")

        val diskresjons = personTPS.diskresjonskode
        if (diskresjons != null && (diskresjons.value == "SPFO" || diskresjons.value == "SPSF")) {
            logger.debug("2.2.2         Adresse, diskresjon ingen adresse")
            return true
        }
        return false
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

    fun hentLandkode(landkode: String): String? {
        return kodeverkClient.finnLandkode2(landkode)
    }
}
