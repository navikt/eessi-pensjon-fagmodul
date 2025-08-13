package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class HentTrygdeTid (val euxInnhentingService: EuxInnhentingService, private val kodeverkClient: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(HentTrygdeTid::class.java)

    fun hentBucFraEux(bucId: Int, fnr: String): PensjonsinformasjonUtlandController.TygdetidForPesys? {
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

        val idAndCreator = sedDoc
            .sortedByDescending {
                val date = (it.lastUpdate as? Long) ?: (it.creationDate as Long)
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC)
            }
            .also { logger.debug("Sortert på dato: {}", it) }
            .map { Pair(it.id, it.creator) }
            .firstOrNull()

        val sed = euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(bucId.toString(), idAndCreator?.first.toString()) as P5000
        val medlemskap = sed.pensjon?.medlemskapboarbeid?.medlemskap?.firstOrNull()
        val medlemskapPeriode = medlemskap?.periode
        val sedMedlemskap = sed.pensjon?.trygdetid?.firstOrNull()
        val org = idAndCreator?.second?.organisation

        val land = medlemskap?.land?.let {
            if (it.length == 2) kodeverkClient.finnLandkode(it) else it
        }
        return PensjonsinformasjonUtlandController.TygdetidForPesys(
            fnr = fnr,
            rinaNr = bucId,
            trygdetid = listOf(
                PensjonsinformasjonUtlandController.Trygdetid(
                    land = land ?: "",
                    acronym = org?.acronym,
                    type = sedMedlemskap?.type ?: "",
                    startdato = medlemskapPeriode?.fom.toString(),
                    sluttdato = medlemskapPeriode?.tom.toString(),
                    aar = sedMedlemskap?.sum?.aar,
                    mnd = sedMedlemskap?.sum?.maaneder,
                    dag = null,
                    dagtype = sedMedlemskap?.sum?.dager?.type,
                    ytelse = sedMedlemskap?.beregning,
                    ordning = sedMedlemskap?.ordning ?: "",
                    beregning = null,
                )
            )
        )
    }

    fun hentAlleP5000(bucUtils: BucUtils) =
        bucUtils.getAllDocuments().filter { it.status == "received" && it.type == SedType.P5000 }
}