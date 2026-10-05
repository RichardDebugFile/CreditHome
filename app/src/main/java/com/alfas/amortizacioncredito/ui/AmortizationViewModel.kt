package com.alfas.amortizacioncredito.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfas.amortizacioncredito.data.feature.FeatureFlagManager
import com.alfas.amortizacioncredito.data.feature.FeatureKey
import com.alfas.amortizacioncredito.data.local.AmortizationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.data.model.RabbitMqConfig
import com.alfas.amortizacioncredito.data.model.SmtpConfig
import com.alfas.amortizacioncredito.data.model.UserRole
import com.alfas.amortizacioncredito.data.model.VoucherConfig
import com.alfas.amortizacioncredito.data.repository.AuditRepository
import com.alfas.amortizacioncredito.data.repository.AuthRepository
import com.alfas.amortizacioncredito.data.repository.BusinessProfileRepository
import com.alfas.amortizacioncredito.data.repository.ClientRepository
import com.alfas.amortizacioncredito.data.repository.RabbitMqRepository
import com.alfas.amortizacioncredito.data.repository.SmtpRepository
import com.alfas.amortizacioncredito.data.repository.VoucherConfigRepository
import com.alfas.amortizacioncredito.domain.security.SecurityManager
import com.alfas.amortizacioncredito.domain.service.AppUpdateManager
import com.alfas.amortizacioncredito.domain.service.RabbitMqQueueManager
import com.alfas.amortizacioncredito.domain.usecase.CalculateAmortizationUseCase
import com.alfas.amortizacioncredito.domain.usecase.CalculateDashboardUseCase
import com.alfas.amortizacioncredito.domain.usecase.EmailNotificationUseCase
import com.alfas.amortizacioncredito.domain.usecase.NotificationManagerUseCase
import com.alfas.amortizacioncredito.util.ValidationUtils
import com.alfas.amortizacioncredito.data.model.AuditActionType
import com.alfas.amortizacioncredito.data.model.OutboxQueueMessage
import com.alfas.amortizacioncredito.data.model.UserPermissions

class AmortizationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val calculateUseCase = CalculateAmortizationUseCase()
    private val calculateDashboardUseCase = CalculateDashboardUseCase(calculateUseCase)
    private val preferences = AmortizationPreferences(application)
    private val clientRepository = ClientRepository(application)
    private val businessProfileRepository = BusinessProfileRepository(application)
    private val voucherConfigRepository = VoucherConfigRepository(application)
    private val featureFlagManager = FeatureFlagManager(application)
    private val authRepository = AuthRepository(application)
    private val auditRepository = AuditRepository(application)
    private val notificationManager = NotificationManagerUseCase(application)
    private val appUpdateManager = AppUpdateManager(application, featureFlagManager)
    private val smtpRepository = SmtpRepository(application)
    private val rabbitMqRepository = RabbitMqRepository(application)
    val emailNotificationUseCase = EmailNotificationUseCase(application, smtpRepository)
    val rabbitMqQueueManager = RabbitMqQueueManager(application, rabbitMqRepository)

    private val _uiState: MutableStateFlow<AmortizationUiState> = MutableStateFlow(
        run {
            val baseState = preferences.loadState()
            val allClients = clientRepository.getAllClients()
            val savedActiveId = preferences.getActiveClientId()
            val activeClient = if (savedActiveId != null) allClients.find { it.id == savedActiveId } else null
            val businessProfile = businessProfileRepository.getProfile()
            val voucherConfig = voucherConfigRepository.getVoucherConfig()
            val dashboardStats = calculateDashboardUseCase(allClients)
            val isAuthEnabled = featureFlagManager.isEnabled(FeatureKey.AUTH_AND_ROLES)
            val sessionUser = if (isAuthEnabled) authRepository.getCurrentSessionUser() else null
            val allUsers = authRepository.getAllUsers()
            val notifications = notificationManager.scanAndGenerateLoanNotifications(allClients)
            val auditLogs = auditRepository.getAllLogs()

            val initialScreen = if (isAuthEnabled && sessionUser == null) AppScreen.LOGIN else AppScreen.DASHBOARD

            if (activeClient != null) {
                baseState.copy(
                    currentScreen = initialScreen,
                    activeClient = activeClient,
                    allClients = allClients,
                    priceInput = activeClient.price,
                    downPaymentInput = activeClient.downPayment,
                    annualRateInput = activeClient.annualRate,
                    yearsInput = activeClient.years,
                    startDateInput = activeClient.startDate,
                    isRoundUpEnabled = activeClient.isRoundUpEnabled,
                    hideInterest = activeClient.hideInterest,
                    isCompoundInterest = activeClient.isCompoundInterest,
                    isFixedPriceLoan = activeClient.isFixedPriceLoan,
                    extraPaymentMonth = activeClient.extraPaymentMonth,
                    extraPaymentAmount = activeClient.extraPaymentAmount,
                    paymentRecords = activeClient.paymentRecords,
                    businessProfile = businessProfile,
                    voucherConfig = voucherConfig,
                    dashboardStats = dashboardStats,
                    currentUser = sessionUser,
                    allUsers = allUsers,
                    notifications = notifications,
                    auditLogs = auditLogs
                )
            } else {
                baseState.copy(
                    currentScreen = initialScreen,
                    allClients = allClients,
                    businessProfile = businessProfile,
                    voucherConfig = voucherConfig,
                    dashboardStats = dashboardStats,
                    currentUser = sessionUser,
                    allUsers = allUsers,
                    notifications = notifications,
                    auditLogs = auditLogs
                )
            }
        }
    )
    val uiState: StateFlow<AmortizationUiState> = _uiState.asStateFlow()

    init {
        recalculate()
        checkForUpdates()
    }

    fun isFeatureEnabled(key: FeatureKey): Boolean = featureFlagManager.isEnabled(key)

    fun navigateToDashboard() {
        _uiState.update { it.copy(currentScreen = AppScreen.DASHBOARD) }
    }

    fun navigateToSimulator(client: ClientProfile? = null) {
        if (client != null) {
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.SIMULATOR,
                    activeClient = client,
                    priceInput = client.price,
                    downPaymentInput = client.downPayment,
                    annualRateInput = client.annualRate,
                    yearsInput = client.years,
                    startDateInput = client.startDate,
                    desiredMonthlyPaymentInput = "",
                    isRoundUpEnabled = client.isRoundUpEnabled,
                    hideInterest = client.hideInterest,
                    isCompoundInterest = client.isCompoundInterest,
                    extraPaymentMonth = client.extraPaymentMonth,
                    extraPaymentAmount = client.extraPaymentAmount,
                    paymentRecords = client.paymentRecords
                )
            }
            persistAndRecalculate()
        } else {
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.SIMULATOR,
                    activeClient = null,
                    priceInput = "0",
                    downPaymentInput = "0",
                    annualRateInput = "0",
                    yearsInput = "1",
                    startDateInput = "",
                    desiredMonthlyPaymentInput = "",
                    extraPaymentMonth = 0,
                    extraPaymentAmount = 0.0,
                    paymentRecords = emptyMap()
                )
            }
            preferences.saveActiveClientId(null)
            persistAndRecalculate()
        }
    }

    fun resetSimulator() {
        // Solo para modo simulador libre: resetear todos los campos a 0
        _uiState.update {
            it.copy(
                activeClient = null,
                priceInput = "0",
                downPaymentInput = "0",
                annualRateInput = "0",
                yearsInput = "1",
                startDateInput = "",
                desiredMonthlyPaymentInput = "",
                extraPaymentMonth = 0,
                extraPaymentAmount = 0.0,
                paymentRecords = emptyMap()
            )
        }
        preferences.saveActiveClientId(null)
        persistAndRecalculate()
    }

    fun onStartDateChanged(value: String) {
        _uiState.update { it.copy(startDateInput = value) }
        persistAndRecalculate()
    }

    fun onPriceChanged(value: String) {
        val clean = com.alfas.amortizacioncredito.util.ValidationUtils.filterDecimalInput(value)
        _uiState.update { it.copy(priceInput = clean) }
        recalculateYearsIfDesiredPaymentActive()
        persistAndRecalculate()
    }

    fun onDownPaymentChanged(value: String) {
        val clean = com.alfas.amortizacioncredito.util.ValidationUtils.filterDecimalInput(value)
        _uiState.update { it.copy(downPaymentInput = clean) }
        recalculateYearsIfDesiredPaymentActive()
        persistAndRecalculate()
    }

    fun onRateChanged(value: String) {
        val clean = com.alfas.amortizacioncredito.util.ValidationUtils.filterDecimalInput(value)
        _uiState.update { it.copy(annualRateInput = clean) }
        recalculateYearsIfDesiredPaymentActive()
        persistAndRecalculate()
    }

    fun onYearsChanged(value: String) {
        val clean = com.alfas.amortizacioncredito.util.ValidationUtils.filterDecimalInput(value)
        _uiState.update {
            it.copy(
                yearsInput = clean,
                desiredMonthlyPaymentInput = ""
            )
        }
        persistAndRecalculate()
    }

    fun onDesiredMonthlyPaymentChanged(value: String) {
        val clean = com.alfas.amortizacioncredito.util.ValidationUtils.filterDecimalInput(value)
        _uiState.update { it.copy(desiredMonthlyPaymentInput = clean) }
        recalculateYearsIfDesiredPaymentActive()
        persistAndRecalculate()
    }

    fun clearDesiredMonthlyPayment() {
        _uiState.update { it.copy(desiredMonthlyPaymentInput = "") }
        persistAndRecalculate()
    }

    private fun recalculateYearsIfDesiredPaymentActive() {
        val current = _uiState.value
        val desiredPayment = current.desiredMonthlyPaymentInput.toDoubleOrNull() ?: return
        if (desiredPayment <= 0.0) return

        val price = current.priceInput.toDoubleOrNull() ?: 0.0
        val downPayment = current.downPaymentInput.toDoubleOrNull() ?: 0.0
        val financed = maxOf(0.0, price - downPayment)
        val rate = current.annualRateInput.toDoubleOrNull() ?: 0.0
        val monthlyRate = (rate / 100.0) / 12.0
        val minMonthlyInterest = financed * monthlyRate

        if (financed > 0.0 && desiredPayment > minMonthlyInterest) {
            var bal = financed
            var monthCount = 0
            while (bal > 0.001 && monthCount < 1200) {
                monthCount++
                val interest = bal * monthlyRate
                val principalAvailable = desiredPayment - interest
                if (principalAvailable >= bal) {
                    bal = 0.0
                    break
                } else {
                    bal -= principalAvailable
                }
            }

            if (monthCount > 0) {
                val yearsStr = if (monthCount % 12 == 0) {
                    (monthCount / 12).toString()
                } else {
                    String.format(java.util.Locale.US, "%.2f", monthCount / 12.0)
                }
                _uiState.update { it.copy(yearsInput = yearsStr) }
            }
        }
    }

    fun toggleRoundUp(enabled: Boolean) {
        _uiState.update { it.copy(isRoundUpEnabled = enabled) }
        persistAndRecalculate()
    }

    fun toggleHideInterest(hide: Boolean) {
        _uiState.update { it.copy(hideInterest = hide) }
        persistAndRecalculate()
    }

    fun toggleCompoundInterest(enabled: Boolean) {
        _uiState.update { it.copy(isCompoundInterest = enabled) }
        persistAndRecalculate()
    }

    fun setExtraPayment(month: Int, amount: Double) {
        _uiState.update {
            it.copy(
                extraPaymentMonth = month,
                extraPaymentAmount = amount
            )
        }
        persistAndRecalculate()
    }

    fun clearExtraPayment() {
        _uiState.update {
            it.copy(
                extraPaymentMonth = 0,
                extraPaymentAmount = 0.0
            )
        }
        persistAndRecalculate()
    }

    // --- Métodos de Gestión de Clientes ---

    fun selectClient(client: ClientProfile?) {
        if (client == null) {
            _uiState.update {
                it.copy(
                    activeClient = null,
                    desiredMonthlyPaymentInput = "",
                    paymentRecords = emptyMap()
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    activeClient = client,
                    priceInput = client.price,
                    downPaymentInput = client.downPayment,
                    annualRateInput = client.annualRate,
                    yearsInput = client.years,
                    startDateInput = client.startDate,
                    desiredMonthlyPaymentInput = "",
                    isRoundUpEnabled = client.isRoundUpEnabled,
                    hideInterest = client.hideInterest,
                    isCompoundInterest = client.isCompoundInterest,
                    isFixedPriceLoan = client.isFixedPriceLoan,
                    extraPaymentMonth = client.extraPaymentMonth,
                    extraPaymentAmount = client.extraPaymentAmount,
                    paymentRecords = client.paymentRecords
                )
            }
        }
        persistAndRecalculate()
    }

    fun createNewClient(
        name: String,
        documentNumber: String = "",
        phone: String = "",
        address: String = "",
        documentOrPhone: String = "",
        email: String = "",
        notes: String = "",
        price: String = _uiState.value.priceInput,
        downPayment: String = _uiState.value.downPaymentInput,
        annualRate: String = _uiState.value.annualRateInput,
        years: String = _uiState.value.yearsInput,
        startDate: String = _uiState.value.startDateInput,
        isFixedPriceLoan: Boolean = _uiState.value.isFixedPriceLoan
    ) {
        val legacyDocOrPhone = documentOrPhone.ifEmpty {
            when {
                documentNumber.isNotBlank() && phone.isNotBlank() -> "$documentNumber | $phone"
                documentNumber.isNotBlank() -> documentNumber
                else -> phone
            }
        }

        val newClient = ClientProfile(
            name = name,
            documentNumber = documentNumber.trim(),
            phone = phone.trim(),
            address = address.trim(),
            documentOrPhone = legacyDocOrPhone.trim(),
            email = email.trim(),
            price = price,
            downPayment = downPayment,
            annualRate = annualRate,
            years = years,
            startDate = startDate,
            isRoundUpEnabled = _uiState.value.isRoundUpEnabled,
            hideInterest = _uiState.value.hideInterest,
            isCompoundInterest = _uiState.value.isCompoundInterest,
            isFixedPriceLoan = isFixedPriceLoan,
            extraPaymentMonth = _uiState.value.extraPaymentMonth,
            extraPaymentAmount = _uiState.value.extraPaymentAmount,
            notes = notes,
            paymentRecords = emptyMap()
        )

        clientRepository.saveOrUpdateClient(newClient)
        val updatedClients = clientRepository.getAllClients()

        auditRepository.log(
            action = AuditActionType.CREATE_CLIENT,
            description = "Cliente '${newClient.name}' registrado en cartera (Monto: $${newClient.price})",
            username = _uiState.value.currentUser?.displayName ?: "Usuario Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName.ifBlank { "CreditHome" },
            details = "Doc/RUC: ${newClient.documentNumber.ifEmpty { "N/D" }} | Tel: ${newClient.phone.ifEmpty { "N/D" }}"
        )

        _uiState.update {
            it.copy(
                currentScreen = AppScreen.SIMULATOR,
                activeClient = newClient,
                allClients = updatedClients,
                priceInput = price,
                downPaymentInput = downPayment,
                annualRateInput = annualRate,
                yearsInput = years,
                startDateInput = startDate,
                isFixedPriceLoan = isFixedPriceLoan,
                desiredMonthlyPaymentInput = "",
                paymentRecords = emptyMap(),
                auditLogs = auditRepository.getAllLogs()
            )
        }
        persistAndRecalculate()
    }

    fun updateActiveClient(
        name: String,
        documentNumber: String = "",
        phone: String = "",
        address: String = "",
        documentOrPhone: String = "",
        email: String = "",
        notes: String = "",
        price: String = _uiState.value.priceInput,
        downPayment: String = _uiState.value.downPaymentInput,
        annualRate: String = _uiState.value.annualRateInput,
        years: String = _uiState.value.yearsInput,
        startDate: String = _uiState.value.startDateInput,
        isFixedPriceLoan: Boolean = _uiState.value.isFixedPriceLoan
    ) {
        val current = _uiState.value
        val existing = current.activeClient ?: return

        val legacyDocOrPhone = documentOrPhone.ifEmpty {
            when {
                documentNumber.isNotBlank() && phone.isNotBlank() -> "$documentNumber | $phone"
                documentNumber.isNotBlank() -> documentNumber
                phone.isNotBlank() -> phone
                else -> existing.documentOrPhone
            }
        }

        val updated = existing.copy(
            name = name,
            documentNumber = documentNumber.trim().ifEmpty { existing.documentNumber },
            phone = phone.trim().ifEmpty { existing.phone },
            address = address.trim().ifEmpty { existing.address },
            documentOrPhone = legacyDocOrPhone.trim(),
            email = email.trim(),
            price = price,
            downPayment = downPayment,
            annualRate = annualRate,
            years = years,
            startDate = startDate,
            isRoundUpEnabled = current.isRoundUpEnabled,
            hideInterest = current.hideInterest,
            isCompoundInterest = current.isCompoundInterest,
            isFixedPriceLoan = isFixedPriceLoan,
            extraPaymentMonth = current.extraPaymentMonth,
            extraPaymentAmount = current.extraPaymentAmount,
            notes = notes,
            paymentRecords = current.paymentRecords
        )

        clientRepository.saveOrUpdateClient(updated)
        val updatedClients = clientRepository.getAllClients()

        auditRepository.log(
            action = AuditActionType.EDIT_CLIENT,
            description = "Perfil del cliente '${updated.name}' actualizado",
            username = _uiState.value.currentUser?.displayName ?: "Usuario Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName.ifBlank { "CreditHome" }
        )

        _uiState.update {
            it.copy(
                activeClient = updated,
                allClients = updatedClients,
                priceInput = price,
                downPaymentInput = downPayment,
                annualRateInput = annualRate,
                yearsInput = years,
                startDateInput = startDate,
                desiredMonthlyPaymentInput = "",
                auditLogs = auditRepository.getAllLogs()
            )
        }
        persistAndRecalculate()
    }

    fun saveCurrentAsClient(
        name: String,
        documentNumber: String = "",
        phone: String = "",
        address: String = "",
        documentOrPhone: String = "",
        email: String = "",
        notes: String = "",
        price: String = _uiState.value.priceInput,
        downPayment: String = _uiState.value.downPaymentInput,
        annualRate: String = _uiState.value.annualRateInput,
        years: String = _uiState.value.yearsInput,
        startDate: String = _uiState.value.startDateInput
    ) {
        if (_uiState.value.activeClient != null) {
            updateActiveClient(name, documentNumber, phone, address, documentOrPhone, email, notes, price, downPayment, annualRate, years, startDate)
        } else {
            createNewClient(name, documentNumber, phone, address, documentOrPhone, email, notes, price, downPayment, annualRate, years, startDate)
        }
    }

    fun deleteClient(id: String) {
        val deletedClient = _uiState.value.allClients.find { it.id == id }
        clientRepository.deleteClient(id)
        val updatedClients = clientRepository.getAllClients()
        val currentActive = _uiState.value.activeClient
        val newActive = if (currentActive?.id == id) null else currentActive

        auditRepository.log(
            action = AuditActionType.DELETE_CLIENT,
            description = "Cliente '${deletedClient?.name ?: id}' eliminado de la cartera",
            username = _uiState.value.currentUser?.displayName ?: "Usuario Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName.ifBlank { "CreditHome" }
        )

        _uiState.update {
            it.copy(
                activeClient = newActive,
                allClients = updatedClients,
                auditLogs = auditRepository.getAllLogs()
            )
        }
        recalculate()
    }

    fun recordPayment(record: PaymentRecord) {
        val current = _uiState.value
        val activeClient = current.activeClient
        val previousRecord = current.paymentRecords[record.month]
        val wasAlreadyPaid = previousRecord?.isPaid == true

        val newMap = current.paymentRecords.toMutableMap()
        newMap[record.month] = record

        val clientName = activeClient?.name ?: "Cliente"
        val actionDesc = if (record.isPaid) {
            "Pago cuota #${record.month} registrado ($${String.format(java.util.Locale.US, "%.2f", record.amountPaid)}) para '$clientName'"
        } else {
            "Cuota #${record.month} de '$clientName' revertida a PENDIENTE"
        }

        auditRepository.log(
            action = if (record.isPaid) AuditActionType.RECORD_PAYMENT else AuditActionType.CANCEL_PAYMENT,
            description = actionDesc,
            username = _uiState.value.currentUser?.displayName ?: "Usuario Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName.ifBlank { "CreditHome" },
            details = "Método: ${record.paymentMethod} | Ref: ${record.referenceNumber.ifBlank { "N/A" }}"
        )

        if (activeClient != null) {
            if (record.isPaid && (!wasAlreadyPaid || (previousRecord != null && previousRecord.amountPaid != record.amountPaid))) {
                notificationManager.addPaymentReceivedNotification(
                    clientName = activeClient.name,
                    month = record.month,
                    amount = record.amountPaid,
                    clientId = activeClient.id
                )
            } else if (!record.isPaid && wasAlreadyPaid) {
                notificationManager.removePaymentReceivedNotification(
                    clientId = activeClient.id,
                    month = record.month
                )
            }
        }

        if (activeClient != null) {
            val updatedClient = clientRepository.updatePaymentRecord(activeClient.id, record)
            val updatedList = clientRepository.getAllClients()
            _uiState.update {
                it.copy(
                    activeClient = updatedClient ?: activeClient.copy(paymentRecords = newMap),
                    allClients = updatedList,
                    paymentRecords = newMap,
                    auditLogs = auditRepository.getAllLogs(),
                    notifications = notificationManager.getAllNotifications()
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    paymentRecords = newMap,
                    auditLogs = auditRepository.getAllLogs(),
                    notifications = notificationManager.getAllNotifications()
                )
            }
        }
        persistAndRecalculate()
    }

    private fun persistAndRecalculate() {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveState(_uiState.value)
            preferences.saveActiveClientId(_uiState.value.activeClient?.id)

            val current = _uiState.value
            val activeClient = current.activeClient
            if (activeClient != null) {
                val updatedClient = activeClient.copy(
                    price = current.priceInput,
                    downPayment = current.downPaymentInput,
                    annualRate = current.annualRateInput,
                    years = current.yearsInput,
                    startDate = current.startDateInput,
                    isRoundUpEnabled = current.isRoundUpEnabled,
                    hideInterest = current.hideInterest,
                    isCompoundInterest = current.isCompoundInterest,
                    isFixedPriceLoan = current.isFixedPriceLoan,
                    extraPaymentMonth = current.extraPaymentMonth,
                    extraPaymentAmount = current.extraPaymentAmount,
                    paymentRecords = current.paymentRecords
                )
                clientRepository.saveOrUpdateClient(updatedClient)
            }
            recalculate()
        }
    }

    private fun recalculate() {
        viewModelScope.launch(Dispatchers.Default) {
            val current = _uiState.value
            val price = current.priceInput.toDoubleOrNull() ?: 0.0
            val downPayment = current.downPaymentInput.toDoubleOrNull() ?: 0.0
            val rate = current.annualRateInput.toDoubleOrNull() ?: 0.0
            val years = current.yearsInput.toDoubleOrNull() ?: 0.0
            val desiredPayment = if (current.activeClient == null) {
                current.desiredMonthlyPaymentInput.toDoubleOrNull() ?: 0.0
            } else {
                0.0
            }

            val result = calculateUseCase(
                price = price,
                downPayment = downPayment,
                annualRate = rate,
                years = years,
                desiredMonthlyPayment = desiredPayment,
                roundUp = current.isRoundUpEnabled,
                isCompoundInterest = current.isCompoundInterest,
                isFixedPriceLoan = current.isFixedPriceLoan,
                extraPaymentMonth = current.extraPaymentMonth,
                extraPaymentAmount = current.extraPaymentAmount,
                startDate = current.startDateInput,
                paymentRecords = current.paymentRecords
            )

            val allLatestClients = withContext(Dispatchers.IO) {
                clientRepository.getAllClients()
            }
            val stats = calculateDashboardUseCase(allLatestClients)
            val updatedNotifs = withContext(Dispatchers.IO) {
                notificationManager.scanAndGenerateLoanNotifications(allLatestClients)
            }
            val latestLogs = withContext(Dispatchers.IO) {
                auditRepository.getAllLogs()
            }

            _uiState.update {
                it.copy(
                    summary = result,
                    allClients = allLatestClients,
                    dashboardStats = stats,
                    notifications = updatedNotifs,
                    auditLogs = latestLogs
                )
            }
        }
    }

    fun saveBusinessProfile(profile: BusinessProfile) {
        businessProfileRepository.saveProfile(profile)
        _uiState.update { it.copy(businessProfile = profile) }
    }

    fun saveBusinessLogoUri(uri: android.net.Uri): String {
        val path = businessProfileRepository.saveLogo(uri)
        val updated = _uiState.value.businessProfile.copy(logoPath = path)
        _uiState.update { it.copy(businessProfile = updated) }
        return path
    }

    fun removeBusinessLogo() {
        businessProfileRepository.removeLogo()
        val updated = _uiState.value.businessProfile.copy(logoPath = "")
        _uiState.update { it.copy(businessProfile = updated) }
    }

    fun saveVoucherConfig(config: VoucherConfig) {
        voucherConfigRepository.saveVoucherConfig(config)
        _uiState.update { it.copy(voucherConfig = config) }
    }

    fun resetVoucherConfig(): VoucherConfig {
        val defaultConfig = voucherConfigRepository.resetToDefault()
        _uiState.update { it.copy(voucherConfig = defaultConfig) }
        return defaultConfig
    }

    fun reloadAllData() {
        val allClients = clientRepository.getAllClients()
        val savedActiveId = preferences.getActiveClientId()
        val activeClient = if (savedActiveId != null) allClients.find { it.id == savedActiveId } else allClients.firstOrNull()
        val businessProfile = businessProfileRepository.getProfile()
        val voucherConfig = voucherConfigRepository.getVoucherConfig()
        val currentNotifications = notificationManager.getAllNotifications()
        val currentAuditLogs = auditRepository.getAllLogs()

        _uiState.update { current ->
            if (activeClient != null) {
                current.copy(
                    activeClient = activeClient,
                    allClients = allClients,
                    priceInput = activeClient.price,
                    downPaymentInput = activeClient.downPayment,
                    annualRateInput = activeClient.annualRate,
                    yearsInput = activeClient.years,
                    startDateInput = activeClient.startDate,
                    isRoundUpEnabled = activeClient.isRoundUpEnabled,
                    hideInterest = activeClient.hideInterest,
                    extraPaymentMonth = activeClient.extraPaymentMonth,
                    extraPaymentAmount = activeClient.extraPaymentAmount,
                    paymentRecords = activeClient.paymentRecords,
                    businessProfile = businessProfile,
                    voucherConfig = voucherConfig,
                    notifications = currentNotifications,
                    auditLogs = currentAuditLogs
                )
            } else {
                current.copy(
                    activeClient = null,
                    allClients = allClients,
                    businessProfile = businessProfile,
                    voucherConfig = voucherConfig,
                    notifications = currentNotifications,
                    auditLogs = currentAuditLogs
                )
            }
        }
        recalculate()
    }

    fun onLoginSuccess(user: AppUser) {
        _uiState.update {
            it.copy(
                currentUser = user,
                currentScreen = AppScreen.DASHBOARD
            )
        }
        auditRepository.log(
            action = AuditActionType.LOGIN,
            description = "Inicio de sesión exitoso",
            username = user.displayName,
            userRole = user.role.name,
            companyName = user.companyName
        )
        recalculate()
    }

    fun logout() {
        val user = _uiState.value.currentUser
        if (user != null) {
            auditRepository.log(
                action = AuditActionType.LOGOUT,
                description = "Cierre de sesión",
                username = user.displayName,
                userRole = user.role.name,
                companyName = user.companyName
            )
        }
        authRepository.logout()
        _uiState.update {
            it.copy(
                currentUser = null,
                currentScreen = if (featureFlagManager.isEnabled(FeatureKey.AUTH_AND_ROLES)) AppScreen.LOGIN else AppScreen.DASHBOARD
            )
        }
    }

    val authRepo: AuthRepository get() = authRepository

    fun saveUser(user: AppUser) {
        authRepository.saveUser(user)
        auditRepository.log(
            action = AuditActionType.CREATE_USER,
            description = "Usuario '@${user.username}' registrado con rol ${user.role.displayName}",
            username = _uiState.value.currentUser?.displayName ?: "Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName
        )
        _uiState.update { it.copy(allUsers = authRepository.getAllUsers()) }
    }

    fun createUser(username: String, pin: String, role: UserRole, permissions: UserPermissions): AppUser {
        val securityManager = SecurityManager(getApplication())
        securityManager.validatePinStrength(pin)?.let { throw IllegalArgumentException(it) }
        val companyName = _uiState.value.businessProfile.businessName.ifBlank { "CreditHome" }
        val newUser = AppUser(
            id = java.util.UUID.randomUUID().toString(),
            username = username.trim().lowercase(),
            displayName = username.trim(),
            pinHash = securityManager.hashPin(pin),
            role = role,
            companyName = companyName,
            permissions = permissions
        )
        saveUser(newUser)
        return newUser
    }

    fun updateUser(user: AppUser) {
        authRepository.saveUser(user)
        auditRepository.log(
            action = AuditActionType.EDIT_USER,
            description = "Perfil/permisos de '@${user.username}' actualizados",
            username = _uiState.value.currentUser?.displayName ?: "Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName
        )
        _uiState.update { it.copy(allUsers = authRepository.getAllUsers()) }
    }

    fun deleteUser(userId: String) {
        val user = authRepository.getAllUsers().find { it.id == userId }
        authRepository.deleteUser(userId)
        if (user != null) {
            auditRepository.log(
                action = AuditActionType.DELETE_USER,
                description = "Usuario '@${user.username}' eliminado",
                username = _uiState.value.currentUser?.displayName ?: "Máster",
                userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
                companyName = _uiState.value.businessProfile.businessName
            )
        }
        _uiState.update { it.copy(allUsers = authRepository.getAllUsers()) }
    }

    fun createMasterAccount(companyName: String, username: String, pin: String, initialPermissions: UserPermissions): AppUser {
        val securityManager = SecurityManager(getApplication())
        securityManager.validatePinStrength(pin)?.let { throw IllegalArgumentException(it) }
        val newMaster = AppUser(
            id = java.util.UUID.randomUUID().toString(),
            username = username.trim().lowercase(),
            displayName = username.trim(),
            pinHash = securityManager.hashPin(pin),
            role = UserRole.MASTER,
            companyName = companyName,
            permissions = initialPermissions
        )
        authRepository.saveUser(newMaster)
        auditRepository.log(
            action = AuditActionType.CREATE_COMPANY,
            description = "Empresa '$companyName' creada con usuario Máster '@${newMaster.username}'",
            username = _uiState.value.currentUser?.displayName ?: "SuperAdmin",
            userRole = _uiState.value.currentUser?.role?.name ?: "SUPERADMIN",
            companyName = "SISTEMA"
        )
        _uiState.update { it.copy(allUsers = authRepository.getAllUsers()) }
        return newMaster
    }

    fun deleteMasterAccount(userId: String) {
        val target = authRepository.getAllUsers().find { it.id == userId }
        authRepository.deleteUser(userId)
        if (target != null) {
            auditRepository.log(
                action = AuditActionType.DELETE_USER,
                description = "Cuenta Máster '@${target.username}' y empresa '${target.companyName}' eliminadas",
                username = _uiState.value.currentUser?.displayName ?: "SuperAdmin",
                userRole = _uiState.value.currentUser?.role?.name ?: "SUPERADMIN",
                companyName = "SISTEMA"
            )
        }
        _uiState.update { it.copy(allUsers = authRepository.getAllUsers()) }
    }

    fun generateInvitationToken(user: AppUser): String {
        val securityManager = SecurityManager(getApplication())
        return securityManager.generateInvitationToken(user)
    }

    fun toggleSuperadminAudit(allowed: Boolean) {
        authRepository.setSuperadminAuditPermission(allowed)
        val currentUser = _uiState.value.currentUser?.copy(allowSuperadminAudit = allowed)
        _uiState.update { it.copy(currentUser = currentUser, allUsers = authRepository.getAllUsers()) }
    }

    fun markNotificationAsRead(id: String) {
        notificationManager.markAsRead(id)
        _uiState.update { it.copy(notifications = notificationManager.getAllNotifications()) }
    }

    fun markAllNotificationsAsRead() {
        notificationManager.markAllAsRead()
        _uiState.update { it.copy(notifications = notificationManager.getAllNotifications()) }
    }

    fun deleteNotification(id: String) {
        notificationManager.deleteNotification(id)
        _uiState.update { it.copy(notifications = notificationManager.getAllNotifications()) }
    }

    fun clearAllNotifications() {
        notificationManager.clearAll()
        _uiState.update { it.copy(notifications = emptyList()) }
    }

    fun clearAuditLogs() {
        auditRepository.clearLogs()
        _uiState.update { it.copy(auditLogs = emptyList()) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val info = appUpdateManager.checkForUpdates()
            _uiState.update { it.copy(appUpdateInfo = if (info.hasUpdate) info else null) }
        }
    }

    fun downloadUpdate(apkUrl: String) {
        appUpdateManager.downloadAndInstallApk(apkUrl)
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(appUpdateInfo = null) }
    }

    // GESTIÓN DE SMTP & NOTIFICACIONES POR CORREO
    fun getSmtpConfig(): SmtpConfig = smtpRepository.getConfig()

    fun saveSmtpConfig(config: SmtpConfig) {
        smtpRepository.saveConfig(config)
        auditRepository.log(
            action = AuditActionType.SETTINGS_CHANGED,
            description = "Configuración de Servidor SMTP Gmail actualizada (${config.senderEmail})",
            username = _uiState.value.currentUser?.displayName ?: "Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName
        )
    }

    suspend fun testSmtpConnection(config: SmtpConfig, targetEmail: String): Result<String> {
        smtpRepository.saveConfig(config)
        val res = emailNotificationUseCase.sendTestEmail(targetEmail, _uiState.value.businessProfile)
        return if (res.isSuccess) {
            Result.success("¡Correo de prueba enviado con éxito a $targetEmail!")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Error desconocido al conectar con SMTP"))
        }
    }

    // GESTIÓN DE COLAS RABBITMQ VPS
    fun getRabbitMqConfig(): RabbitMqConfig = rabbitMqRepository.getConfig()

    fun saveRabbitMqConfig(config: RabbitMqConfig) {
        rabbitMqRepository.saveConfig(config)
        auditRepository.log(
            action = AuditActionType.SETTINGS_CHANGED,
            description = "Configuración de Colas RabbitMQ actualizada (${config.host}:${config.port})",
            username = _uiState.value.currentUser?.displayName ?: "Máster",
            userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
            companyName = _uiState.value.businessProfile.businessName
        )
    }

    suspend fun testRabbitMqConnection(config: RabbitMqConfig): Result<String> {
        return rabbitMqQueueManager.testRabbitMqConnection(config)
    }

    fun getOutboxMessages(): List<OutboxQueueMessage> = rabbitMqRepository.getOutboxMessages()

    fun clearOutbox() {
        rabbitMqRepository.clearQueue()
    }

    // DESPACHO DE RECORDATORIOS Y COMPROBANTES FIRMADOS
    suspend fun sendPaymentReminderEmail(
        client: ClientProfile,
        month: Int,
        scheduledPayment: Double,
        dueDate: String
    ): Result<String> {
        val totalMonths = _uiState.value.summary?.actualMonths ?: 1
        val res = emailNotificationUseCase.sendPaymentReminderEmail(
            config = smtpRepository.getConfig(),
            businessProfile = _uiState.value.businessProfile,
            client = client,
            month = month,
            totalMonths = totalMonths,
            dueDate = dueDate,
            amount = scheduledPayment,
            remainingBalance = 0.0,
            daysRemaining = 0
        )
        return if (res.isSuccess) {
            auditRepository.log(
                action = AuditActionType.EMAIL_SENT,
                description = "Recordatorio de cuota mes #$month enviado a '${client.name}' (${client.email})",
                username = _uiState.value.currentUser?.displayName ?: "Sistema",
                userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
                companyName = _uiState.value.businessProfile.businessName
            )
            Result.success("Recordatorio enviado a ${client.email}")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Error al enviar recordatorio"))
        }
    }

    suspend fun sendSignedPaymentReceiptEmail(
        client: ClientProfile,
        record: PaymentRecord,
        month: Int,
        totalMonths: Int,
        scheduledPayment: Double,
        interest: Double,
        principal: Double,
        remainingBalance: Double
    ): Result<String> {
        if (!record.isSigned) {
            return Result.failure(IllegalStateException("El comprobante debe estar firmado digital o físicamente antes del envío."))
        }

        val res = emailNotificationUseCase.sendSignedReceiptEmail(
            config = smtpRepository.getConfig(),
            businessProfile = _uiState.value.businessProfile,
            client = client,
            record = record,
            totalMonths = totalMonths,
            scheduledPayment = scheduledPayment,
            remainingBalance = remainingBalance
        )

        return if (res.isSuccess) {
            val updatedRecord = record.copy(
                emailSentCount = record.emailSentCount + 1,
                lastEmailSentAt = System.currentTimeMillis()
            )
            recordPayment(updatedRecord)
            auditRepository.log(
                action = AuditActionType.RECEIPT_SIGNED,
                description = "Comprobante firmado mes #$month enviado a '${client.name}' (${client.email}) - Envío #${updatedRecord.emailSentCount}",
                username = _uiState.value.currentUser?.displayName ?: "Máster",
                userRole = _uiState.value.currentUser?.role?.name ?: "MASTER",
                companyName = _uiState.value.businessProfile.businessName
            )
            Result.success("Comprobante oficial enviado a ${client.email}")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Error al enviar comprobante"))
        }
    }
}
