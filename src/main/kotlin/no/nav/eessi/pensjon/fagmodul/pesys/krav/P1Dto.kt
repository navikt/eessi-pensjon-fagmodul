package no.nav.eessi.pensjon.fagmodul.pesys.krav

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.eessi.pensjon.eux.model.Avsender
import no.nav.eessi.pensjon.eux.model.sed.AndreinstitusjonerItem
import java.time.LocalDate

data class P1Dto(
    val innehaver: P1Person,
    val forsikrede: P1Person,
    val sakstype: String?,
    val kravMottattDato: LocalDate? = null,
    val innvilgedePensjoner: List<InnvilgetPensjon>,
    val avslaattePensjoner: List<AvslaattPensjon>,
    val utfyllendeInstitusjon: String
)

data class P1Person(
    val fornavn: String? = null,
    val etternavn: String? = null,
    val etternavnVedFoedsel: String? = null,
    val foedselsdato: LocalDate? = null,
    val adresselinje: String? = null,
    val poststed: String? = null,
    val postnummer: String? = null,
    val landkode: String? = null
)

data class InnvilgetPensjon(
    val institusjon: List<EessisakItemP1>?,
    val pensjonstype: String?,
    val datoFoersteUtbetaling: LocalDate?,
    val bruttobeloep: String?,
    val valuta: String?,
    val utbetalingsHyppighet: String?,
    val grunnlagInnvilget: String?,
    val reduksjonsgrunnlag: String?,
    val vurderingsperiode: String?,
    val adresseNyVurdering: List<AndreinstitusjonerItem>?,
    val vedtaksdato: String?,
    @JsonIgnore
    val avsender: Avsender? = null,
) {
    fun erNorskInnvilget(): Boolean = Avsender.erNorsk(avsender)
}

data class AvslaattPensjon(
    val institusjon: List<EessisakItemP1>?,
    val pensjonstype: String?,
    val avslagsbegrunnelse: String?,
    val vurderingsperiode: String?,
    val adresseNyVurdering: List<AndreinstitusjonerItem>?,
    val vedtaksdato: String?,
    @JsonIgnore
    val avsender: Avsender? = null
) {
    fun erNorskAvslag(): Boolean = Avsender.erNorsk(avsender)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EessisakItemP1(
    val institusjonsid: String? = null,
    val institusjonsnavn: String? = null,
    val saksnummer: String? = null,
    val land: String? = null,
    val identifikatorForsikrede: String? = null,
    val identifikatorInnehaver: String? = null

)