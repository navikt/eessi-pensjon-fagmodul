package no.nav.eessi.pensjon.fagmodul.sedmodel

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


class P5000Pensjon(
    val trygdetid: List<MedlemskapItem>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MedlemskapItem(
    val land: String? = null,
    val periode: Periode? = null,
)