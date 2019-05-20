package no.nav.eessi.eessifagmodul.models

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class SedDokumentIkkeGyldigException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class IkkeGyldigKallException(message: String) : IllegalArgumentException(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeOpprettetException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeLestException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedIkkeSlettetException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class BucIkkeMottattException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class IkkeFunnetException(message: String) : IllegalArgumentException(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class PersonV3IkkeFunnetException(message: String?): Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class AktoerregisterIkkeFunnetException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.FORBIDDEN)
class PersonV3SikkerhetsbegrensningException(message: String?): Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class EuxGenericServerException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class RinaCasenrIkkeMottattException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class PensjoninformasjonException(message: String) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class AktoerregisterException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class SedDokumentIkkeSendtException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
class EuxServerException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
class SystembrukerTokenException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
class SedValidatorException(message: String) : GenericUnprocessableEntity(message)

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
open class GenericUnprocessableEntity(message: String) : IllegalArgumentException(message)
