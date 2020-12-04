package no.nav.eessi.pensjon.metrics

import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.stereotype.Component
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

@Component
@Aspect
@EnableAspectJAutoProxy(proxyTargetClass=true)

class StatistikkInterceptorHandler  {

    private val logger = LoggerFactory.getLogger(StatistikkInterceptorHandler::class.java)


    @AfterReturning("execution(* no.nav.eessi.pensjon.fagmodul.eux.EuxKlient.*(..))"
         // pointcut="execution(* no.nav.eessi.pensjon.fagmodul.eux.EuxKlient.*(..))",
            //pointcut="no.nav.eessi.pensjon.fagmodul.eux.EuxKlient.opprettSed(*)")
         ,returning="retVal")
    fun logMethodCall(point: JoinPoint, retVal: BucSedResponse) {
        logger.debug("Kommet inn i statistikkhandler")
        logger.debug(point.signature.toShortString())
        logger.debug(retVal.toString())
    }
}


