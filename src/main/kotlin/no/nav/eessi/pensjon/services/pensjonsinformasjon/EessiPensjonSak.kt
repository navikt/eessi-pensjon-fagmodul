package no.nav.eessi.pensjon.services.pensjonsinformasjon

data class EessiPensjonSak (
    val sakId: String,
    val sakType: EessiFellesDto.EessiSakType,
    val sakStatus: EessiFellesDto.EessiSakStatus
)