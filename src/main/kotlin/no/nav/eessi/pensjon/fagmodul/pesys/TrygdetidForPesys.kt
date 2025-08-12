package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.eux.model.sed.MedlemskapItem

data class TrygdetidForPesys (
    val fnr: String?,
    val rinaNr: Int,
    val trygdetid: List<MedlemskapItem> = emptyList(),
    val error: String? = null) {
}

