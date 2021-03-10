package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.services.pensjonsinformasjon.Sakstatus

/**
     *  [07] Førtidspensjon
     *  [08] Uførepensjon
     *  [10] Alderspensjon
     *  [11] Etterlattepensjon
     */
     fun mapSaktype(saktype: String?): String {
            return when (saktype) {
                "UFOREP" -> "08"
                "ALDER" -> "10"
                "GJENLEV" -> "11"
                "BARNEP" -> "11"
                else -> "07"
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
                else -> "01"
            }
        } catch (ex: Exception) {
            "01"
        }
    }
