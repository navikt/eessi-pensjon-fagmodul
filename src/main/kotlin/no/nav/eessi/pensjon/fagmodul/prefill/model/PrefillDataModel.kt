package no.nav.eessi.pensjon.fagmodul.prefill.model

import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.AndreinstitusjonerItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.InstitusjonX005
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs

/**
 * Data class to store different required data to build any given sed, auto or semiauto.
 *
 * sed, aktoerregister,  psak-saknr, rinanr, institutions (mottaker eg. nav),
 *
 * services:  aktoerregister, person, pen, maybe joark, eux-basis.
 *
 */

class PersonId(val norskIdent: String,
               val aktorId: String)

class PrefillDataModel(val penSaksnummer: String, val bruker: PersonId, val avdod: PersonId?) {

    //pensjon
    lateinit var vedtakId: String

    lateinit var saktype: String

    //rina
    lateinit var rinaSubject: String
    lateinit var euxCaseID: String
    lateinit var buc: String
    lateinit var sed: SED
    lateinit var institution: List<InstitusjonItem>

    lateinit var skipSedkey: List<String>

    //hjelpe parametere for utfylling av institusjon
    var andreInstitusjon: AndreinstitusjonerItem? = null
    var institusjonX005: InstitusjonX005? = null

    //div payload seddata json
    var partSedAsJson: MutableMap<String, String> = mutableMapOf()

    fun getSEDid(): String {
        return sed.sed!!
    }

    fun getPartSEDasJson(key: String): String? {
        return partSedAsJson[key]
    }

    fun getPersonInfoFromRequestData(): BrukerInformasjon? {
        val personInfo = getPartSEDasJson("PersonInfo") ?: return null
        return mapJsonToAny(personInfo, typeRefs())
    }

    fun getInstitutionsList(): List<InstitusjonItem> {
        return institution
    }

    fun kanFeltSkippes(key: String): Boolean {
        return try {
            skipSedkey.contains(key)
        } catch (ex: Exception) {
            false
        }
    }

    fun clone() : String {
        return mapAnyToJson(this)
    }

    companion object {
        @JvmStatic
        fun fromJson(prefillData: String) : PrefillDataModel {
            return mapJsonToAny(prefillData, typeRefs(), true)
        }
    }

}


