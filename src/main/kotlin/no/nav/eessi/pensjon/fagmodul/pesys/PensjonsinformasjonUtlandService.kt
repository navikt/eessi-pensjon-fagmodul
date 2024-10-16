package no.nav.eessi.pensjon.fagmodul.pesys

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.pesys.krav.AlderpensjonUtlandKrav
import no.nav.eessi.pensjon.fagmodul.pesys.krav.UforeUtlandKrav
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class PensjonsinformasjonUtlandService(
    private val alderpensjonUtlandsKrav: AlderpensjonUtlandKrav,
    private val uforeUtlandKrav: UforeUtlandKrav,
    private val euxInnhentingService: EuxInnhentingService) {

    private val logger = LoggerFactory.getLogger(PensjonsinformasjonUtlandService::class.java)

    private final val validBuc = listOf("P_BUC_01", "P_BUC_03")
    private final val kravSedBucmap = mapOf("P_BUC_01" to SedType.P2000, "P_BUC_03" to SedType.P2200)

    /**
     * funksjon for å hente buc-metadata fra RINA (eux-rina-api)
     * lese inn KRAV-SED P2xxx for så å plukke ut nødvendige data for så
     * returnere en KravUtland model
     */
    fun hentKravUtland(bucId: Int): KravUtland? {
        logger.info("** innhenting av kravdata for buc: $bucId **")

        val buc = euxInnhentingService.getBucAsSystemuser(bucId.toString())
        if(buc == null){
            logger.error("Buc: $bucId kan ikke hentes og vi er ikke i stand til å hente krav")
            return null
        }
        val bucUtils = BucUtils(buc)

        logger.debug("Starter prosess for henting av krav fra utland (P2000, P2200)")

        if (!validBuc.contains(bucUtils.getProcessDefinitionName())) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ugyldig BUC, Ikke korrekt type KRAV.")
        if (bucUtils.getCaseOwner() == null) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen CaseOwner funnet på BUC med id: $bucId").also { logger.error(it.message) }

        val sedDoc = getKravSedDocument(bucUtils, kravSedBucmap[bucUtils.getProcessDefinitionName()])
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingen dokument metadata funnet i BUC med id: $bucId.").also { logger.error(it.message) }

        val kravSed = sedDoc.id?.let { sedDocId -> euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(bucId.toString(), sedDocId) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ingen gyldig kravSed i BUC med id: $bucId funnet.").also { logger.error(it.message) }

        // finner rette korrekt metode for utfylling av KravUtland ut ifra hvilke SED/saktype det gjelder.
        logger.info("*** Starter kravUtlandpensjon: ${kravSed.type} bucId: $bucId bucType: ${bucUtils.getProcessDefinitionName()} ***")

        return when {
            erAlderpensjon(kravSed) -> {
                logger.debug("Kravtype er alderpensjon")
                alderpensjonUtlandsKrav.kravAlderpensjonUtland(kravSed, bucUtils, sedDoc).also {
                    debugPrintout(it)
                }
            }
            erUforepensjon(kravSed) -> {
                logger.debug("Kravtype er uførepensjon")
                uforeUtlandKrav.kravUforepensjonUtland(kravSed, bucUtils, sedDoc).also {
                    debugPrintout(it)
                }
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ikke støttet request")
        }

    }

    fun debugPrintout(kravUtland: KravUtland) {
        logger.info(
            """Følgende krav utland returneres:
            ${kravUtland.toJson()}
            """.trimIndent()
        )
    }

    fun getKravSedDocument(bucUtils: BucUtils, SedType: SedType?) =
        bucUtils.getAllDocuments().firstOrNull { it.status == "received" && it.type == SedType }

    fun erAlderpensjon(sed: SED) = sed.type == SedType.P2000

    fun erUforepensjon(sed: SED) = sed.type == SedType.P2200

}