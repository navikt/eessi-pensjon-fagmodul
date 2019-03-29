package no.nav.eessi.eessifagmodul.services

import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.models.SedValidatorException

class SedValidator {

    fun valudateP2000(sed: SED) {
        when {
            sed.nav?.bruker?.person?.etternavn == null -> throw SedValidatorException("Etternavn mangler")
            sed.nav?.bruker?.person?.fornavn == null -> throw SedValidatorException("Fornavn mangler")
            sed.nav?.bruker?.person?.foedselsdato == null -> throw SedValidatorException("Fødseldsdato mangler")
            sed.nav?.bruker?.person?.kjoenn == null -> throw SedValidatorException("Kjønn mangler")
            sed.nav?.krav?.dato == null -> throw SedValidatorException("Kravdato mangler")
        }
    }

}