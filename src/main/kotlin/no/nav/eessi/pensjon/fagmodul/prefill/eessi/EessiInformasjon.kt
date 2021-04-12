package no.nav.eessi.pensjon.fagmodul.prefill.eessi

import no.nav.eessi.pensjon.eux.model.sed.AndreinstitusjonerItem
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EessiInformasjon(
        @Value("\${eessi-pensjon-institusjon}") val institutionid: String = "",
        @Value("\${eessi-pensjon-institusjon-navn}") val institutionnavn: String = "",
        @Value("\${eessi.pensjon_adresse_gate}") val institutionGate: String = "",
        @Value("\${eessi.pensjon_adresse_by}") val institutionBy: String = "",
        @Value("\${eessi.pensjon_adresse_postnummer}") val institutionPostnr: String = "",
        @Value("\${eessi.pensjon_adresse_land}") val institutionLand: String = ""
) {
    fun asAndreinstitusjonerItem() =
            AndreinstitusjonerItem(
                    institusjonsid = institutionid,
                    institusjonsnavn = institutionnavn,
                    institusjonsadresse = institutionGate,
                    postnummer = institutionPostnr,
                    bygningsnr = null,
                    land = institutionLand,
                    region = null,
                    poststed = institutionBy
            )
}
