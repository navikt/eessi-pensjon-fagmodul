package no.nav.eessi.pensjon.fagmodul.models

enum class SEDType {
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
    P15000,
    X005,
    H070,
    H120,
    H121;

    companion object {
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