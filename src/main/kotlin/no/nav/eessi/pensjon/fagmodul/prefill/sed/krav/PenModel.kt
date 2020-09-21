package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

enum class Kravtype {
    REVURD,
    F_BH_MED_UTL,
    FORSTEG_BH,
    F_BH_BO_UTL
}

enum class Kravstatus {
    TIL_BEHANDLING,
    AVSL
}

enum class KravArsak {
    GJNL_SKAL_VURD
}

enum class EPSaktype {
    ALDER,
    UFOREP,
    BARNEP,
    GJENLEV,
    GJENLEV_BARNEP;

    companion object {
        @JvmStatic
        fun isValid(input: String): Boolean {
            return try {
                valueOf(input)
                true
            } catch (ia: IllegalArgumentException) {
                false
            }
        }
    }
}

enum class Sakstatus {
    INNV,
    AVSL
}