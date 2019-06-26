package no.nav.eessi.eessifagmodul.person

import no.nav.eessi.eessifagmodul.models.AktoerregisterException
import no.nav.eessi.eessifagmodul.models.IkkeGyldigKallException
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService

abstract class AktoerIdHelper(private val aktoerregisterService: AktoerregisterService) {

    @Throws(AktoerregisterException::class)
    fun hentAktoerIdPin(aktorid: String): String {

        if (aktorid.isBlank()) throw IkkeGyldigKallException("Mangler AktorId")
        return aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktorid)

    }

}