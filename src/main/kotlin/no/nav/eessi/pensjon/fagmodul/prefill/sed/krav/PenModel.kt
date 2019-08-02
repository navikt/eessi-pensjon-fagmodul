package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav


enum class Kravtype {
    REVURD,
    F_BH_MED_UTL,
    FORSTEG_BH
}

enum class Kravstatus {
    TIL_BEHANDLING
}

//K_SAK_T Kodeverk fra PESYS
enum class Saktype {
    ALDER,
    UFOREP,
    GJENLEV,
    BARNEP;

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