package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.MedlemskapItem
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class HentTrygdeTid (val euxInnhentingService: EuxInnhentingService) {

    private val logger = LoggerFactory.getLogger(HentTrygdeTid::class.java)

    fun hentBucFraEux(bucId: Int, fnr: String): List<MedlemskapItem> ? {
        logger.info("** Innhenting av kravdata for BUC: $bucId **")

        val buc = euxInnhentingService.getBucAsSystemuser(bucId.toString()) ?: run {
            logger.error("BUC: $bucId kan ikke hentes")
            return null
        }

        val bucUtils = BucUtils(buc)

        val sedDoc = hentAlleP5000(bucUtils).also { logger.debug("P5000: ${it.toJson()}") }
        if (sedDoc.isEmpty()) throw ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Ingen P5000 dokument metadata funnet for BUC med id: $bucId"
        ).also { logger.error(it.message) }

        val sedList = sedDoc
            .sortedByDescending {
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.lastUpdate as Long), ZoneOffset.UTC)
            }
            .also { logger.debug("Sortert på dato: {}", it) }
            .mapNotNull { it.id }
            .map { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(bucId.toString(), it) as P5000 }
            .firstOrNull()
            ?.let { listOf(it) } ?: emptyList()

        if (sedList.isEmpty()) throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Ingen gyldig P5000 SED funnet for BUC med id: $bucId"
        ).also { logger.error(it.message) }

        return sedList.map { it.pensjon?.trygdetid }.firstOrNull()
    }

    fun hentAlleP5000(bucUtils: BucUtils) =
        bucUtils.getAllDocuments().filter { it.status == "received" && it.type == SedType.P5000 }
}