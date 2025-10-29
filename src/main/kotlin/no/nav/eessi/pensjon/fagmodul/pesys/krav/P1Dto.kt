package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.sed.AndreinstitusjonerItem
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import java.time.LocalDate

data class P1Dto(
    val innehaver: P1Person,
    val forsikrede: P1Person,
    val sakstype: String,
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
        val landkode: String? = null,
        val pin: List<PinItem>? = null,
    )

    data class InnvilgetPensjon(
        val institusjon: List<EessisakItem>?,
        val pensjonstype: String,
        val datoFoersteUtbetaling: LocalDate?,
        val bruttobeloep: String?,
        val valuta: String?,
        val utbetalingsHyppighet: String?,
        val grunnlagInnvilget: String?,
        val reduksjonsgrunnlag: String?,
        val vurderingsperiode: String?,
        val adresseNyVurdering: List<AndreinstitusjonerItem>?,
        val vedtaksdato: String?,
    )

    data class AvslaattPensjon(
        val institusjon: List<EessisakItem>?,
        val pensjonstype: String?,
        val avslagsbegrunnelse: String?,
        val vurderingsperiode: String?,
        val adresseNyVurdering: List<AndreinstitusjonerItem>?,
        val vedtaksdato: String?,
        @Transient
        var retning: String? = null
    )