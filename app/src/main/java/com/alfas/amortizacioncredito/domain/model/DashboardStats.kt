package com.alfas.amortizacioncredito.domain.model

enum class ClientLoanStatus {
    AL_DIA,
    VENCE_PRONTO,
    EN_MORA,
    LIQUIDADO
}

data class DashboardClientSummary(
    val id: String,
    val name: String,
    val documentOrPhone: String,
    val startDate: String,
    val totalFinanced: Double,
    val totalPaid: Double,
    val remainingBalance: Double,
    val paidMonths: Int,
    val totalMonths: Int,
    val nextDueDate: String,
    val nextPaymentAmount: Double,
    val status: ClientLoanStatus,
    val progress: Float // 0f to 1f
)

data class DashboardStats(
    val totalClients: Int = 0,
    val totalFinanced: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalPendingBalance: Double = 0.0,
    val collectionRate: Float = 0f, // 0f to 100f
    val overdueClientsCount: Int = 0,
    val dueThisMonthCount: Int = 0,
    val fullyPaidClientsCount: Int = 0,
    val clientSummaries: List<DashboardClientSummary> = emptyList()
)
