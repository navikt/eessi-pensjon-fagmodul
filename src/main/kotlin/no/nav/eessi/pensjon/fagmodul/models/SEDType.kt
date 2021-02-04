package no.nav.eessi.pensjon.fagmodul.models

import com.fasterxml.jackson.annotation.JsonCreator

enum class SEDType {
    DummyChooseParts, // I særtilfeller hvor SedType ikke er valgt (P_BUC_06)
    P2000,
    P2100,
    P2200,
    P3000,
    P4000,
    P6000,
    P5000,
    P7000,
    P8000,
    P9000,
    P10000,
    P14000,
    P15000,
    X005,
    H020,
    H021,
    H070,
    H120,
    H121,
    P12000,
    P13000,
    P1000,
    P1100,
    P11000,
    X007,
    X008,
    X009,
    R004,
    R005,
    R006,
    P3000_FR, //Frankrike
    P3000_RO, //Romania
    P3000_IE, //Irland
    P3000_HU, //Ungarn
    P3000_LT, //Litauen
    P3000_IS, //Island
    P3000_UK, //Storbritannia
    P3000_NO, //Norge
    P3000_IT, //Italia
    P3000_SI, //Slovenia
    P3000_MT, //Malta
    P3000_BE, //Belgia
    P3000_EE, //Estland
    P3000_AT, //Østrike
    P3000_BG, //Bulgaria
    P3000_LI, //Liechtenstein
    P3000_DK, //Danmark
    P3000_SE, //Sverige
    P3000_FI, //Finland
    P3000_PL, //Polen
    P3000_DE, //Tyskland
    P3000_ES, //Spania
    P3000_PT, //Portugal
    P3000_LV, //Latvia
    P3000_SK, //Slovakia
    P3000_NL, //Nederland
    P3000_GR, //Hellas
    P3000_HR, //Kroatia
    P3000_CY, //Kypros
    P3000_LU, //Luxembourg
    P3000_CH, //Sveits
    P3000_CZ; //Tjekkia

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(s: String): SEDType? {
            return try {
                valueOf(s)
            } catch (e: Exception) {
                null
            }
        }

        @JvmStatic
        fun isValidSEDType(input: String): Boolean {
            return try {
                valueOf(input)
                true
            } catch (ia: IllegalArgumentException) {
                false
            }
        }
    }

}
