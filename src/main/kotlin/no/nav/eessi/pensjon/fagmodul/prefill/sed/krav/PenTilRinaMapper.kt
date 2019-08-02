package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import org.springframework.stereotype.Component
import java.lang.Exception


@Component
class PenTilRinaMapper {
    /**
     *  [07] Førtidspensjon
     *  [08] Uførepensjon
     *  [10] Alderspensjon
     *  [11] Etterlattepensjon
     */
    fun mapSaktype(saktype: String): String {
        return try {
            return when (Saktype.valueOf(saktype)) {
                Saktype.ALDER -> "10"
                Saktype.GJENLEV -> "11"
                Saktype.UFOREP -> "08"
                else -> "07"
            }
        } catch (ex: Exception) {
            "07"
        }
    }

    /**
     *  [01] Søkt
     *  [02] Innvilget
     *  [03] Avslått
     */
    fun mapSakstatus(sakstatus: String): String {
        return try {
            when (Sakstatus.valueOf(sakstatus)) {
                Sakstatus.INNV -> "02"
                Sakstatus.AVSL -> "03"
            }
        } catch (ex: Exception) {
            "01"
        }
    }

    /**
     *  [01] Botid
     *  [02] I arbeid
     */
    fun mapPensjonBasertPå(saktype: String): String? {
        return try {
            return when (Saktype.valueOf(saktype)) {
                Saktype.ALDER -> "02"
                Saktype.UFOREP -> "01"
                Saktype.GJENLEV -> "01"
                else -> null
            }
        } catch (ex: Exception) {
            null
        }
    }
}