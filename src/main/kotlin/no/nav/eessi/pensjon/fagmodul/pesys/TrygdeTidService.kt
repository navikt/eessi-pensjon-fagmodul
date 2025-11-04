package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.sed.MedlemskapItem
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class TrygdeTidService (val euxInnhentingService: EuxInnhentingService, private val kodeverkClient: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(TrygdeTidService::class.java)

    fun hentBucFraEux(bucId: Int?, fnr: String): TrygdetidForPesys? {
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
            Trygdetid(
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

        return TrygdetidForPesys(fnr = fnr, trygdetid = trygdetidList)
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
        bucId: Int?
    ): Pair<List<MedlemskapItem>, String?> {
        val sedIdOgMedlemskap = sedDoc
            .sortedByDescending {
                Instant.ofEpochMilli((it.lastUpdate ?: it.creationDate) as Long)
            }
            .map { Pair(it.id, it.participants?.find { p -> p?.role == "Sender" }?.organisation?.acronym) }
            .firstOrNull()

        return hentSedInfo(bucId, sedIdOgMedlemskap)
    }

    /**
     * Henter SED fra EUX
     * @return medlemskap og organisasjons-info
     */
    private fun hentSedInfo(bucId: Int?, sedIdOgMedlemskap: Pair<String?, String?>?): Pair<List<MedlemskapItem>, String?> {
        val sed = euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(bucId.toString(), sedIdOgMedlemskap?.first!!) as P5000
        val medlemskap = sed.pensjon?.medlemskapboarbeid?.medlemskap.orEmpty()
        val org = sedIdOgMedlemskap.second
        return Pair(medlemskap, org)
    }

    fun parseTrygdetid(lagretTrygdetid: List<Pair<String, String?>>): List<Pair<String?, List<Trygdetid>>>? {
        return lagretTrygdetid.map { (trygdetid, rinaNr) ->
            val json = trygdetid.trim('"')
                .replace("\\n", "")
                .replace("\\\"", "\"")

            val trygdeTidListe = hentLandFraKodeverk(json)

            val rinaId = rinaNr?.split(Regex("\\D+"))
                ?.lastOrNull { it.isNotEmpty() }

            rinaId to trygdeTidListe
        }
    }

    fun hentLandFraKodeverk(json: String): List<Trygdetid> = mapJsonToAny<List<Trygdetid>>(json).map { trygdetid ->
        trygdetid.takeIf { it.land.length != 2 } ?: trygdetid.copy(
            land = kodeverkClient.finnLandkode(trygdetid.land) ?: trygdetid.land
        )
    }

    /**
     * Henter alle P5000 som er mottatt (i.e. status = "received")
     */
    fun hentAlleP5000(bucUtils: BucUtils) =
        bucUtils.getAllDocuments().filter { it.status == "received" && it.type == SedType.P5000 }
}

