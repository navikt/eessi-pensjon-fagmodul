package no.nav.eessi.eessifagmodul.models

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeOpprettetException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class SedDokumentIkkeSendtException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class SedDokumentIkkeGyldigException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class RinaCasenrIkkeMottattException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class IkkeGyldigKallException(message: String) : IllegalArgumentException(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeLestException(message: String): Exception(message)

class PersonV3IkkeFunnetException(message: String?): Exception(message)

class PersonV3SikkerhetsbegrensningException(message: String?): Exception(message)

class AktoerregisterIkkeFunnetException(message: String?): Exception(message)

class AktoerregisterException(message: String) : Exception(message)
