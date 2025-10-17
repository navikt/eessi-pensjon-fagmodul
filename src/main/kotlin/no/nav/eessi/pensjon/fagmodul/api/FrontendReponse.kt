package no.nav.eessi.pensjon.fagmodul.api

import org.springframework.http.HttpStatus

class FrontEndResponse<T>(
    val result: T? = null,
    val status: String? = null,
    val message: String? = null,
    val stackTrace: String? = null
)

//class SuccessResponse<T>(result: T) : FrontEndResponse<T>(result = result, status = HttpStatus.OK.name)
//class BadRequestResponse<T>(message: String, stackTrace: String? = null) : FrontEndResponse<T>(status = HttpStatus.BAD_REQUEST.name, message = message, stackTrace = stackTrace)
