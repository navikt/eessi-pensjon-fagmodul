package no.nav.eessi.eessifagmodul.services

import org.springframework.http.HttpStatus

class EessiServiceException(message:String, var httpStatus: HttpStatus): RuntimeException(message)