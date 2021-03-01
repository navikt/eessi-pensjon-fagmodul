package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.fagmodul.prefill.PersonDataService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

    fun hentFnrfraAktoerService(aktoerid: String?, persondataService: PersonDataService): String {
        if (aktoerid.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Fant ingen aktoerident")
        }
        return persondataService.hentIdent (IdentType.NorskIdent, AktoerId(aktoerid)).id

    }
