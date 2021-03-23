package no.nav.eessi.pensjon.fagmodul.sedmodel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs

// SED class main request class to basis
// Strukturerte Elektroniske Dokumenter
@JsonIgnoreProperties(ignoreUnknown = true)
open class SED(
    @JsonProperty("sed")
    open val type: SEDType,
    open val sedGVer: String? = "4",
    open var sedVer: String? = "1",
    open var nav: Nav? = null, // TODO Mutable
    open var pensjon: Pensjon? = null, // TODO Mutable
) {
    companion object {
        @JvmStatic
        fun fromJson(sed: String): SED {
            return mapJsonToAny(sed, typeRefs(), true)
        }
    }

    @JsonIgnore
    override fun toString() : String = this.toJson()

}