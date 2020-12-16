package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.stereotype.Component

@Component
@Aspect
@EnableAspectJAutoProxy(proxyTargetClass=true)
class EuxKlientInterceptorHandler(private val statistikkHandler: StatistikkHandler)  {

    private val logger = LoggerFactory.getLogger(EuxKlientInterceptorHandler::class.java)

    @AfterReturning("execution(* no.nav.eessi.pensjon.fagmodul.eux.EuxKlient.opprettSed(..))",
        returning="retVal")
    fun logOpprettSed(point: JoinPoint, retVal: BucSedResponse) {

        logger.info("StatistikkhandlerInterceptor, logger opprettSed fra ${point.args}, med BucResponse: $retVal")
        statistikkHandler.produserSedOpprettetHendelse(retVal.caseId, retVal.documentId)
    }

    @AfterReturning("execution(* no.nav.eessi.pensjon.fagmodul.eux.EuxKlient.opprettSvarSed(..))",
        returning="retVal")
    fun logOpprettSvarSed(point: JoinPoint, retVal: BucSedResponse) {

        logger.info("StatistikkhandlerInterceptor, logger opprettSvarSed, med BucResponse: $retVal")
        statistikkHandler.produserSedOpprettetHendelse(retVal.caseId, retVal.documentId)
    }
}


