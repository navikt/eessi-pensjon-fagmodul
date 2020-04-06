package no.nav.eessi.pensjon.vedlegg.client

import org.springframework.http.HttpStatus

class SafException(message: String, var httpStatus: HttpStatus) : RuntimeException(message)