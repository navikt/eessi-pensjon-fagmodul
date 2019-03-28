package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.AndreinstitusjonerItem
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.pensjon.v1.brukersbarn.V1BrukersBarn
import no.nav.pensjon.v1.ektefellepartnersamboer.V1EktefellePartnerSamboer

/**
 * Data class to store different required data to build any given sed, auto or semiauto.
 *
 * sed, aktoerregister,  psak-saknr, rinanr, institutions (mottaker eg. nav),
 *
 * services:  aktoerregister, person, pen, maybe joark, eux-basis.
 *
 */
class PrefillDataModel {

    //pensjon
    lateinit var penSaksnummer: String
    lateinit var vedtakId: String
    lateinit var kravId: String

    //aktoearid og aktoerId for person
    lateinit var personNr: String
    lateinit var aktoerID: String

    //data fra pesys
    lateinit var saktype: String
    lateinit var barnlist: List<V1BrukersBarn>
    lateinit var partnerFnr: List<V1EktefellePartnerSamboer>

    //avdod rellasjon - gjennlevende
    lateinit var avdod: String
    lateinit var avdodFar: String
    lateinit var avdodMor: String

    //rina
    lateinit var rinaSubject: String
    lateinit var euxCaseID: String
    lateinit var bucsedres: BucSedResponse
    lateinit var buc: String
    lateinit var sed: SED
    lateinit var institution: List<InstitusjonItem>

    lateinit var skipSedkey: List<String>

    //hjelpe parametere for utfylling av institusjon
    var andreInstitusjon: AndreinstitusjonerItem? = null

    //div payload seddata json
    val partSedAsJson: MutableMap<String, String> = mutableMapOf()

    fun getSEDid(): String {
        return sed.sed!!
    }

    fun getPartSEDasJson(key: String): String? {
        return partSedAsJson[key]
    }

    fun getInstitutionsList(): List<InstitusjonItem> {
        return institution
    }

    fun erGyldigEtterlatt(): Boolean {
        //TODO finne bedre metode?
        return try {
            val state = checkNotNull(avdod)
            return state.isNotBlank()
        } catch (ex: Exception) {
            false
        }
    }

    fun erForeldreLos(): Boolean {
        //TODO finne bedre metode?
        return try {
            val stateOne = checkNotNull(avdodFar)
            val stateTwo = checkNotNull(avdodMor)
            stateOne.isNotBlank() && stateTwo.isNotBlank()
        } catch (ex: Exception) {
            false
        }
    }

    fun kanFeltSkippes(key: String): Boolean {
        return try {
            skipSedkey.contains(key)
        } catch (ex: Exception) {
            false
        }
    }

}
