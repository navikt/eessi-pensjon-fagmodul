package no.nav.eessi.pensjon.services.pensjonsinformasjon

import java.time.LocalDate

object EessiFellesDto { // brukes kun for namespace.
    enum class EessiSakType { AFP, AFP_PRIVAT, ALDER, BARNEP, FAM_PL, GAM_YRK, GENRL, GJENLEV, GRBL, KRIGSP, OMSORG, UFOREP }
    enum class EessiSakStatus { TIL_BEHANDLING, INGEN_STATUS, OPPRETTET, UKJENT, TRUKKET, AVBRUTT, AVSL, INNV, OPPHOR, VELG, VETIKKE, ANNET, EESSI_INGEN_STATUS, EESSI_UKJENT, EESSI_AVBRUTT}
    enum class EessiKravAarsak { GJNL_SKAL_VURD, TILST_DOD, NY_SOKNAD, ANNET, ENDRET_OPPTJENING, ANNEN_ARSAK, OPPL_UTLAND, OMREGNING }

    enum class EessiKravGjelder { AFP_EO, ANKE, EKSPORT, ENDR_UTTAKSGRAD, ERSTATNING, ETTERGIV_GJELD, FAS_UTG_IO, FORSTEG_BH,
        F_BH_BO_UTL, F_BH_KUN_UTL, F_BH_MED_UTL, GJ_RETT, GOD_OMSGSP, GOMR, HJLPBER_OVERG_UT, INNT_E,
        INNT_KTRL, KLAGE, KONTROLL_3_17_A, KONVERTERING, KONVERTERING_MIN, KONV_AVVIK_G_BATCH, MELLOMBH, MTK,
        OMGJ_TILBAKE, OVERF_OMSGSP, REGULERING, REVURD, SAK_OMKOST, SLUTT_BH_UTL, SOK_OKN_UG, SOK_RED_UG,
        SOK_UU, SOK_YS, TILBAKEKR, UT_EO, UT_VURDERING_EO, UTSEND_AVTALELAND, SLUTTBEH_KUN_UTL }

    data class PensjonSakDto(val sakId: String, val sakType: EessiSakType, val sakStatus: EessiSakStatus)
    data class EessiAvdodDto(val avdod: String?, val avdodMor: String?, val avdodFar: String?)
    data class EessiUfoeretidspunktDto(val uforetidspunkt: LocalDate?, val virkningstidspunkt: LocalDate?)
}
