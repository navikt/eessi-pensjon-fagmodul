package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs

// SED class main request class to basis
// Strukturerte Elektroniske Dokumenter
data class SED(
        val sed: String? = null,
        val sedGVer: String? = "4",
        val sedVer: String? = "1",
        var nav: Nav? = null, // TODO Mutable
        var pensjon: Pensjon? = null, // TODO Mutable
        var trygdetid: PersonArbeidogOppholdUtland? = null, //P4000 // TODO Mutable
        val ignore: Ignore? = null,

        //H120
        val horisontal: Horisontal? = null
) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }

    fun toJsonSkipEmpty(): String {
        return mapAnyToJson(this, true)
    }


    companion object {
        @JvmStatic
        fun fromJson(sed: String): SED {
            return mapJsonToAny(sed, typeRefs(), true)
        }
    }

    override fun toString() : String = this.toJson()

}