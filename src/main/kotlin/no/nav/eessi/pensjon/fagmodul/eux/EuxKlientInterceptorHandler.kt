package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.stereotype.Component

@Component
@Aspect
@EnableAspectJAutoProxy(proxyTargetClass=true)
class EuxKlientInterceptorHandler  {

    private val logger = LoggerFactory.getLogger(EuxKlientInterceptorHandler::class.java)

    @AfterReturning("execution(* no.nav.eessi.pensjon.fagmodul.eux.EuxKlient.*(..))",
        returning="retVal")
    fun logMethodCall(point: JoinPoint, retVal: BucSedResponse) {
        logger.debug("Kommet inn i statistikkhandler")
        logger.debug(point.signature.toShortString())
    }
}


