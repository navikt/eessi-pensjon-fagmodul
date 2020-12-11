package no.nav.eessi.pensjon.fagmodul.prefill.model

import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.sedmodel.AndreinstitusjonerItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.InstitusjonX005
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
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
        val sedType: String,
        val sed: SED,
        val buc: String,
        val vedtakId: String? = null,
        val kravDato: String? = null,
        val kravId: String? = null,
        val euxCaseID: String,
        val institution: List<InstitusjonItem>,
        val refTilPerson: ReferanseTilPerson? = null,
        var melding: String? = null,
        var andreInstitusjon: AndreinstitusjonerItem? = null,
        var institusjonX005: InstitusjonX005? = null,
        val partSedAsJson: MutableMap<String, String> = mutableMapOf()
        ) {

    override fun toString(): String {
        return "DataModel: sedType: $sedType, bucType: $buc, penSakId: $penSaksnummer, vedtakId: $vedtakId, euxCaseId: $euxCaseID"
    }

    fun getSEDType(): String {
        return sedType
    }

    fun getPartSEDasJson(key: String): String? {
        return partSedAsJson[key]
    }

    fun getPersonInfoFromRequestData(): BrukerInformasjon? {
        val personInfo = getPartSEDasJson("PersonInfo") ?: return null
        return mapJsonToAny(personInfo, typeRefs())
    }

    fun getInstitutionsList(): List<InstitusjonItem> {
        return institution ?: emptyList()
    }

    fun isMinimumPrefill() = getSEDType() != SEDType.P6000.name

}

enum class ReferanseTilPerson(val verdi: String) {
    SOKER("02"),
    AVDOD("01");
}

