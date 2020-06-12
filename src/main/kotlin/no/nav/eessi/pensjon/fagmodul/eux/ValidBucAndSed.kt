package no.nav.eessi.pensjon.fagmodul.eux

class ValidBucAndSed {

    fun getAvailableSedOnBuc(bucType: String?): List<String> {
        val map = initSedOnBuc()

        if (bucType.isNullOrEmpty()) {
            val set = mutableSetOf<String>()
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
    fun initSedOnBuc(): Map<String, List<String>> {
        return mapOf(
                "P_BUC_01" to listOf("P2000"),
                "P_BUC_02" to listOf("P2100"),
                "P_BUC_03" to listOf("P2200"),
                "P_BUC_05" to listOf("P8000"),
                "P_BUC_06" to listOf("P5000", "P6000", "P7000", "P10000"),
                "P_BUC_09" to listOf("P14000"),
                "P_BUC_10" to listOf("P15000"),
                "P_BUC_04" to listOf("P1000"),
                "P_BUC_07" to listOf("P11000"),
                "P_BUC_08" to listOf("P12000")
        )
    }

}