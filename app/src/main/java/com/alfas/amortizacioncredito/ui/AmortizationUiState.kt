package com.alfas.amortizacioncredito.ui

import com.alfas.amortizacioncredito.domain.model.DashboardStats
import com.alfas.amortizacioncredito.data.model.AmortizationSummary
import com.alfas.amortizacioncredito.data.model.AppNotification
import com.alfas.amortizacioncredito.data.model.AppUpdateInfo
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.data.model.AuditLogEntry
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.data.model.VoucherConfig

enum class AppScreen {
    LOGIN,
    DASHBOARD,
    SIMULATOR
}

data class AmortizationUiState(
    val currentScreen: AppScreen = AppScreen.DASHBOARD,
    val priceInput: String = "86000",
    val downPaymentInput: String = "40000",
    val annualRateInput: String = "3.0",
    val yearsInput: String = "5",
    val startDateInput: String = "",
    val desiredMonthlyPaymentInput: String = "",
    val isRoundUpEnabled: Boolean = false,
    val hideInterest: Boolean = false,
    val isCompoundInterest: Boolean = false,
    val isFixedPriceLoan: Boolean = false,
    val extraPaymentMonth: Int = 0,
    val extraPaymentAmount: Double = 0.0,
    val activeClient: ClientProfile? = null,
    val allClients: List<ClientProfile> = emptyList(),
    val paymentRecords: Map<Int, PaymentRecord> = emptyMap(),
    val businessProfile: BusinessProfile = BusinessProfile(),
    val dashboardStats: DashboardStats = DashboardStats(),
    val currentUser: AppUser? = null,
    val allUsers: List<AppUser> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val appUpdateInfo: AppUpdateInfo? = null,
    val summary: AmortizationSummary? = null,
    val voucherConfig: VoucherConfig = VoucherConfig()
)

