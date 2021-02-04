package no.nav.eessi.pensjon.fagmodul.models

import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs

/**
 * Data class to store different required data to build any given sed, auto or semiauto.
 * sed, aktoerregister,  psak-saknr, rinanr, institutions (mottaker eg. nav),
 *
 * services:  aktoerregister, person, pen, maybe joark, eux-basis.
 */
class PersonId(val norskIdent: String,
               val aktorId: String)

data class PrefillDataModel(
    val penSaksnummer: String,
    val bruker: PersonId,
    val avdod: PersonId?,
    val sedType: SEDType,
    val buc: String,
    val vedtakId: String? = null,
    val kravDato: String? = null,
    val kravType: KravType? = null,
    val kravId: String? = null,
    val euxCaseID: String,
    val institution: List<InstitusjonItem>,
    val refTilPerson: ReferanseTilPerson? = null,
    var melding: String? = null,
    val partSedAsJson: MutableMap<String, String> = mutableMapOf()
    ) {

    override fun toString(): String {
        return "DataModel: sedType: $sedType, bucType: $buc, penSakId: $penSaksnummer, vedtakId: $vedtakId, euxCaseId: $euxCaseID"
    }

    fun getPartSEDasJson(key: String): String? {
        return partSedAsJson[key]
    }

    fun getPersonInfoFromRequestData(): BrukerInformasjon? {
        val personInfo = getPartSEDasJson("PersonInfo") ?: return null
        return mapJsonToAny(personInfo, typeRefs())
    }

    fun getInstitutionsList(): List<InstitusjonItem> = institution

    //fun isMinimumPrefill() = sedType != SEDType.P6000
}

enum class ReferanseTilPerson(val verdi: String) {
    SOKER("02"),
    AVDOD("01");
}

enum class KravType(val verdi: String) {
    ALDER("01"),
    GJENLEV("02"),
    UFOREP("03");
}
