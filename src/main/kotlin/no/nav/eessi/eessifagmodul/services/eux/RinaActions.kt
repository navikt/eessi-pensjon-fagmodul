package no.nav.eessi.eessifagmodul.services.eux

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RinaActions(private val euxService: EuxService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(RinaActions::class.java) }

    @Value("\${rinaaction.waittime:4000}")
    lateinit var waittime: String  // waittime (basis venter 6000 på flere tjenester?)

    val create = "Create"
    val update = "Update"

    fun canUpdate(sed: String, rinanr: String): Boolean {
        return isActionPossible(sed, rinanr, update, 30)
    }

    fun canCreate(sed: String, rinanr: String): Boolean {
        return isActionPossible(sed, rinanr, create, 1)
    }

    fun isActionPossible(sed: String, euxCaseId: String, keyWord: String, maxLoop: Int = 1): Boolean {
        logger.info("Henter RINAaksjoner på sed: $sed, mot euxCaseId: $euxCaseId, leter etter: $keyWord")
        for (i in 1..maxLoop) {
            val result = getMuligeAksjoner(euxCaseId)
            result.forEach {
                if (sed == it.dokumentType && keyWord == it.navn) {
                    logger.info("Found $keyWord for $sed  exit.")
                    return true
                }
            }
            logger.warn("Not found, Try again in $waittime ms.")
            Thread.sleep(waittime.toLong())
        }
        logger.error("Max looping exit with false")
        return false
    }

    private fun getMuligeAksjoner(rinanr: String): List<RINAaksjoner> {
        return euxService.getPossibleActions(rinanr)
    }


}