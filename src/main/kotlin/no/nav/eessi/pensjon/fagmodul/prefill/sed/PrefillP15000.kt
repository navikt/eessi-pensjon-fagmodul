package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.FamilieRelasjonType
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.model.KravType
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.RelasjonAvdodItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.toJson
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class PrefillP15000(private val prefillSed: PrefillSed) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP15000::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonData, sak: V1Sak?, pensjonsinformasjon: Pensjonsinformasjon?): SED {

        val kravType = prefillData.kravType ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "For preutfylling av P15000 så kreves det kravtype")
        val penSaksnummer = prefillData.penSaksnummer
        val sakType = sak?.sakType
        val gjenlevendeAktoerId = prefillData.bruker.aktorId
        val avdodFnr = prefillData.avdod?.norskIdent

        logger.debug("Saken er av type: $sakType og kravType request er: $kravType")
        logger.debug("Vedtak har avdød? ${pensjonsinformasjon?.avdod != null}")

        if (kravType != KravType.GJENLEV && kravType.name != sakType) {
            logger.warn("Du kan ikke opprette ${sedTypeAsText(kravType)} i en ${sakTypeAsText(sakType)} (PESYS-saksnr: $penSaksnummer har sakstype $sakType)")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Du kan ikke opprette ${sedTypeAsText(kravType)} i en ${sakTypeAsText(sakType)} (PESYS-saksnr: $penSaksnummer har sakstype $sakType)")
        }

        val relasjon = relasjon(pensjonsinformasjon, gjenlevendeAktoerId, avdodFnr,  personData)
        val navsed = prefillSed.prefill(prefillData, personData)
        val eessielm = navsed.nav?.eessisak

        val gjenlevendeBruker: Bruker? = navsed.pensjon?.gjenlevende
        val forsikretBruker = navsed.nav?.bruker

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

        return SED(SEDType.P15000.name, nav = nav, pensjon = pensjonGjenlevende(gjenlevendeBruker, relasjon))
    }


    private fun relasjon(pensjonsinformasjon: Pensjonsinformasjon?, gjenlevendeAktoerId: String, avdodFnr: String?, personData: PersonData): String? {
        return if (pensjonsinformasjon != null && avdodFnr != null) {
            val relasjon = relasjonRolle(gjenlevendeAktoerId, avdodFnr,  personData.forsikretPerson, pensjonsinformasjon)
            logger.debug("relsajson: ${relasjon.toJson()}")
            when(relasjon) {
                "FAR","MOR" -> "06"
                else -> "01"
            }
        } else {
            null
        }
    }

    private fun pensjonGjenlevende(gjenlevende: Bruker?, relasjon: String?): Pensjon? {
        return if (gjenlevende != null) {

            val relasjontilAvdod = if (relasjon != null) {
                RelasjonAvdodItem(relasjon = relasjon)
            } else {
                null
            }
            val person = gjenlevende.person?.copy(relasjontilavdod = relasjontilAvdod)
            val gjenlevendeBruker = gjenlevende.copy(person = person)

            Pensjon(gjenlevende = gjenlevendeBruker)
        } else {
            null
        }
    }

    private fun sakTypeAsText(sakType: String?) =
        when (sakType) {
            "UFOREP" -> "uføretrygdsak"
            "ALDER" -> "alderspensjonssak"
            "GJENLEV" -> "gjenlevendesak"
            "BARNEP" -> "barnepensjonssak"
            null -> "[NULL]"
            else -> "$sakType-sak"
        }

    private fun sedTypeAsText(kravType: KravType) =
        when (kravType) {
            KravType.ALDER -> "alderspensjonskrav"
            KravType.GJENLEV-> "gjenlevende-krav"
            KravType.UFOREP -> "uføretrygdkrav"
        }

    private fun relasjonRolle(gjenlevendeAktoerId: String, avdodFnr: String?, gjenlevende: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person, pensjonInfo: Pensjonsinformasjon): String {
        return hentRetteAvdode(avdodFnr,
            mapOf(
                pensjonInfo.avdod?.avdod.toString() to "EKTE",
                pensjonInfo.avdod?.avdodFar.toString() to FamilieRelasjonType.FAR.name,
                pensjonInfo.avdod?.avdodMor.toString() to FamilieRelasjonType.MOR.name
            )
        ).map { avdod -> pairPersonFnr(avdod.key, avdod.value, gjenlevende ) }
        .single().also {
             logger.info("Det ble funnet $it avdøde for den gjenlevende med aktørID: $gjenlevendeAktoerId")
        }

    }

    private fun pairPersonFnr(
            avdodFnr: String,
            avdodRolle: String?,
            gjenlevende: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
        ): String {

        return if (avdodRolle == null) {
            val familierelasjon = gjenlevende.harFraRolleI.first { (it.tilPerson.aktoer as PersonIdent).ident.ident == avdodFnr }
            familierelasjon.tilRolle.value.toUpperCase()
        } else {
            avdodRolle
        }
    }

    private fun hentRetteAvdode(avdodFnr: String?, avdode: Map<String, String>): Map<String, String> {
        return avdode.filter { isNumber(it.key) }
            .filter { avdodFnr == it.key }
            .map { it.key to it.value }.toMap()
    }

    private fun isNumber(s: String?): Boolean {
        return if (s.isNullOrEmpty()) false else s.all { Character.isDigit(it) }
    }

}
