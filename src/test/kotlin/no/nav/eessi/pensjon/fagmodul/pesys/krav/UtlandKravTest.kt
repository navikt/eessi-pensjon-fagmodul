package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.Utsettelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UtlandKravTest {

    @Test
    fun `Virkningsdato skal bli første dag i mnd når utsettelsesdato er mellom 1-15 i måneden `() {
        val trettende = LocalDate.now().withDayOfMonth(13)
        val kravSed  = SED(SedType.P2000, pensjon = Pensjon(utsettelse = listOf(Utsettelse(tildato = trettende.toString()))))

        val virkningsDato = UtlandKrav().virkningsDato(kravSed, trettende)
        val resultat = trettende.withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)

    }

    @Test
    fun `Virkningsdato skal bli den første dagen i inneværende mnd når utsettelsesdato er 15 `() {
        val femtende = LocalDate.now().withDayOfMonth(15)
        val kravSed  = SED(SedType.P2000, pensjon = Pensjon(utsettelse = listOf(Utsettelse(tildato = femtende.toString()))))

        val virkningsDato = UtlandKrav().virkningsDato(kravSed, femtende)
        val resultat = femtende.withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)

    }

    @Test
    fun `Virkningsdato skal bli første dag i neste mnd dersom ingen utsettelsesdato finnes bruker da mottattdato `() {
        val kravDato = LocalDate.now().withDayOfMonth(10)

        val kravSed  = SED(SedType.P2000, pensjon = Pensjon(kravDato = Krav(dato = kravDato.toString(), null)))

        val virkningsDato = UtlandKrav().virkningsDato(kravSed, kravDato)
        val resultat = kravDato.plusMonths(1).withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)
    }

    @Test
    fun `Virkningsdato skal bli første dag i neste mnd dersom ingen utsettelsesdato finnes men benytter kravdato fra sed `() {
        val mottattDato = LocalDate.now().withDayOfMonth(10)
        val kravSed  = SED(SedType.P2000)


        val virkningsDato = UtlandKrav().virkningsDato(kravSed, mottattDato)
        val resultat = mottattDato.plusMonths(1).withDayOfMonth(1)
        assertEquals(resultat, virkningsDato)
    }

}