package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.sed.MedlemskapItem
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

        val buc = euxInnhentingService.getBucAsSystemuser(bucId.toString()) ?: return logger.error("BUC: $bucId kan ikke hentes").let { null }
        val sedDoc = hentAlleP5000(BucUtils(buc)).also { logger.debug("P5000: ${it.toJson()}") }

        if (sedDoc.isEmpty()) {
            logger.error("Ingen P5000 dokument metadata funnet for BUC med id: $bucId")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingen P5000 dokument metadata funnet for BUC med id: $bucId")
        }

        val (medlemskapList, org) = hentMedlemskapOgOrg(sedDoc, bucId)

        val trygdetidList = medlemskapList.map { medlemskap ->
            val land = medlemskap.land?.takeIf { it.length == 2 }?.let(kodeverkClient::finnLandkode) ?: medlemskap.land
            PensjonsinformasjonUtlandController.Trygdetid(
                land = land ?: "",
                acronym = org,
                type = medlemskap.type,
                startdato = medlemskap.periode?.fom.toString(),
                sluttdato = medlemskap.periode?.tom.toString(),
                aar = medlemskap.sum?.aar,
                mnd = medlemskap.sum?.maaneder,
                dag = medlemskap.sum?.dager?.nr,
                dagtype = medlemskap.sum?.dager?.type,
                ytelse = medlemskap.beregning,
                ordning = medlemskap.ordning,
                beregning = medlemskap.beregning
            )
        }

        return PensjonsinformasjonUtlandController.TygdetidForPesys(fnr = fnr, rinaNr = bucId, trygdetid = trygdetidList)
    }

    /**
     * Henter medlemskap og organisasjon fra P5000 SED dokumenter.
     * Det hentes det siste oppdaterte dokumentet, og deretter hentes medlemskap
     *
     * @param sedDoc Liste over P5000 SED dokumenter
     * @param bucId ID for BUC
     * @return En pair med liste over medlemskap og organisasjonsakronym
     */
    private fun hentMedlemskapOgOrg(
        sedDoc: List<DocumentsItem>,
        bucId: Int
    ): Pair<List<MedlemskapItem>, String?> {
        val sedIdOgMedlemskap = sedDoc
            .sortedByDescending {
                OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(
                        (it.lastUpdate ?: it.creationDate) as Long
                    ), ZoneOffset.UTC
                )
            }
            .map { Pair(it.id, it.participants?.find { p -> p?.role == "Sender" }?.organisation?.acronym) }
            .firstOrNull()

        val sed = euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(bucId.toString(), sedIdOgMedlemskap?.first!!) as P5000
        val medlemskapList = sed.pensjon?.medlemskapboarbeid?.medlemskap.orEmpty()
        val org = sedIdOgMedlemskap.second
        return Pair(medlemskapList, org)
    }

    fun hentAlleP5000(bucUtils: BucUtils) =
        bucUtils.getAllDocuments().filter { it.status == "received" && it.type == SedType.P5000 }
}