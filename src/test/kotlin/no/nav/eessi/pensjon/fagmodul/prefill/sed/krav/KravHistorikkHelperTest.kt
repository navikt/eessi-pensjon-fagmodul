package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import org.junit.jupiter.api.Test

internal class KravHistorikkHelperTest {

    @Test
    fun `Gitt en liste med krav som har gjenlevende årsak når kravhistorikk blir hentet så returneres kravhistorikk med enten tilstøtende_dødsfall eller gjenlevende_tilegg`() {
        val gjenlevendHistorikk =  V1KravHistorikk()
        gjenlevendHistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val kravListe = mutableListOf(gjenlevendHistorikk)
        val kravHistorikkListe = V1KravHistorikkListe()
        kravHistorikkListe.kravHistorikkListe.addAll(kravListe)
        val actual = KravHistorikkHelper.hentKravhistorikkForGjenlevende(kravHistorikkListe)

        assert(actual?.kravArsak == KravArsak.GJNL_SKAL_VURD.name)

        val tilsDodHistorikk =  V1KravHistorikk()
        gjenlevendHistorikk.kravArsak = KravArsak.TILST_DOD.name
        val kravListeDod = mutableListOf(tilsDodHistorikk, gjenlevendHistorikk)
        val kravHistorikkListeDod = V1KravHistorikkListe()
        kravHistorikkListeDod.kravHistorikkListe.addAll(kravListeDod)
        val actuaDod = KravHistorikkHelper.hentKravhistorikkForGjenlevende(kravHistorikkListeDod)

        assert(actuaDod?.kravArsak == KravArsak.TILST_DOD.name)
    }
}