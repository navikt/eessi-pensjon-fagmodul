package no.nav.eessi.pensjon.fagmodul.models

import java.util.*

//fra UI til Fagmodul (person info)
data class BrukerInformasjon(
        //bank
        val bankAddress: String?,
        val bankBicSwift: String?,
        val bankCode: String?,
        val bankCountry: BankCountry?,
        val bankIban: String?,
        val bankName: String?,
        val userEmail: String?,
        val userPhone: String?,
        //work
        val retirementCountry: RetirementCountry?,
        val workType: String?,
        val workHourPerWeek: String?,
        val workIncome: String?,
        val workIncomeCurrency: WorkIncomeCurrency?,
        val workPaymentDate: String?,
        val workPaymentFrequency: String?,
        val workEstimatedRetirementDate: Date?,
        val workStartDate: Date?,
        val workEndDate: Date?

)

data class BankCountry(
        val currency: String?,
        val currencyLabel: String?,
        val label: String?,
        val value: String?
)

data class WorkIncomeCurrency(
        val currency: String?,
        val currencyLabel: String?,
        val label: String?,
        val value: String?
)

data class RetirementCountry(
        val currency: String?,
        val currencyLabel: String?,
        val label: String?,
        val value: String?
)