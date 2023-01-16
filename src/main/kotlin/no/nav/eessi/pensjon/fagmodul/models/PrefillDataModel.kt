package no.nav.eessi.pensjon.fagmodul.models

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.KravType

/**
 * Data class to store different required data to build any given sed, auto or semiauto.
 * sed, pdl,  psak-saknr, rinanr, institutions (mottaker eg. nav),
 *
 * services:  pdl, person, pen, maybe joark, eux-basis.
 */
class PersonId(val norskIdent: String,
               val aktorId: String)

data class PrefillDataModel(
    val penSaksnummer: String,
    val bruker: PersonId,
    val avdod: PersonId?,
    val sedType: SedType,
    val buc: BucType,
    val vedtakId: String? = null,
    val kravDato: String? = null, // Brukes bare av P15000
    val kravType: KravType? = null, // Brukes bare av P15000
    val kravId: String? = null,
    val euxCaseID: String,
    val institution: List<InstitusjonItem>,
    val refTilPerson: ReferanseTilPerson? = null,
    var melding: String? = null,
    val partSedAsJson: MutableMap<String, String> = mutableMapOf()
    ) {

    override fun toString(): String {
        return "DataModel: SedType: $SedType, bucType: $buc, penSakId: $penSaksnummer, vedtakId: $vedtakId, euxCaseId: $euxCaseID"
    }

    fun getInstitutionsList(): List<InstitusjonItem> = institution
}

enum class ReferanseTilPerson(val verdi: String) {
    SOKER("02"),
    AVDOD("01");
}
