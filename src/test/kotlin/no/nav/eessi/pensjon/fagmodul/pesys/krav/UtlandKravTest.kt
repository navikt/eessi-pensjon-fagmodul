package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.Utsettelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate

@Disabled
internal class UtlandKravTest {

    @Test
    @Disabled
    fun `Virkningsdato skal bli første dag i mnd når utsettelsesdato er mellom 1-15 i måneden `() {
        val trettende = LocalDate.now().withDayOfMonth(13)
        val kravSed  = SED(SedType.P2000, pensjon = Pensjon(utsettelse = listOf(Utsettelse(tildato = trettende.toString()))))

        val virkningsDato = UtlandKrav().virkningsDato(kravSed, trettende)
        val resultat = trettende.withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)

    }

    @Test
    @Disabled
    fun `Virkningsdato skal bli den første dagen i inneværende mnd når utsettelsesdato er 15 `() {
        val femtende = LocalDate.now().withDayOfMonth(15)
        val kravSed  = SED(SedType.P2000, pensjon = Pensjon(utsettelse = listOf(Utsettelse(tildato = femtende.toString()))))

        val virkningsDato = UtlandKrav().virkningsDato(kravSed, femtende)
        val resultat = femtende.withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)

    }

    @Test
    @Disabled
    fun `Virkningsdato skal første dag i neste mnd når utsettelsesdato er mellom 16 og siste dag i inneværende mnd `() {
        val syttende = LocalDate.now().withDayOfMonth(17)
        val kravSed  = SED(SedType.P2000, pensjon = Pensjon(utsettelse = listOf(Utsettelse(tildato = syttende.toString()))))

        val virkningsDato = UtlandKrav().virkningsDato(kravSed, syttende)
        val resultat = syttende.plusMonths(1).withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)

    }

    @Test
    @Disabled
    fun `Virkningsdato skal bli første dag i neste mnd dersom ingen utsettelsesdato finnes bruker da mottattdato `() {
        val mottattDato = LocalDate.now().withDayOfMonth(10)
        val kravSed  = SED(SedType.P2000)


        val virkningsDato = UtlandKrav().virkningsDato(kravSed, mottattDato)
        val resultat = mottattDato.plusMonths(1).withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)
    }

}