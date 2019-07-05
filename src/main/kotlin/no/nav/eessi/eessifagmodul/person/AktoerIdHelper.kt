package no.nav.eessi.eessifagmodul.person

import no.nav.eessi.eessifagmodul.person.aktoerregister.AktoerregisterException
import no.nav.eessi.eessifagmodul.person.aktoerregister.AktoerregisterService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

abstract class AktoerIdHelper(private val aktoerregisterService: AktoerregisterService) {

    @Throws(AktoerregisterException::class)
    fun hentAktoerIdPin(aktorid: String): String {

        if (aktorid.isBlank()) throw ManglerAktoerIdException("Mangler AktorId")
        return aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktorid)

    }

}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class ManglerAktoerIdException(message: String) : IllegalArgumentException(message)
