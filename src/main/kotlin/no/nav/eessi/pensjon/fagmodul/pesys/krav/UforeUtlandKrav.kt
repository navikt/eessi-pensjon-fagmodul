package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.pesys.KravUtland
import no.nav.eessi.pensjon.fagmodul.pesys.SkjemaPersonopplysninger
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class UforeUtlandKrav(
    private val kodeverkClient: KodeverkClient,
    @Value("\${ENV}")
    private val environment: String) : UtlandKrav() {

    private val logger = LoggerFactory.getLogger(UforeUtlandKrav::class.java)

    //P2200
    fun kravUforepensjonUtland(kravSed: SED, bucUtils: BucUtils, doc: DocumentsItem): KravUtland {
        val caseOwner = bucUtils.getCaseOwner()!!
        val caseOwnerCountryBuc = if (environment == "q2" || environment == "test") {
            "SE"
        } else {
            justerAvsenderLand(caseOwner.country)
        }
        val caseOwnerCountry = kodeverkClient.finnLandkode(caseOwnerCountryBuc)

        logger.debug("CaseOwnerCountry: $caseOwnerCountry")
        logger.debug("CaseOwnerId     : ${caseOwner.institution}")
        logger.debug("CaseOwnerName   : ${caseOwner.name}")

        return KravUtland(
            mottattDato = mottattDocumentDato(doc, bucUtils),                       // når SED ble mottatt i NAV-RINA
            iverksettelsesdato = iverksettDatoUfore(kravSed),                         // hentes fra kp. 9.1 kravdato - 3 mnd
            fremsattKravdato = LocalDate.parse(kravSed.nav?.krav?.dato) ?: null, // hentes fra kp. 9.1 kravdato

            vurdereTrygdeavtale = true,

            personopplysninger = SkjemaPersonopplysninger(
                statsborgerskap = finnStatsborgerskapsLandkode3(kodeverkClient, kravSed)
            ),
            sivilstand = sivilstand(kravSed),
            soknadFraLand = caseOwnerCountry
        )
    }
}