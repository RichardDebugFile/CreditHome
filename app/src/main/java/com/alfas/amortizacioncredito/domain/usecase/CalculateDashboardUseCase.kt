package com.alfas.amortizacioncredito.domain.usecase

import com.alfas.amortizacioncredito.data.model.ClientProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.alfas.amortizacioncredito.domain.model.ClientLoanStatus
import com.alfas.amortizacioncredito.domain.model.DashboardClientSummary
import com.alfas.amortizacioncredito.domain.model.DashboardStats

class CalculateDashboardUseCase(
    private val calculateAmortizationUseCase: CalculateAmortizationUseCase = CalculateAmortizationUseCase()
) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    operator fun invoke(clients: List<ClientProfile>): DashboardStats {
        if (clients.isEmpty()) {
            return DashboardStats()
        }

        var totalFinancedAll = 0.0
        var totalCollectedAll = 0.0
        var totalPendingBalanceAll = 0.0
        var overdueCount = 0
        var dueThisMonthCount = 0
        var fullyPaidCount = 0

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = todayCal.time
        val currentMonth = todayCal.get(Calendar.MONTH)
        val currentYear = todayCal.get(Calendar.YEAR)

        val clientSummaries = mutableListOf<DashboardClientSummary>()

        for (client in clients) {
            val price = client.price.toDoubleOrNull() ?: 0.0
            val downPayment = client.downPayment.toDoubleOrNull() ?: 0.0
            val rate = client.annualRate.toDoubleOrNull() ?: 0.0
            val years = client.years.toDoubleOrNull() ?: 0.0

            val summary = calculateAmortizationUseCase(
                price = price,
                downPayment = downPayment,
                annualRate = rate,
                years = years,
                roundUp = client.isRoundUpEnabled,
                isCompoundInterest = client.isCompoundInterest,
                isFixedPriceLoan = client.isFixedPriceLoan,
                extraPaymentMonth = client.extraPaymentMonth,
                extraPaymentAmount = client.extraPaymentAmount,
                startDate = client.startDate,
                paymentRecords = client.paymentRecords
            )

            val financed = summary.financedAmount
            val totalMonths = maxOf(1, summary.actualMonths)

            val paidPayments = client.paymentRecords.values.filter { it.isPaid }
            val paidMonths = paidPayments.size
            val totalPaidAmount = paidPayments.sumOf { it.amountPaid }

            val lastPaidMonth = client.paymentRecords.filter { it.value.isPaid }.keys.maxOrNull() ?: 0
            val remainingBal = if (paidMonths >= totalMonths) {
                0.0
            } else if (lastPaidMonth > 0) {
                summary.schedule.find { it.month == lastPaidMonth }?.remainingBalance ?: financed
            } else {
                financed
            }

            // Próxima cuota pendiente
            val nextUnpaidItem = summary.schedule.find { item ->
                client.paymentRecords[item.month]?.isPaid != true
            }

            val nextDueDate = nextUnpaidItem?.dueDate ?: ""
            val nextAmount = nextUnpaidItem?.payment ?: 0.0

            var status = ClientLoanStatus.AL_DIA
            if (paidMonths >= totalMonths && financed > 0) {
                status = ClientLoanStatus.LIQUIDADO
                fullyPaidCount++
            } else if (nextDueDate.isNotBlank()) {
                try {
                    val dueDateObj = dateFormat.parse(nextDueDate)
                    if (dueDateObj != null) {
                        val dueCal = Calendar.getInstance().apply { time = dueDateObj }
                        if (dueDateObj.before(today)) {
                            status = ClientLoanStatus.EN_MORA
                            overdueCount++
                        } else if (dueCal.get(Calendar.MONTH) == currentMonth && dueCal.get(Calendar.YEAR) == currentYear) {
                            status = ClientLoanStatus.VENCE_PRONTO
                            dueThisMonthCount++
                        }
                    }
                } catch (_: Exception) {}
            }

            val progress = if (totalMonths > 0) (paidMonths.toFloat() / totalMonths.toFloat()).coerceIn(0f, 1f) else 0f

            totalFinancedAll += financed
            totalCollectedAll += totalPaidAmount
            totalPendingBalanceAll += remainingBal

            clientSummaries.add(
                DashboardClientSummary(
                    id = client.id,
                    name = client.name,
                    documentOrPhone = client.displayDocumentOrPhone,
                    startDate = client.startDate,
                    totalFinanced = financed,
                    totalPaid = totalPaidAmount,
                    remainingBalance = remainingBal,
                    paidMonths = paidMonths,
                    totalMonths = totalMonths,
                    nextDueDate = nextDueDate,
                    nextPaymentAmount = nextAmount,
                    status = status,
                    progress = progress
                )
            )
        }

        // Ordenar clientes: primero los que tienen mora, luego los que vencen pronto, luego al día, luego liquidados
        clientSummaries.sortWith(
            compareBy<DashboardClientSummary> {
                when (it.status) {
                    ClientLoanStatus.EN_MORA -> 0
                    ClientLoanStatus.VENCE_PRONTO -> 1
                    ClientLoanStatus.AL_DIA -> 2
                    ClientLoanStatus.LIQUIDADO -> 3
                }
            }.thenBy { it.name }
        )

        val collectionRate = if (totalFinancedAll > 0.0) {
            ((totalCollectedAll / totalFinancedAll) * 100.0).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }

        return DashboardStats(
            totalClients = clients.size,
            totalFinanced = totalFinancedAll,
            totalCollected = totalCollectedAll,
            totalPendingBalance = totalPendingBalanceAll,
            collectionRate = collectionRate,
            overdueClientsCount = overdueCount,
            dueThisMonthCount = dueThisMonthCount,
            fullyPaidClientsCount = fullyPaidCount,
            clientSummaries = clientSummaries
        )
    }
}
