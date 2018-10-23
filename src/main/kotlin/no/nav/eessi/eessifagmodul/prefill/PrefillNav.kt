package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

@Component
class PrefillNav(private val preutfyllingPersonFraTPS: PrefillPersonDataFromTPS): Prefill<Nav> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillNav::class.java) }

    //TODO hva vil avsender ID på RINA være for NAV-PEN?
    //vil dette hentes fra Fasit? eller Rina?
    private val institutionid = "NO:noinst002"
    private val institutionnavn = "NOINST002, NO INST002, NO"

    override fun prefill(prefillData: PrefillDataModel): Nav {
        return utfyllNav(prefillData)
    }

    private fun utfyllNav(utfyllingData: PrefillDataModel): Nav {
        logger.debug("perfill pinid: ${utfyllingData.personNr}")
        logger.debug("perfill aktoerid: ${utfyllingData.aktoerID}")
        //bruker død hvis etterlatt (etterlatt aktoerregister fylt ut)
        val brukertps = bruker(utfyllingData)

        //skal denne kjøres hver gang? eller kun under P2000?
        val barnatps = hentBarnaFraTPS(utfyllingData)
        val pensaknr = utfyllingData.penSaksnummer
        val lokalSaksnr = opprettLokalSaknr( pensaknr )

        val nav = Nav(
                barn = barnatps,

                bruker = brukertps,
                //korrekt bruk av eessisak? skal pen-saknr legges ved?
                //eller peker denne til en ekisterende rina-casenr?

                eessisak = lokalSaksnr,

                //YYYY-MM-dd -- now date"
                krav = Krav(SimpleDateFormat("yyyy-MM-dd").format(Date()))
            )
        logger.debug("[${utfyllingData.getSEDid()}] Sjekker PinID : ${utfyllingData.personNr}")

        //${nav.eessisak}"
        logger.debug("[${utfyllingData.getSEDid()}] Utfylling av NAV data med lokalsaksnr: $pensaknr")
        return nav
    }

    private fun bruker(utfyllingData: PrefillDataModel): Bruker {

            // kan denne utfylling benyttes på alle SED?
            // etterlatt pensjon da er dette den avdøde.(ikke levende)
            // etterlatt pensjon da er den levende i pk.3 sed (gjenlevende) (pensjon.gjenlevende)

            if (utfyllingData.isValidEtterlatt()) {
                val pinid = utfyllingData.avdodPersonnr
                val bruker = preutfyllingPersonFraTPS.prefillBruker(pinid)
                logger.debug("Preutfylling Utfylling (avdød) Nav END")
                return bruker
            }
            val pinid = utfyllingData.personNr
            val bruker = preutfyllingPersonFraTPS.prefillBruker(pinid)
            logger.debug("Preutfylling Utfylling Nav END")
            return bruker
    }

    private fun hentBarnaFraTPS(utfyllingData: PrefillDataModel) :List<BarnItem> {
        if (utfyllingData.getSEDid() != "P2000") {
            logger.debug("Preutfylling barn SKIP not valid SED?")
            return listOf()
        }
        logger.debug("Preutfylling barn START")
        val barnaspin = preutfyllingPersonFraTPS.hentBarnaPinIdFraBruker(utfyllingData.personNr)
        val barna = mutableListOf<BarnItem>()
        barnaspin.forEach {
            val barnBruker = preutfyllingPersonFraTPS.prefillBruker(it)
            logger.debug("Preutfylling barn x..")
            val barn = BarnItem(
                    person = barnBruker.person,
                    far = barnBruker.far,
                    mor = barnBruker.mor,
                    relasjontilbruker = "BARN"
            )
            barna.add(barn)
        }
        logger.debug("Preutfylling barn END")
        return barna.toList()
    }

    /**
     * TODO NAV lokal institusjon må hentes fra Fasit? Rina?
     */
    private fun opprettLokalSaknr(pensaknr: String = ""): List<EessisakItem> {
        //Må få hentet ut NAV institusjon avsender fra fasit?
        val lokalsak = EessisakItem(
                institusjonsid = institutionid,
                institusjonsnavn = institutionnavn,
                saksnummer = pensaknr,
                land = "NO"
        )
        return listOf(lokalsak)
    }


}

