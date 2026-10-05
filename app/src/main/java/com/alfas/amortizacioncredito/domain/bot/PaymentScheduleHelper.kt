package com.alfas.amortizacioncredito.domain.bot

import com.alfas.amortizacioncredito.data.model.AmortizationItem
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.domain.usecase.CalculateAmortizationUseCase

class PaymentScheduleHelper(
    private val calculateAmortizationUseCase: CalculateAmortizationUseCase = CalculateAmortizationUseCase()
) {

    fun getAmortizationSchedule(client: ClientProfile): List<AmortizationItem> {
        val price = client.price.toDoubleOrNull() ?: 0.0
        val downPayment = client.downPayment.toDoubleOrNull() ?: 0.0
        val annualRate = client.annualRate.toDoubleOrNull() ?: 0.0
        val years = client.years.toDoubleOrNull() ?: 1.0

        val summary = calculateAmortizationUseCase(
            price = price,
            downPayment = downPayment,
            annualRate = annualRate,
            years = years,
            roundUp = client.isRoundUpEnabled,
            isCompoundInterest = client.isCompoundInterest,
            isFixedPriceLoan = client.isFixedPriceLoan,
            extraPaymentMonth = client.extraPaymentMonth,
            extraPaymentAmount = client.extraPaymentAmount,
            startDate = client.startDate,
            paymentRecords = client.paymentRecords
        )
        return summary.schedule
    }

    fun getPendingInstallments(client: ClientProfile): List<AmortizationItem> {
        val schedule = getAmortizationSchedule(client)
        return schedule.filter { item ->
            val record = client.paymentRecords[item.month]
            record == null || !record.isPaid
        }
    }

    fun getNextPendingInstallment(client: ClientProfile): AmortizationItem? {
        return getPendingInstallments(client).firstOrNull()
    }

    fun isInstallmentPaid(client: ClientProfile, month: Int): Boolean {
        val record = client.paymentRecords[month]
        return record?.isPaid == true
    }

    fun getExistingPaymentRecord(client: ClientProfile, month: Int): PaymentRecord? {
        return client.paymentRecords[month]
    }
}
