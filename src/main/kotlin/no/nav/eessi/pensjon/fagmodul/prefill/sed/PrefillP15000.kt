package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.models.KravType
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class PrefillP15000(private val prefillSed: PrefillSed) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP15000::class.java) }

    fun prefill(
        prefillData: PrefillDataModel,
        personData: PersonDataCollection,
        sak: V1Sak?,
        pensjonsinformasjon: Pensjonsinformasjon?
    ): P15000 {

        val kravType = prefillData.kravType ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "For preutfylling av P15000 så kreves det kravtype")
        val penSaksnummer = prefillData.penSaksnummer
        val sakType = sak?.sakType
        val avdodFnr = prefillData.avdod?.norskIdent

        logger.debug("Saken er av type: $sakType og kravType request er: $kravType")
        logger.debug("Vedtak har avdød? ${pensjonsinformasjon?.avdod != null}")

        if (kravType != KravType.GJENLEV && kravType.name != sakType) {
            if(sakType == null) {
                val errorMsg = "Ved opprettelse av krav SED må saksbehandling være fullført i Pesys ( vilkårsprøving o.l ) og jordklode i brukerkontekst kan ikke benyttes"
                logger.warn(errorMsg)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg)
            }
            val errorMsg =  "Du kan ikke opprette ${sedTypeAsText(kravType)} i en ${sakTypeAsText(sakType)} (PESYS-saksnr: $penSaksnummer har sakstype ${sakType})"
            logger.warn(errorMsg)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg)
        }

        val relasjon = relasjon(pensjonsinformasjon, avdodFnr)
        val navsed = prefillSed.prefill(prefillData, personData)
        val eessielm = navsed.nav?.eessisak

        val gjenlevendeBruker: Bruker? = navsed.pensjon?.gjenlevende
        val forsikretBruker = if (kravType != KravType.GJENLEV && gjenlevendeBruker != null) {
            gjenlevendeBruker
        } else {
            navsed.nav?.bruker
        }

        logger.debug("gjenlevendeBruker: ${gjenlevendeBruker?.person?.fornavn} PIN: ${gjenlevendeBruker?.person?.pin?.firstOrNull()?.identifikator} ")
        logger.debug("avDodBruker: ${forsikretBruker?.person?.fornavn} PIN: ${forsikretBruker?.person?.pin?.firstOrNull()?.identifikator} ")

        val forsikretPerson = forsikretBruker?.person
        val forsikretPersonPin = forsikretPerson?.pin?.firstOrNull()
        val adresse = forsikretBruker?.adresse

        val krav = Krav(dato = prefillData.kravDato, type = kravType.verdi)

        val nav = Nav(
            eessisak = eessielm,
            bruker = Bruker(
                person = Person(
                    etternavn = forsikretPerson?.etternavn,
                    fornavn = forsikretPerson?.fornavn,
                    foedselsdato = forsikretPerson?.foedselsdato,
                    kjoenn = forsikretPerson?.kjoenn,
                    pin = listOf(
                        PinItem(
                            identifikator = forsikretPersonPin?.identifikator,
                            land = forsikretPersonPin?.land
                        )
                    )
                ),
                adresse = adresse
            ),
            krav = krav
        )
        val gjenlevende = bestemGjenlevende(gjenlevendeBruker, relasjon, kravType)

        return P15000(nav = nav, p15000Pensjon = P15000Pensjon(gjenlevende))
    }


    private fun relasjon(pensjonsinformasjon: Pensjonsinformasjon?, avdodFnr: String?): String? {
        return if (pensjonsinformasjon != null && avdodFnr != null) {
            val relasjon = relasjonRolle(pensjonsinformasjon, avdodFnr)
            logger.debug("relsajson: $relasjon")
            when (relasjon) {
                null -> null
                "FAR", "MOR" -> "06"
                else -> "01"
            }
        } else {
            null
        }
    }

    private fun bestemGjenlevende(gjenlevende: Bruker?,
                                  relasjon: String?,
                                  kravType: KravType): Bruker? {
        if (kravType == KravType.GJENLEV) {
            return if (gjenlevende != null) {

                val relasjontilAvdod = if (relasjon != null) {
                    RelasjonAvdodItem(relasjon = relasjon)
                } else {
                    null
                }
                val person = gjenlevende.person?.copy(relasjontilavdod = relasjontilAvdod)
                val gjenlevendeBruker = gjenlevende.copy(person = person)

                gjenlevendeBruker
            } else {
                null
            }
        } else
            return null
    }

    private fun sakTypeAsText(sakType: String?) =
        when (sakType) {
            "UFOREP" -> "uføretrygdsak"
            "ALDER" -> "alderspensjonssak"
            "GJENLEV" -> "gjenlevendesak"
            "BARNEP" -> "barnepensjonssak"
            null -> "SAKTYPE MANGLER"
            else -> "$sakType-sak"
        }

    private fun sedTypeAsText(kravType: KravType) =
        when (kravType) {
            KravType.ALDER -> "alderspensjonskrav"
            KravType.GJENLEV -> "gjenlevende-krav"
            KravType.UFOREP -> "uføretrygdkrav"
        }

    private fun relasjonRolle(pensjonInfo: Pensjonsinformasjon, avdodFnr: String): String? {
        val avdode = mapOf(
            pensjonInfo.avdod?.avdod to Sivilstandstype.GIFT.name,
            pensjonInfo.avdod?.avdodFar to Familierelasjonsrolle.FAR.name,
            pensjonInfo.avdod?.avdodMor to Familierelasjonsrolle.MOR.name
        )
        return avdode
            .filter { (fnr, _) -> fnr?.toLongOrNull() != null }
            .filter { (fnr, _) -> fnr == avdodFnr }
            .map { (_, value) -> value }
            .singleOrNull()
    }
}
