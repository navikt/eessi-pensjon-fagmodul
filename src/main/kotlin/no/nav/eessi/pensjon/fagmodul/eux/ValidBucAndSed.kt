package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.SedType

open class ValidBucAndSed {

    fun getAvailableSedOnBuc(bucType: String?): List<SedType> {
        val map = initSedOnBuc()

        if (bucType.isNullOrEmpty()) {
            val set = mutableSetOf<SedType>()
            map["P_BUC_01"]?.let { set.addAll(it) }
            map["P_BUC_02"]?.let { set.addAll(it) }
            map["P_BUC_03"]?.let { set.addAll(it) }
            map["P_BUC_05"]?.let { set.addAll(it) }
            map["P_BUC_06"]?.let { set.addAll(it) }
            map["P_BUC_09"]?.let { set.addAll(it) }
            map["P_BUC_10"]?.let { set.addAll(it) }
            return set.toList()
        }
        return map[bucType].orEmpty()
    }

    /**
     * Own impl. no list from eux that contains list of SED to a speific BUC
     */
    fun initSedOnBuc(): Map<String, List<SedType>> {
        return mapOf(
                "P_BUC_01" to listOf(SedType.P2000),
                "P_BUC_02" to listOf(SedType.P2100),
                "P_BUC_03" to listOf(SedType.P2200),
                "P_BUC_05" to listOf(SedType.P8000),
                "P_BUC_06" to listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000),
                "P_BUC_09" to listOf(SedType.P14000),
                "P_BUC_10" to listOf(SedType.P15000),
                "P_BUC_04" to listOf(SedType.P1000),
                "P_BUC_07" to listOf(SedType.P11000),
                "P_BUC_08" to listOf(SedType.P12000)
        )
    }

}