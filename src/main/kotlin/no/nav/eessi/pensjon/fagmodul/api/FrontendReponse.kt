package no.nav.eessi.pensjon.fagmodul.api

class FrontEndResponse<T>(
    val result: T? = null,
    val status: String? = null,
    val message: String? = null,
    val stackTrace: String? = null
)

//class SuccessResponse<T>(result: T) : FrontEndResponse<T>(result = result, status = "OK")
//class ErrorResponse<T>(message: String, stackTrace: String? = null) : FrontEndResponse<T>(status = "ERROR", message = message, stackTrace = stackTrace)