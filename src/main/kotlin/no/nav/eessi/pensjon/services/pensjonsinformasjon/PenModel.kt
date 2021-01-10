package no.nav.eessi.pensjon.services.pensjonsinformasjon

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

//https://confluence.adeo.no/pages/viewpage.action?pageId=338181301
enum class KravArsak {
    GJNL_SKAL_VURD,
    TILST_DOD
    //GJENLEVENDERETT
    //GJENLEVENDETILLEGG
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