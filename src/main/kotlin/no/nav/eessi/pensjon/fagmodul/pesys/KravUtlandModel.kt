package no.nav.eessi.pensjon.fagmodul.pesys

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.LocalDate

//Model for opprettelse av kravhode til pesys.
data class KravUtland(
        val errorMelding: String? = null,

        @JsonSerialize(using = LocalDateSerializer::class)
        val mottattDato: LocalDate? = null,  // 9.1 kravsato

        @JsonSerialize(using = LocalDateSerializer::class)
        val iverksettelsesdato: LocalDate? = null, // 9.1 + 1dag i mnd - 3mnd

        @JsonSerialize(using = LocalDateSerializer::class)
        val virkningsDato: LocalDate? = null, // 9.1.4 + 1dag i mnd om dato er før 15, ellers 1 dag i mnd etter

        @JsonSerialize(using = LocalDateSerializer::class)
        val fremsattKravdato: LocalDate? = null, //SED metadata dato

        val uttaksgrad: String? = "0",         //P3000
        val vurdereTrygdeavtale: Boolean? = null,
        val personopplysninger: SkjemaPersonopplysninger? = null,
        val utland: SkjemaUtland? = null, //utland opphold filtrer bort Norge
        var sivilstand: SkjemaFamilieforhold? = null, // gift, samb..
        val soknadFraLand: String? = null, //hvilket land kommer kravsøknad fra (buc-caseowner)
        val initiertAv: String = "BRUKER" //skal alltid være BRUKER
)

data class SkjemaPersonopplysninger(
        val statsborgerskap: String? = null   //P2000 pkt. 2.2.1.1 land_3 tegn
)

//P4000
data class SkjemaUtland(
        val utlandsopphold: List<Utlandsoppholditem>? = null
)

//P4000 - P5000 (for bosted nå) P4000 kan f.eks inneholde kun norge noe pesys ikke vil ha
//da må vi også sende med data fra P5000.
data class Utlandsoppholditem(
        val land: String? = null,
        //2017-05-01T00:00:00+02:00
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        //format pattern yyyy-MM-dd
        val fom: LocalDate? = null,
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        //format pattern yyyy-MM-dd
        val tom: LocalDate? = null,
        val bodd: Boolean? = null,
        val arbeidet: Boolean? = null,
        val pensjonsordning: String? = null,
        val utlandPin: String? = null
)

//P2000
data class SkjemaFamilieforhold(
        //Sivilstand for søker. Må være en gyldig verdi fra T_K_SIVILSTATUS_T:
        //ENKE, GIFT, GJES, GJPA, GJSA, GLAD, PLAD, REPA,SAMB, SEPA, SEPR, SKIL, SKPA, UGIF.
        //Pkt p2000 - 2.2.2.1. Familiestatus
        val valgtSivilstatus: String? = null,
        @JsonDeserialize(using = LocalDateDeserializer::class)
        @JsonSerialize(using = LocalDateSerializer::class)
        //format pattern yyyy-MM-dd
        val sivilstatusDatoFom: LocalDate? = null
)
