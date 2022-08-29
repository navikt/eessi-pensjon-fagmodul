package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.pesys.KravUtland
import no.nav.eessi.pensjon.fagmodul.pesys.SkjemaPersonopplysninger
import no.nav.eessi.pensjon.fagmodul.pesys.SkjemaUtland
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Service
class AlderpensjonUtlandKrav(
    private val kodeverkClient: KodeverkClient,
    @Value("\${ENV}")
    private val environment: String
) : UtlandKrav() {

    private val logger = LoggerFactory.getLogger(AlderpensjonUtlandKrav::class.java)

    fun kravAlderpensjonUtland(kravSed: SED, bucUtils: BucUtils, doc: DocumentsItem): KravUtland {

        val caseOwner = bucUtils.getCaseOwner()!!
        val caseOwnerCountryBuc = if (environment == "q2" || environment == "test") {
            "SE" //settes til SE for test i Q2 fra utland
        } else {
            justerAvsenderLand(caseOwner.country)
        }

        val caseOwnerCountry = kodeverkClient.finnLandkode(caseOwnerCountryBuc)

        logger.debug("CaseOwnerCountry: $caseOwnerCountry")
        logger.debug("CaseOwnerId     : ${caseOwner.institution}")
        logger.debug("CaseOwnerName   : ${caseOwner.name}")

        val mottattDato = mottattDocumentDato(doc, bucUtils)
        val kravdato = LocalDate.parse(kravSed.nav?.krav?.dato) ?: null

        return KravUtland(
            mottattDato = mottattDato,                     // når SED ble mottatt i NAV-RINA
            iverksettelsesdato = iverksettDatoAlder(kravSed, kravdato),  //fremsattdato
            fremsattKravdato = kravdato,                   // hentes fra kp. 9.1 kravdato
            uttaksgrad = "100",                            //saksbehandler retter på denne etter at vi setter den til 100%
            vurdereTrygdeavtale = true,
            personopplysninger = SkjemaPersonopplysninger(
                statsborgerskap = finnStatsborgerskapAlderLandkode3(kravSed)
            ),
            sivilstand = sivilstand(kravSed),
            soknadFraLand = caseOwnerCountry,
            utland = SkjemaUtland(emptyList())
        )
    }

    fun finnStatsborgerskapAlderLandkode3(kravSed: SED): String? {
        val statsborgerskap = kravSed.nav?.bruker?.person?.statsborgerskap?.firstOrNull { it.land != null }
        return statsborgerskap?.let { kodeverkClient.finnLandkode(it.land!!) }
    }

}