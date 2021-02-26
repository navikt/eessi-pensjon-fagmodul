package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.SEDType.H020
import no.nav.eessi.pensjon.fagmodul.models.SEDType.H021
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P10000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P15000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P2000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P2100
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P2200
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P4000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P6000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P7000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.P8000
import no.nav.eessi.pensjon.fagmodul.models.SEDType.X005
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2000
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2100
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2200
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillP6000
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype.ALDER
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype.UFOREP
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.vedtak.V1Vedtak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class PrefillSEDService(
    private val pensjonsinformasjonService: PensjonsinformasjonService,
    private val eessiInformasjon: EessiInformasjon,
    private val prefillPDLnav: PrefillPDLNav
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillSEDService::class.java) }

    fun prefill(prefillData: PrefillDataModel, personDataCollection: PersonDataCollection): SED {

        val sedType = prefillData.sedType

        logger.debug("mapping prefillClass to SED: $sedType")

        return when (sedType) {
            //krav
            P2000 -> PrefillP2000(prefillPDLnav).prefillSed(prefillData, personDataCollection, hentRelevantPensjonSak(prefillData) { pensakType -> pensakType == ALDER.name }, hentRelevantVedtak(prefillData))
            P2200 -> PrefillP2200(prefillPDLnav).prefillSed(prefillData, personDataCollection, hentRelevantPensjonSak(prefillData) { pensakType -> pensakType == UFOREP.name }, hentRelevantVedtak(prefillData))
            P2100 -> {
                val sedpair = PrefillP2100(prefillPDLnav).prefillSed(prefillData, personDataCollection, hentRelevantPensjonSak(prefillData) { pensakType ->
                    listOf(
                        "ALDER",
                        "BARNEP",
                        "GJENLEV",
                        "UFOREP"
                    ).contains(pensakType)
                })
                prefillData.melding = sedpair.first
                sedpair.second
            }

            //vedtak
            P6000 -> PrefillP6000(prefillPDLnav, eessiInformasjon, pensjonsinformasjonService.hentVedtak(hentVedtak(prefillData))).prefillSed(prefillData, personDataCollection)

            P4000 -> PrefillP4000(PrefillSed(prefillPDLnav)).prefillSed(prefillData, personDataCollection)
            P7000 -> PrefillP7000(PrefillSed(prefillPDLnav)).prefillSed(prefillData, personDataCollection)

            P8000 -> {
                if (prefillData.buc == "P_BUC_05") {
                    try {
                        PrefillP8000(PrefillSed(prefillPDLnav)).prefillSed(prefillData, personDataCollection, hentRelevantPensjonSak(prefillData) { pensakType -> listOf("ALDER", "BARNEP", "GJENLEV", "UFOREP", "GENRL", "OMSORG").contains(pensakType) })
                    } catch (ex: Exception) {
                        logger.error(ex.message)
                        PrefillP8000(PrefillSed(prefillPDLnav)).prefillSed(prefillData, personDataCollection, null)
                    }
                } else {
                    PrefillP8000(PrefillSed(prefillPDLnav)).prefillSed(prefillData, personDataCollection, null)
                }
            }

            P15000 -> PrefillP15000(PrefillSed(prefillPDLnav)).prefillSed(
                prefillData,
                personDataCollection,
                hentRelevantPensjonSak(prefillData) { pensakType -> listOf("ALDER", "BARNEP", "GJENLEV", "UFOREP", "GENRL", "OMSORG").contains(pensakType) },
                hentRelevantPensjonsinformasjon(prefillData)
            )

            P10000 -> PrefillP10000(PrefillSed(prefillPDLnav)).prefillSed(prefillData, personDataCollection)
            X005 -> PrefillX005(prefillPDLnav).prefill(prefillData, personDataCollection)
            H020, H021 -> PrefillH02X(PrefillSed(prefillPDLnav)).prefillSed(prefillData, personDataCollection)
            else ->
                //P3000_SE, PL, DK, DE, UK, med flere vil gå denne veien..
                //P5000, P9000, P14000, P15000.. med flere..
                PrefillSed(prefillPDLnav).prefill(prefillData, personDataCollection)
        }
    }

    fun hentVedtak(prefillData: PrefillDataModel): String {
        val vedtakId = prefillData.vedtakId
        vedtakId?.let {
            return it
        }
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler vedtakID")
    }

    fun hentRelevantPensjonSak(prefillData: PrefillDataModel, akseptabelSakstypeForSed: (String) -> Boolean): V1Sak? {
        logger.debug("sakNr er: ${prefillData.penSaksnummer} aktoerId er: ${prefillData.bruker.aktorId} prøver å hente Sak")
        return pensjonsinformasjonService.hentRelevantPensjonSak(prefillData, akseptabelSakstypeForSed)
    }

    private fun hentRelevantVedtak(prefillData: PrefillDataModel): V1Vedtak? {
        prefillData.vedtakId.let {
            logger.debug("vedtakId er: $it, prøver å hente vedtaket")
            return pensjonsinformasjonService.hentRelevantVedtakHvisFunnet(it ?: "")
        }
    }

    private fun hentRelevantPensjonsinformasjon(prefillData: PrefillDataModel): Pensjonsinformasjon? {
        return prefillData.vedtakId?.let {
            logger.debug("vedtakid er: $it, prøver å hente pensjonsinformasjon for vedtaket")
            pensjonsinformasjonService.hentMedVedtak(it)
        }
    }

}
