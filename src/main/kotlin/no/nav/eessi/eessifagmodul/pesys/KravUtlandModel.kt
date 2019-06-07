package no.nav.eessi.eessifagmodul.pesys

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.LocalDate

//Model for opprettelse av kravhode til pesys.
//Krever p2x00 og P3000_no, P4000,P5000 før en kan
//tilby tjeneste med nødvendig informasjon for PESYS til å opprette kravhode automatisk.

//omhandler alle SED?
data class KravUtland(
        var errorMelding: String? = null,
        //P2000 - format pattern yyyy-MM-dd
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        var mottattDato: LocalDate? = null,
        //virkningsTidspunkt: LocalDate? = null,
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        var iverksettelsesdato: LocalDate? = null,

        //P3000
        var uttaksgrad: String? = null,
        //P5000
        var vurdereTrygdeavtale: Boolean? = null,

        var personopplysninger: SkjemaPersonopplysninger? = null,

        //P4000
        val utland: SkjemaUtland? = null,
        //P2000
        var sivilstand: SkjemaFamilieforhold? = null,

        //caseowner fra type
        var soknadFraLand: String? = null,

        //p2000
        var initiertAv: String? = null
)

data class SkjemaPersonopplysninger(
        //P2000 pkt. 2.2.1.1 land_3 tegn
        var statsborgerskap: String? = null
//        //utvandret?
//        var utvandret: Boolean? = null,
//        //statsborgeskap
//        var land: String? = null
)

//P4000
data class SkjemaUtland(
        var utlandsopphold: List<Utlandsoppholditem>? = null
        //var harOpphold: Boolean? = null
)

//P4000 - P5000 (for bosted nå) P4000 kan f.eks inneholde kun norge noe pesys ikke vil ha
//da må vi også sende med data fra P5000.
data class Utlandsoppholditem(
        var land: String? = null,
        //2017-05-01T00:00:00+02:00
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        //format pattern yyyy-MM-dd
        var fom: LocalDate? = null,
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        //format pattern yyyy-MM-dd
        var tom: LocalDate? = null,
        var bodd: Boolean? = null,
        var arbeidet: Boolean? = null,
        var pensjonsordning: String? = null,
        var utlandPin: String? = null
)

//P2000
data class SkjemaFamilieforhold(
        //Sivilstand for søker. Må være en gyldig verdi fra T_K_SIVILSTATUS_T:
        //ENKE, GIFT, GJES, GJPA, GJSA, GLAD, PLAD, REPA,SAMB, SEPA, SEPR, SKIL, SKPA, UGIF.
        //Pkt p2000 - 2.2.2.1. Familiestatus
        var valgtSivilstatus: String? = null,
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        //format pattern yyyy-MM-dd
        var sivilstatusDatoFom: LocalDate? = null
)
