package no.nav.eessi.pensjon.fagmodul.eux.basismodel

import no.nav.eessi.pensjon.eux.model.buc.BucType

/**
 * data class model from EUX Basis
 */

//benyttes av UI
data class BucView(
        val euxCaseId: String,
        val buctype: BucType?,
        val aktoerId: String,
        val saknr: String,
        val avdodFnr: String? = null,
        val kilde: BucViewKilde
)

enum class BucViewKilde{
        BRUKER,
        SAF,
        AVDOD;
}
