package no.nav.eessi.eessifagmodul.models

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs

// SED class main request class to basis
// Strukturerte Elektroniske Dokumenter
data class SED(
        var sed: String? = null,
        var sedGVer: String? = null,
        var sedVer: String? = null,
        var nav: Nav? = null,
        var pensjon: Pensjon? = null,
        var trygdetid: PersonArbeidogOppholdUtland? = null, //P4000
        var ignore: Ignore? = null,

        //H120
        var horisontal: Horisontal? = null
) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }

    fun toJsonSkipEmpty(): String {
        return mapAnyToJson(this, true)
    }


    companion object {
        @JvmStatic
        fun create(name: String): SED {
            return SED(sed = name, sedVer = "1", sedGVer = "4")
        }

        @JvmStatic
        fun fromJson(sed: String): SED {
            return mapJsonToAny(sed, typeRefs(), true)
        }
    }

    override fun toString() : String = this.toJson()

}

//Data struktur for bruk av apirequest(frontend) og utfyllingdata (backend)
data class InstitusjonItem(
        var country: String,
        var institution: String,
        var name: String? = null) {

        //sjekker på Instisjon legger ut ID til rina som <XX:ZZZZZ>
        fun checkAndConvertInstituion(): String {
            if (institution.contains(":")) {
                return institution
            }
            return "$country:$institution"
        }
}

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
    X005;

    companion object {
        @JvmStatic
        fun isValidSEDType(input: String): Boolean {
            return try {
                SEDType.valueOf(input)
                true
            } catch (ia: IllegalArgumentException) {
                throw SedDokumentIkkeGyldigException("Ikke gyldig SED")
            }
        }
    }
}