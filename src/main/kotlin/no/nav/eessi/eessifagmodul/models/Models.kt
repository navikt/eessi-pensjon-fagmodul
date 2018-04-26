package no.nav.eessi.eessifagmodul.models

import java.util.*

//https://confluence.adeo.no/pages/viewpage.action?pageId=262412867

data class BUC(
        val flytType: String,
        val saksbehandler: String,
        val saksnummerPensjon: String,
        val Parter: SenderReceiver,
        val SenderID: String = "NO:NAV",
        val SEDType: String,
        val NAVSaksnummer: String?,
        val notat_tmp: String
)

data class SED(
        val SEDType: String,
        val NAVSaksnummer: String?,
        val ForsikretPerson: NavPerson,
        val Barn: List<NavPerson>? = null,
        val Samboer: NavPerson? = null
)

data class NavPerson(
        val fnr: String
)

data class SenderReceiver(
        val sender: Institusjon,
        val receiver: List<Institusjon>
)

data class Institusjon(
        val landkode: String,
        val navn: String
)

data class OpprettBuCogSEDResponse(
        val korrelasjonsID: UUID,
        val rinasaksnummer: String?,
        val status: String?
        //val nyere_parameter_versjon2: String?
)