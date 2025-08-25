package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import no.nav.eessi.pensjon.fagmodul.pesys.PensjonsinformasjonUtlandController.EmptyStringToNullDeserializer

data class TrygdetidForPesys(
    val fnr: String?,
    val rinaNr: Int?,
    val trygdetid: List<Trygdetid> = emptyList(),
    val error: String? = null
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class Trygdetid(
    val land: String,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val acronym: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val type: String?,
    val startdato: String,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val sluttdato: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val aar: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val mnd: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val dag: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val dagtype: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val ytelse: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val ordning: String?,
    @JsonDeserialize(using = EmptyStringToNullDeserializer::class)
    val beregning: String?
)

