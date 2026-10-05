package com.alfas.amortizacioncredito.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.feature.FeatureKey
import com.alfas.amortizacioncredito.data.model.AmortizationItem
import com.alfas.amortizacioncredito.data.model.AmortizationSummary
import com.alfas.amortizacioncredito.data.model.AppNotification
import com.alfas.amortizacioncredito.data.model.NotificationType
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.data.model.UserRole
import com.alfas.amortizacioncredito.domain.usecase.ExportAmortizationToExcelUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import com.alfas.amortizacioncredito.ui.AmortizationViewModel
import com.alfas.amortizacioncredito.ui.dialogs.admin.AppUpdateDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.AuditLogDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.BackupRestoreDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.NotificationCenterDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.RabbitMqConfigDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.SmtpConfigDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.SuperAdminDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.UserManagementDialog
import com.alfas.amortizacioncredito.ui.dialogs.admin.UserProfileDialog
import com.alfas.amortizacioncredito.ui.dialogs.business.BusinessProfileDialog
import com.alfas.amortizacioncredito.ui.dialogs.business.VoucherCustomizationDialog
import com.alfas.amortizacioncredito.ui.dialogs.client.ClientListDialog
import com.alfas.amortizacioncredito.ui.dialogs.client.SaveClientDialog
import com.alfas.amortizacioncredito.ui.dialogs.payment.EarlyPaymentDialog
import com.alfas.amortizacioncredito.ui.dialogs.payment.PaymentReceiptDialog
import com.alfas.amortizacioncredito.ui.dialogs.payment.PaymentRecordDialog
import com.alfas.amortizacioncredito.ui.dialogs.payment.TargetMonthlyPaymentDialog
import com.alfas.amortizacioncredito.ui.dialogs.bot.PaymentAssistantBotDialog
import com.alfas.amortizacioncredito.ui.theme.*
import com.alfas.amortizacioncredito.ui.AppScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizationScreen(
    viewModel: AmortizationViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scrollHorizontalState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val excelUseCase = remember { ExportAmortizationToExcelUseCase() }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showEarlyPaymentDialog by remember { mutableStateOf(false) }
    var showTargetMonthlyPaymentDialog by remember { mutableStateOf(false) }
    var showClientListDialog by remember { mutableStateOf(false) }
    var showSaveClientDialog by remember { mutableStateOf(false) }
    var isEditingClientDialog by remember { mutableStateOf(false) }
    var showBusinessProfileDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    var showUserManagementDialog by remember { mutableStateOf(false) }
    var showSuperAdminDialog by remember { mutableStateOf(false) }
    var showAuditLogDialog by remember { mutableStateOf(false) }
    var showSmtpConfigDialog by remember { mutableStateOf(false) }
    var showRabbitMqConfigDialog by remember { mutableStateOf(false) }
    var showTopBarOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteActiveClientDialog by remember { mutableStateOf(false) }
    var showVoucherCustomizationDialog by remember { mutableStateOf(false) }
    var showSignatureVaultDialog by remember { mutableStateOf(false) }
    var showPaymentBotDialog by remember { mutableStateOf(false) }
    var selectedPaymentItem by remember { mutableStateOf<AmortizationItem?>(null) }
    var viewingReceiptItem by remember { mutableStateOf<AmortizationItem?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Si el menú hamburguesa está abierto, presionar atrás cierra el menú en lugar de salir
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    // Navegación de retroceso: Si está en el Simulador, vuelve al Dashboard; si está en el Dashboard, doble toque para salir
    val isAnyDialogOpen = showEarlyPaymentDialog || showTargetMonthlyPaymentDialog || showClientListDialog ||
            showSaveClientDialog || showBusinessProfileDialog || showBackupDialog || showResetConfirmDialog ||
            showNotificationCenterDialog || showUserProfileDialog || showUserManagementDialog ||
            showSuperAdminDialog || showAuditLogDialog || showSmtpConfigDialog || showRabbitMqConfigDialog ||
            showDeleteActiveClientDialog || showVoucherCustomizationDialog || showSignatureVaultDialog ||
            showPaymentBotDialog || selectedPaymentItem != null || viewingReceiptItem != null
    BackHandler(enabled = drawerState.isClosed && !isAnyDialogOpen) {
        if (state.currentScreen == AppScreen.SIMULATOR) {
            viewModel.navigateToDashboard()
        } else if (state.currentScreen == AppScreen.DASHBOARD) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher para guardar archivo Excel
    val saveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                        excelUseCase.exportToStream(state, outputStream)
                    }
                    snackbarHostState.showSnackbar("Excel guardado exitosamente")
                } catch (e: Throwable) {
                    snackbarHostState.showSnackbar("Error al guardar Excel: ${e.localizedMessage ?: "desconocido"}")
                }
            }
        }
    }

    // Modal de Simulación de Abono
    if (showEarlyPaymentDialog) {
        val price = state.priceInput.toDoubleOrNull() ?: 0.0
        val downPayment = state.downPaymentInput.toDoubleOrNull() ?: 0.0
        val rate = state.annualRateInput.toDoubleOrNull() ?: 0.0
        val years = state.yearsInput.toDoubleOrNull() ?: 0.0

        EarlyPaymentDialog(
            price = price,
            downPayment = downPayment,
            rate = rate,
            years = years,
            isRoundUp = state.isRoundUpEnabled,
            isFixedPriceLoan = state.isFixedPriceLoan,
            currentExtraMonth = state.extraPaymentMonth,
            currentExtraAmount = state.extraPaymentAmount,
            paymentRecords = state.paymentRecords,
            onDismiss = { showEarlyPaymentDialog = false },
            onApply = { month, amount ->
                viewModel.setExtraPayment(month, amount)
                showEarlyPaymentDialog = false
            },
            onClear = {
                viewModel.clearExtraPayment()
                showEarlyPaymentDialog = false
            }
        )
    }

    // Modal Calculadora de Meta de Cuota Mensual (¿En cuántos meses liquido pagando $X al mes?)
    if (showTargetMonthlyPaymentDialog) {
        val summary = state.summary
        val financedAmount = summary?.financedAmount ?: 0.0
        val lastPaidMonth = state.paymentRecords.filter { it.value.isPaid }.keys.maxOrNull() ?: 0
        val remainingBal = if (lastPaidMonth > 0) {
            summary?.schedule?.find { it.month == lastPaidMonth }?.remainingBalance ?: financedAmount
        } else {
            financedAmount
        }
        val hasPaid = lastPaidMonth > 0
        val annualRate = state.annualRateInput.toDoubleOrNull() ?: 0.0
        val originalMonths = ((state.yearsInput.toDoubleOrNull() ?: 0.0) * 12.0).toInt()
        val originalMonthlyPayment = summary?.monthlyPayment ?: 0.0
        val originalTotalInterest = summary?.totalInterest ?: 0.0

        TargetMonthlyPaymentDialog(
            financedAmount = financedAmount,
            remainingBalance = remainingBal,
            hasPaidMonths = hasPaid,
            annualRate = annualRate,
            originalMonths = originalMonths,
            originalMonthlyPayment = originalMonthlyPayment,
            originalTotalInterest = originalTotalInterest,
            isCompoundInterest = state.isCompoundInterest,
            onApplyMonths = { months ->
                val yearsStr = if (months % 12 == 0) {
                    (months / 12).toString()
                } else {
                    String.format(Locale.US, "%.2f", months / 12.0)
                }
                viewModel.onYearsChanged(yearsStr)
                showTargetMonthlyPaymentDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Plazo actualizado a $months meses ($yearsStr años)")
                }
            },
            onDismiss = { showTargetMonthlyPaymentDialog = false }
        )
    }

    // Modal Directorio de Clientes
    if (showClientListDialog) {
        ClientListDialog(
            clients = state.allClients,
            currentClientId = state.activeClient?.id,
            onSelectClient = { client ->
                viewModel.navigateToSimulator(client)
                showClientListDialog = false
            },
            onNewClient = {
                showClientListDialog = false
                isEditingClientDialog = false
                showSaveClientDialog = true
            },
            onDeleteClient = { id ->
                viewModel.deleteClient(id)
            },
            onDismiss = { showClientListDialog = false }
        )
    }

    // Modal Guardar / Asignar a Cliente
    if (showSaveClientDialog) {
        SaveClientDialog(
            initialName = if (isEditingClientDialog) (state.activeClient?.name ?: "") else "",
            initialDocument = if (isEditingClientDialog) (state.activeClient?.displayDocumentNumber ?: "") else "",
            initialPhone = if (isEditingClientDialog) (state.activeClient?.displayPhone ?: (state.activeClient?.documentOrPhone ?: "")) else "",
            initialAddress = if (isEditingClientDialog) (state.activeClient?.address ?: "") else "",
            initialEmail = if (isEditingClientDialog) (state.activeClient?.email ?: "") else "",
            initialNotes = if (isEditingClientDialog) (state.activeClient?.notes ?: "") else "",
            initialPrice = if (isEditingClientDialog) (state.activeClient?.price ?: state.priceInput) else state.priceInput,
            initialDownPayment = if (isEditingClientDialog) (state.activeClient?.downPayment ?: state.downPaymentInput) else state.downPaymentInput,
            initialAnnualRate = if (isEditingClientDialog) (state.activeClient?.annualRate ?: state.annualRateInput) else state.annualRateInput,
            initialYears = if (isEditingClientDialog) (state.activeClient?.years ?: state.yearsInput) else state.yearsInput,
            initialStartDate = if (isEditingClientDialog) (state.activeClient?.startDate ?: state.startDateInput) else state.startDateInput,
            initialIsFixedPriceLoan = if (isEditingClientDialog) (state.activeClient?.isFixedPriceLoan ?: state.isFixedPriceLoan) else state.isFixedPriceLoan,
            paidMonthsCount = state.paymentRecords.values.count { it.isPaid },
            isEditing = isEditingClientDialog,
            onDismiss = { showSaveClientDialog = false },
            onSave = { name, documentNumber, phone, address, email, notes, p, dp, r, y, startDate, isFixedPriceLoan ->
                if (isEditingClientDialog) {
                    viewModel.updateActiveClient(
                        name = name,
                        documentNumber = documentNumber,
                        phone = phone,
                        address = address,
                        email = email,
                        notes = notes,
                        price = p,
                        downPayment = dp,
                        annualRate = r,
                        years = y,
                        startDate = startDate,
                        isFixedPriceLoan = isFixedPriceLoan
                    )
                } else {
                    viewModel.createNewClient(
                        name = name,
                        documentNumber = documentNumber,
                        phone = phone,
                        address = address,
                        email = email,
                        notes = notes,
                        price = p,
                        downPayment = dp,
                        annualRate = r,
                        years = y,
                        startDate = startDate,
                        isFixedPriceLoan = isFixedPriceLoan
                    )
                }
                showSaveClientDialog = false
                coroutineScope.launch {
                    val msg = if (isEditingClientDialog) "Cliente '$name' y condiciones actualizadas" else "Nuevo cliente '$name' registrado"
                    snackbarHostState.showSnackbar(msg)
                }
            },
            onDelete = if (isEditingClientDialog && state.activeClient != null) {
                {
                    val deletedName = state.activeClient!!.name
                    viewModel.deleteClient(state.activeClient!!.id)
                    showSaveClientDialog = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Cliente '$deletedName' eliminado")
                    }
                }
            } else null
        )
    }

    // Modal Control de Pago & Comprobante
    selectedPaymentItem?.let { item ->
        val schedule = state.summary?.schedule ?: emptyList()
        val itemIndex = schedule.indexOfFirst { it.month == item.month }
        val previousBalance = if (itemIndex > 0) {
            schedule[itemIndex - 1].remainingBalance
        } else {
            (state.priceInput.toDoubleOrNull() ?: 0.0) - (state.downPaymentInput.toDoubleOrNull() ?: 0.0)
        }

        PaymentRecordDialog(
            month = item.month,
            totalMonths = state.summary?.actualMonths ?: 1,
            expectedAmount = item.payment,
            itemInterest = item.interest,
            itemPrincipal = item.principal,
            previousBalance = previousBalance,
            itemRemainingBalance = item.remainingBalance,
            scheduledDueDate = item.dueDate,
            currentRecord = state.paymentRecords[item.month],
            clientName = state.activeClient?.name,
            clientDocumentOrPhone = state.activeClient?.displayDocumentOrPhone,
            businessProfile = state.businessProfile,
            voucherConfig = state.voucherConfig,
            onEditBusinessProfile = { showBusinessProfileDialog = true },
            onSaveVoucherConfig = { viewModel.saveVoucherConfig(it) },
            onDismiss = { selectedPaymentItem = null },
            onSave = { record ->
                viewModel.recordPayment(record)
                selectedPaymentItem = null
                coroutineScope.launch {
                    val msg = if (record.isPaid) "Cuota ${item.month} registrada como PAGADA ($${String.format(Locale.US, "%.2f", record.amountPaid)})" else "Cuota ${item.month} marcada como PENDIENTE"
                    snackbarHostState.showSnackbar(msg)
                }
            }
        )
    }

    // Modal Visualizador de Voucher / Comprobante de Pago Oficial
    viewingReceiptItem?.let { item ->
        val record = state.paymentRecords[item.month]
        if (record != null && record.isPaid) {
            val schedule = state.summary?.schedule ?: emptyList()
            val itemIndex = schedule.indexOfFirst { it.month == item.month }
            val previousBalance = if (itemIndex > 0) {
                schedule[itemIndex - 1].remainingBalance
            } else {
                (state.priceInput.toDoubleOrNull() ?: 0.0) - (state.downPaymentInput.toDoubleOrNull() ?: 0.0)
            }

            PaymentReceiptDialog(
                record = record,
                month = item.month,
                totalMonths = state.summary?.actualMonths ?: 1,
                scheduledPayment = item.payment,
                interest = item.interest,
                principal = item.principal,
                previousBalance = previousBalance,
                remainingBalance = item.remainingBalance,
                clientName = state.activeClient?.name,
                clientDocumentOrPhone = state.activeClient?.displayDocumentOrPhone,
                clientEmail = state.activeClient?.email,
                businessProfile = state.businessProfile,
                voucherConfig = state.voucherConfig,
                onEditBusinessProfile = { showBusinessProfileDialog = true },
                onSaveVoucherConfig = { viewModel.saveVoucherConfig(it) },
                onSaveRecord = { updatedRec ->
                    viewModel.recordPayment(updatedRec)
                },
                onOpenFullEditor = {
                    selectedPaymentItem = item
                    viewingReceiptItem = null
                },
                onSendEmail = { updatedRec ->
                    val client = state.activeClient
                    if (client != null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Enviando comprobante firmado a ${client.email.ifBlank { client.name }}...")
                            val result = viewModel.sendSignedPaymentReceiptEmail(
                                client = client,
                                record = updatedRec,
                                month = item.month,
                                totalMonths = state.summary?.actualMonths ?: 1,
                                scheduledPayment = item.payment,
                                interest = item.interest,
                                principal = item.principal,
                                remainingBalance = item.remainingBalance
                            )
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("✅ Comprobante enviado exitosamente por correo")
                            } else {
                                snackbarHostState.showSnackbar("❌ Error al enviar correo: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("⚠️ Asigne este crédito a un cliente con correo electrónico para enviarlo.")
                        }
                    }
                },
                onDismiss = { viewingReceiptItem = null }
            )
        } else {
            viewingReceiptItem = null
        }
    }

    // Minibot Asistente Inteligente de Cobros y Pagos
    if (showPaymentBotDialog) {
        PaymentAssistantBotDialog(
            initialClient = state.activeClient,
            onPaymentRegistered = { updatedClient ->
                viewModel.reloadAllData()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("✅ Pago registrado correctamente para ${updatedClient.name}")
                }
            },
            onDismiss = { showPaymentBotDialog = false }
        )
    }

    // Modal de Personalización de Campos del Voucher
    if (showVoucherCustomizationDialog) {
        VoucherCustomizationDialog(
            initialConfig = state.voucherConfig,
            onDismiss = { showVoucherCustomizationDialog = false },
            onSaveConfig = { newConfig ->
                viewModel.saveVoucherConfig(newConfig)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Configuración del voucher actualizada")
                }
            }
        )
    }

    // Modal de Datos de Empresa / Logotipo (Vouchers de Ecuador)
    if (showBusinessProfileDialog) {
        BusinessProfileDialog(
            currentProfile = state.businessProfile,
            onSaveProfile = { profile ->
                viewModel.saveBusinessProfile(profile)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Datos de emisor actualizados")
                }
            },
            onSaveLogoUri = { uri ->
                val path = viewModel.saveBusinessLogoUri(uri)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Logotipo actualizado correctamente")
                }
                path
            },
            onRemoveLogo = {
                viewModel.removeBusinessLogo()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Logotipo eliminado")
                }
            },
            onOpenVoucherCustomization = { showVoucherCustomizationDialog = true },
            onDismiss = { showBusinessProfileDialog = false }
        )
    }

    // Modal de Copia de Seguridad & Sincronización Google Drive
    if (showBackupDialog) {
        BackupRestoreDialog(
            onDataRestored = {
                viewModel.reloadAllData()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("🎉 Datos y comprobantes restaurados correctamente")
                }
            },
            onDismiss = { showBackupDialog = false }
        )
    }

    // Modal de Confirmación para Reiniciar / Limpiar Simulador a Cero (Modo Libre)
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = RedError,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("¿Reiniciar Simulador a Cero?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Se restablecerán todos los valores del simulador a cero para empezar una nueva cuenta. Tus clientes guardados en el directorio no se modificarán ni borrarán.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetSimulator()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Valores del simulador restablecidos a 0")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sí, Borrar Todo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal de Confirmación Estricta para Eliminar Cliente Activo (Escribir nombre para confirmar)
    if (showDeleteActiveClientDialog && state.activeClient != null) {
        val clientToDelete = state.activeClient!!
        var confirmNameInput by remember { mutableStateOf("") }
        val isMatch = confirmNameInput.trim().equals(clientToDelete.name.trim(), ignoreCase = true)

        AlertDialog(
            onDismissRequest = { showDeleteActiveClientDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = RedError,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "Eliminar Cliente",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = RedError
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Esta acción eliminará de forma irreversible al cliente '${clientToDelete.name}', junto con toda su tabla de amortización y comprobantes registrados.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Para confirmar la eliminación, escribe el nombre del cliente exactamente:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = RedBadgeBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = clientToDelete.name,
                            fontWeight = FontWeight.Bold,
                            color = RedBadgeText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    OutlinedTextField(
                        value = confirmNameInput,
                        onValueChange = { confirmNameInput = it },
                        label = { Text("Escribe el nombre aquí") },
                        placeholder = { Text(clientToDelete.name) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedName = clientToDelete.name
                        viewModel.deleteClient(clientToDelete.id)
                        showDeleteActiveClientDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Cliente '$deletedName' eliminado correctamente")
                        }
                    },
                    enabled = isMatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedError,
                        disabledContainerColor = RedError.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar Definitivamente", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteActiveClientDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Centro de Notificaciones
    if (showNotificationCenterDialog) {
        NotificationCenterDialog(
            notifications = state.notifications,
            onMarkAllAsRead = { viewModel.markAllNotificationsAsRead() },
            onMarkAsRead = { notifId -> viewModel.markNotificationAsRead(notifId) },
            onDeleteNotification = { notifId -> viewModel.deleteNotification(notifId) },
            onClearAllNotifications = { viewModel.clearAllNotifications() },
            onNotificationClick = { notif ->
                viewModel.markNotificationAsRead(notif.id)
                if (notif.targetClientId != null) {
                    val client = state.allClients.find { it.id == notif.targetClientId }
                    if (client != null) {
                        viewModel.selectClient(client)
                        viewModel.navigateToSimulator(client)
                        if (notif.targetMonth != null) {
                            val schedule = viewModel.uiState.value.summary?.schedule ?: state.summary?.schedule ?: emptyList()
                            val item = schedule.find { it.month == notif.targetMonth }
                            if (item != null) {
                                if (notif.type == NotificationType.PAYMENT_RECEIVED) {
                                    viewingReceiptItem = item
                                } else {
                                    selectedPaymentItem = item
                                }
                            }
                        }
                        showNotificationCenterDialog = false
                    }
                } else if (notif.type == NotificationType.SYSTEM_UPDATE) {
                    viewModel.checkForUpdates()
                    showNotificationCenterDialog = false
                } else if (notif.type == NotificationType.AUDIT_ALERT) {
                    showAuditLogDialog = true
                    showNotificationCenterDialog = false
                }
            },
            onSendEmailReminder = { notif ->
                val client = state.allClients.find { it.id == notif.targetClientId }
                if (client != null) {
                    if (client.email.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("⚠️ El cliente '${client.name}' no tiene correo configurado.")
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Enviando recordatorio a ${client.email}...")
                            val res = viewModel.sendPaymentReminderEmail(
                                client = client,
                                month = notif.targetMonth ?: 1,
                                scheduledPayment = client.price.toDoubleOrNull() ?: 0.0,
                                dueDate = ""
                            )
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("✅ Recordatorio de pago enviado a ${client.name}")
                            } else {
                                snackbarHostState.showSnackbar("❌ Error al enviar: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    }
                }
            },
            onDismiss = { showNotificationCenterDialog = false }
        )
    }

    // Modal Servidor SMTP Gmail (Solo Superusuario)
    if (showSmtpConfigDialog && state.currentUser?.role == UserRole.SUPERADMIN) {
        val currentSmtp = remember { viewModel.getSmtpConfig() }
        SmtpConfigDialog(
            initialConfig = currentSmtp,
            onSaveConfig = { cfg ->
                viewModel.saveSmtpConfig(cfg)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Configuración de Servidor SMTP guardada")
                }
            },
            onTestEmail = { targetEmail, onResult ->
                coroutineScope.launch {
                    val res = viewModel.testSmtpConnection(viewModel.getSmtpConfig(), targetEmail)
                    if (res.isSuccess) {
                        onResult(true, res.getOrNull() ?: "Conexión exitosa")
                    } else {
                        onResult(false, res.exceptionOrNull()?.message ?: "Error al conectar")
                    }
                }
            },
            onDismiss = { showSmtpConfigDialog = false }
        )
    }

    // Modal Gestor de Colas RabbitMQ VPS (Solo Superusuario)
    if (showRabbitMqConfigDialog && state.currentUser?.role == UserRole.SUPERADMIN) {
        val currentRmq = remember { viewModel.getRabbitMqConfig() }
        val outbox = viewModel.getOutboxMessages()
        RabbitMqConfigDialog(
            initialConfig = currentRmq,
            pendingMessagesCount = outbox.size,
            onSaveConfig = { cfg ->
                viewModel.saveRabbitMqConfig(cfg)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Configuración de RabbitMQ guardada")
                }
            },
            onTestConnection = { cfg, onResult ->
                coroutineScope.launch {
                    val res = viewModel.testRabbitMqConnection(cfg)
                    if (res.isSuccess) {
                        onResult(true, res.getOrNull() ?: "Conexión exitosa")
                    } else {
                        onResult(false, res.exceptionOrNull()?.message ?: "Error al conectar")
                    }
                }
            },
            onClearQueue = {
                viewModel.clearOutbox()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Cola de salida local limpiada")
                }
            },
            onDismiss = { showRabbitMqConfigDialog = false }
        )
    }

    // Modal Perfil de Usuario & Softoken
    if (showUserProfileDialog && state.currentUser != null) {
        UserProfileDialog(
            user = state.currentUser!!,
            onLogout = {
                showUserProfileDialog = false
                viewModel.logout()
            },
            onDismiss = { showUserProfileDialog = false }
        )
    }

    // Modal Gestión de Usuarios (Máster)
    if (showUserManagementDialog && state.currentUser != null) {
        UserManagementDialog(
            users = state.allUsers,
            companyName = state.businessProfile.businessName,
            companyRuc = state.businessProfile.ruc,
            allowSuperadminAudit = state.currentUser?.allowSuperadminAudit ?: true,
            onSaveUser = { user -> viewModel.saveUser(user) },
            onDeleteUser = { userId -> viewModel.deleteUser(userId) },
            onToggleSuperadminAudit = { allowed -> viewModel.toggleSuperadminAudit(allowed) },
            onDismiss = { showUserManagementDialog = false }
        )
    }

    // Modal Panel SuperAdmin
    if (showSuperAdminDialog && state.currentUser?.role == UserRole.SUPERADMIN) {
        val masterAccounts = state.allUsers.filter { it.role == UserRole.MASTER }
        SuperAdminDialog(
            masterUsers = masterAccounts,
            onDismiss = { showSuperAdminDialog = false }
        )
    }

    // Modal Historial de Auditoría
    if (showAuditLogDialog) {
        AuditLogDialog(
            logs = state.auditLogs,
            companyName = state.businessProfile.businessName,
            onDismiss = { showAuditLogDialog = false }
        )
    }

    // Modal de Actualización OTA desde Servidor VPS
    state.appUpdateInfo?.let { updateInfo ->
        AppUpdateDialog(
            updateInfo = updateInfo,
            onDownload = { apkUrl ->
                viewModel.downloadUpdate(apkUrl)
            },
            onDismiss = { viewModel.dismissUpdate() }
        )
    }

    // Modal Bóveda de Firmas Digitales (Gestión de Firmas Táctiles, .p12 y Escaneadas)
    if (showSignatureVaultDialog) {
        val issuerOrAdmin = state.businessProfile.issuerName.ifBlank {
            state.businessProfile.businessName.ifBlank { "Administrador" }
        }
        com.alfas.amortizacioncredito.ui.dialogs.business.SignatureCaptureDialog(
            signerName = issuerOrAdmin,
            voucherInfo = "Bóveda de Seguridad CreditHome",
            isStandaloneVault = true,
            onSignatureSuccess = { path, type, signer, date, details ->
                showSignatureVaultDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("✅ Firma registrada y guardada en la bóveda")
                }
            },
            onDismiss = { showSignatureVaultDialog = false }
        )
    }

    if (state.currentScreen == AppScreen.LOGIN) {
        LoginScreen(
            authRepository = viewModel.authRepo,
            onLoginSuccess = { user ->
                viewModel.onLoginSuccess(user)
            }
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                val drawerScrollState = rememberScrollState()
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(320.dp)
                        .verticalScroll(drawerScrollState)
                ) {
                    // Header Drawer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 20.dp, vertical = 22.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Slate900),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.alfas.amortizacioncredito.R.drawable.ic_credithome_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.businessProfile.businessName.ifBlank { "CreditHome" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${state.allClients.size} cliente(s) registrados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // SECCIÓN 1: VISTAS PRINCIPALES
                    DrawerSectionTitle("Vistas Principales")
                    DrawerItemRow(
                        icon = Icons.Default.Dashboard,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Dashboard / Inicio",
                        subtitle = "Resumen general y accesos rápidos",
                        selected = state.currentScreen == AppScreen.DASHBOARD,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.navigateToDashboard()
                        }
                    )
                    DrawerItemRow(
                        icon = Icons.Default.Calculate,
                        iconTint = CreditHomeBlue,
                        title = "Simulador de Crédito",
                        subtitle = "Tabla de amortización y cuotas",
                        selected = state.currentScreen == AppScreen.SIMULATOR,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.navigateToSimulator()
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // SECCIÓN 2: GESTIÓN DE CLIENTES
                    DrawerSectionTitle("Directorio & Clientes")
                    DrawerItemRow(
                        icon = Icons.Default.Groups,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Directorio de Clientes",
                        subtitle = "${state.allClients.size} clientes en cartera",
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showClientListDialog = true
                        }
                    )
                    DrawerItemRow(
                        icon = Icons.Default.PersonAdd,
                        iconTint = GreenSuccess,
                        title = "Registrar Nuevo Cliente",
                        subtitle = "Crear perfil con amortización",
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            isEditingClientDialog = false
                            showSaveClientDialog = true
                        }
                    )

                    if (state.activeClient != null) {
                        DrawerItemRow(
                            icon = Icons.Default.Edit,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Editar Cliente Activo",
                            subtitle = state.activeClient!!.name,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                isEditingClientDialog = true
                                showSaveClientDialog = true
                            }
                        )
                        DrawerItemRow(
                            icon = Icons.Default.RestartAlt,
                            iconTint = SkyBlueAction,
                            title = "Volver al Modo Libre",
                            subtitle = "Desvincular cliente actual",
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    viewModel.navigateToSimulator(null)
                                    snackbarHostState.showSnackbar("Modo Libre activado")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // SECCIÓN 3: EMPRESA & NUBE
                    DrawerSectionTitle("Empresa & Servidores")
                    DrawerItemRow(
                        icon = Icons.Default.Storefront,
                        iconTint = GreenSuccess,
                        title = "Perfil del Emisor / Empresa",
                        subtitle = "Datos del emisor, RUC/CI y logo",
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showBusinessProfileDialog = true
                        }
                    )
                    DrawerItemRow(
                        icon = Icons.Default.Tune,
                        iconTint = TealAccent,
                        title = "Personalizar Voucher / Recibo",
                        subtitle = "Activar/quitar campos y datos extra",
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showVoucherCustomizationDialog = true
                        }
                    )
                    DrawerItemRow(
                        icon = Icons.Default.Draw,
                        iconTint = PurpleLight,
                        title = "Bóveda de Firmas Digitales",
                        subtitle = "Gestionar firmas táctiles, .p12 y fotos",
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showSignatureVaultDialog = true
                        }
                    )
                    DrawerItemRow(
                        icon = Icons.Default.CloudSync,
                        iconTint = CreditHomeBlue,
                        title = "Copia de Seguridad (Drive)",
                        subtitle = "Exportar y restaurar datos JSON",
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showBackupDialog = true
                        }
                    )

                    // SECCIÓN 4: SEGURIDAD Y CONTROL DE USUARIOS (Sujeta a Feature Flags)
                    val isAuthEnabled = viewModel.isFeatureEnabled(FeatureKey.AUTH_AND_ROLES)
                    val isAuditEnabled = viewModel.isFeatureEnabled(FeatureKey.AUDIT_LOG)
                    val isUpdateEnabled = viewModel.isFeatureEnabled(FeatureKey.VPS_AUTO_UPDATE)
                    val isSuperAdminEnabled = viewModel.isFeatureEnabled(FeatureKey.SUPERADMIN_ROLE)
                    val isSmtpEnabled = viewModel.isFeatureEnabled(FeatureKey.SMTP_EMAIL)
                    val isRabbitEnabled = viewModel.isFeatureEnabled(FeatureKey.RABBITMQ_SYNC)

                    val hasSecuritySection = (state.currentUser != null) || isAuthEnabled || isAuditEnabled || isUpdateEnabled || isSuperAdminEnabled || isSmtpEnabled || isRabbitEnabled

                    if (hasSecuritySection) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(4.dp))

                        DrawerSectionTitle("Seguridad & Accesos")

                        if (state.currentUser != null) {
                            DrawerItemRow(
                                icon = Icons.Default.AccountCircle,
                                iconTint = CreditHomeBlue,
                                title = state.currentUser!!.displayName,
                                subtitle = "Rol: ${state.currentUser!!.role.displayName}",
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    showUserProfileDialog = true
                                }
                            )
                        }

                        if (isAuthEnabled && (state.currentUser == null || state.currentUser?.role == UserRole.MASTER)) {
                            DrawerItemRow(
                                icon = Icons.Default.AdminPanelSettings,
                                iconTint = PurpleAccent,
                                title = "Gestión de Usuarios",
                                subtitle = "Roles y permisos de equipo",
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    showUserManagementDialog = true
                                }
                            )
                        }

                        if (isSuperAdminEnabled && state.currentUser?.role == UserRole.SUPERADMIN) {
                            DrawerItemRow(
                                icon = Icons.Default.Security,
                                iconTint = RedError,
                                title = "Panel Superusuario",
                                subtitle = "Gestión de empresas y auditoría",
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    showSuperAdminDialog = true
                                }
                            )
                        }

                        if (isSmtpEnabled) {
                            DrawerItemRow(
                                icon = Icons.Default.Email,
                                iconTint = IndigoAccent,
                                title = "Notificaciones por Correo",
                                subtitle = "Alertas de cobro y comprobantes",
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    showSmtpConfigDialog = true
                                }
                            )
                        }

                        if (isRabbitEnabled) {
                            DrawerItemRow(
                                icon = Icons.Default.SyncAlt,
                                iconTint = OrangeDark,
                                title = "Sincronización en la Nube",
                                subtitle = "Conexión y respaldo en tiempo real",
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    showRabbitMqConfigDialog = true
                                }
                            )
                        }

                        if (isAuditEnabled && (state.currentUser == null || state.currentUser?.role in listOf(UserRole.MASTER, UserRole.SUPERADMIN))) {
                            DrawerItemRow(
                                icon = Icons.Default.HistoryEdu,
                                iconTint = AmberWarningDark,
                                title = "Registro de Auditoría",
                                subtitle = "Historial de cambios y operaciones",
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    showAuditLogDialog = true
                                }
                            )
                        }

                        if (isUpdateEnabled) {
                            DrawerItemRow(
                                icon = Icons.Default.SystemUpdate,
                                iconTint = SkyBlueAction,
                                title = "Buscar Actualizaciones",
                                subtitle = "Verificar versión APK en servidor",
                                onClick = {
                                    coroutineScope.launch {
                                        drawerState.close()
                                        viewModel.checkForUpdates()
                                        snackbarHostState.showSnackbar("Buscando actualización en servidor...")
                                    }
                                }
                            )
                        }

                        if (state.currentUser != null) {
                            DrawerItemRow(
                                icon = Icons.AutoMirrored.Filled.Logout,
                                iconTint = RedError,
                                title = "Cerrar Sesión",
                                subtitle = "Salir de la cuenta actual",
                                onClick = {
                                    coroutineScope.launch {
                                        drawerState.close()
                                        viewModel.logout()
                                    }
                                }
                            )
                        }
                    }

                    // Herramientas adicionales de simulación si está en el simulador
                    if (state.currentScreen == AppScreen.SIMULATOR) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        DrawerSectionTitle("Opciones de Cuota y Cálculo")

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Redondeo de Cuota", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Redondea al entero superior", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.isRoundUpEnabled, onCheckedChange = { viewModel.toggleRoundUp(it) })
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ocultar Interés", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Modo Cliente (vista simplificada)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.hideInterest, onCheckedChange = { viewModel.toggleHideInterest(it) })
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Interés Compuesto Directo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(if (state.isCompoundInterest) "Interés Compuesto Directo" else "Sistema Francés (Tradicional)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.isCompoundInterest, onCheckedChange = { viewModel.toggleCompoundInterest(it) })
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        DrawerSectionTitle("Herramientas de Cálculo")

                        DrawerItemRow(
                            icon = Icons.Default.Speed,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Calcular Plazo por Cuota ($)",
                            subtitle = "Ajustar cuota mensual deseada",
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showTargetMonthlyPaymentDialog = true
                            }
                        )

                        DrawerItemRow(
                            icon = Icons.Default.Savings,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Simular Abono Extraordinario",
                            subtitle = "Pago anticipado a capital",
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showEarlyPaymentDialog = true
                            }
                        )

                        DrawerItemRow(
                            icon = Icons.Default.SmartToy,
                            iconTint = GreenSuccess,
                            title = "Asistente de Pagos 🎙️",
                            subtitle = "Registro rápido por voz y texto",
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showPaymentBotDialog = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // BRANDING & MARCA DE LA APP
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.alfas.amortizacioncredito.R.drawable.ic_credithome_logo),
                                contentDescription = "CreditHome Logo",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CreditHome",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Sistema de Gestión de Créditos & Cobranzas",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Versión 2.4.0",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        ) {
            Crossfade(
                targetState = state.currentScreen,
                animationSpec = tween(durationMillis = 140),
                label = "screen_crossfade"
            ) { currentScreen ->
            if (currentScreen == AppScreen.DASHBOARD) {
                DashboardScreen(
                state = state,
                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                onOpenNotifications = { showNotificationCenterDialog = true },
                onNavigateToSimulator = { client ->
                    viewModel.navigateToSimulator(client)
                },
                onOpenClientList = { showClientListDialog = true },
                onOpenNewClient = {
                    isEditingClientDialog = false
                    showSaveClientDialog = true
                },
                onOpenBusinessProfile = { showBusinessProfileDialog = true },
                onOpenBackupDialog = { showBackupDialog = true },
                onOpenSignatureVault = { showSignatureVaultDialog = true },
                onOpenPaymentAssistant = { showPaymentBotDialog = true },
                onExportExcel = {
                    val prefix = "Cartera_CreditHome"
                    saveExcelLauncher.launch("${prefix}_${System.currentTimeMillis()}.xlsx")
                },
                modifier = modifier
            )
        } else {
            val unreadSimNotificationsCount = remember(state.notifications) { state.notifications.count { !it.isRead } }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menú Principal"
                                )
                            }
                        },
                        title = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp)
                            ) {
                                Text(
                                    text = if (state.activeClient != null) state.activeClient!!.name else "Simulador de Crédito",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (state.activeClient != null) "Tabla de Amortización" else "Modo Libre / Cotizador",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            // Botón Asistente de Pagos en Simulator
                            IconButton(onClick = { showPaymentBotDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "Asistente de Pagos",
                                    tint = GreenSuccess
                                )
                            }

                            // Campanita de Notificaciones en Simulator
                            IconButton(onClick = { showNotificationCenterDialog = true }) {
                                BadgedBox(
                                    badge = {
                                        if (unreadSimNotificationsCount > 0) {
                                            Badge(
                                                containerColor = RedError,
                                                contentColor = Color.White
                                            ) {
                                                 Text(
                                                     text = if (unreadSimNotificationsCount > 99) "99+" else "$unreadSimNotificationsCount",
                                                     fontSize = 9.sp,
                                                     fontWeight = FontWeight.Bold
                                                 )
                                             }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (unreadSimNotificationsCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                        contentDescription = "Notificaciones",
                                        tint = if (unreadSimNotificationsCount > 0) AmberWarningDark else MaterialTheme.colorScheme.onSurfaceVariant
                                     )
                                }
                            }

                            // Menú desplegable para acciones secundarias (Exportar, Compartir, Directorio, Eliminar Cliente, Reset)
                            Box {
                                IconButton(onClick = { showTopBarOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Más opciones"
                                    )
                                }

                                DropdownMenu(
                                    expanded = showTopBarOverflowMenu,
                                    onDismissRequest = { showTopBarOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Directorio de Clientes") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Groups,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showTopBarOverflowMenu = false
                                            showClientListDialog = true
                                        }
                                    )

                                    if (state.summary != null && state.summary!!.schedule.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Exportar Excel (.xlsx)") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.FileDownload,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showTopBarOverflowMenu = false
                                                val clientPrefix = state.activeClient?.name?.replace(" ", "_") ?: "Tabla_Amortizacion"
                                                saveExcelLauncher.launch("${clientPrefix}_${System.currentTimeMillis()}.xlsx")
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Compartir Tabla Excel") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showTopBarOverflowMenu = false
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val shareIntent = excelUseCase.createShareIntent(context, state)
                                                    if (shareIntent != null) {
                                                        val chooser = Intent.createChooser(
                                                            shareIntent,
                                                            "Compartir Tabla de Amortización"
                                                        )
                                                        context.startActivity(chooser)
                                                    } else {
                                                        snackbarHostState.showSnackbar("Error al generar archivo para compartir")
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    if (state.activeClient != null) {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Eliminar Cliente Actual", color = RedError) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteForever,
                                                    contentDescription = null,
                                                    tint = RedError
                                                )
                                            },
                                            onClick = {
                                                showTopBarOverflowMenu = false
                                                showDeleteActiveClientDialog = true
                                            }
                                        )
                                    } else {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Limpiar Simulación", color = RedError) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteSweep,
                                                    contentDescription = null,
                                                    tint = RedError
                                                )
                                            },
                                            onClick = {
                                                showTopBarOverflowMenu = false
                                                showResetConfirmDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                modifier = modifier
            ) { innerPadding ->
                Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Barra de Cliente Activo / Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (state.activeClient != null) {
                        val paidCount = state.paymentRecords.values.count { it.isPaid }
                        val totalMonths = state.summary?.actualMonths ?: 1

                        InputChip(
                            selected = true,
                            onClick = { showClientListDialog = true },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                selectedTrailingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            border = InputChipDefaults.inputChipBorder(
                                enabled = true,
                                selected = true,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            label = {
                                Text(
                                    text = "${state.activeClient!!.name} ($paidCount/$totalMonths)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        viewModel.selectClient(null)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Regresaste al Simulador Libre")
                                        }
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar cliente y volver a modo libre",
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .height(34.dp)
                                .weight(1f, fill = false)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    isEditingClientDialog = false
                                    showSaveClientDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Nuevo", fontSize = 11.sp)
                            }

                            TextButton(
                                onClick = {
                                    isEditingClientDialog = true
                                    showSaveClientDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Editar", fontSize = 11.sp)
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showClientListDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clientes", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    isEditingClientDialog = false
                                    showSaveClientDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar", fontSize = 12.sp)
                            }
                        }

                        // Botón de Borrar / Limpiar Simulación directamente accesible en pantalla
                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = RedError
                            ),
                            border = BorderStroke(1.dp, RedError.copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Limpiar Simulación",
                                tint = RedError,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                val isClientActive = state.activeClient != null

                // Indicador de parámetros protegidos si hay cliente activo
                if (isClientActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isEditingClientDialog = true
                                showSaveClientDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Condiciones protegidas. Toca 'Editar' para modificarlas.",
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Entradas de parámetros
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.priceInput,
                        onValueChange = if (isClientActive) { _ -> } else viewModel::onPriceChanged,
                        readOnly = isClientActive,
                        trailingIcon = if (isClientActive) {
                            { Icon(Icons.Default.Lock, contentDescription = "Protegido", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(15.dp)) }
                        } else null,
                        label = { Text("Precio Total") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.downPaymentInput,
                        onValueChange = if (isClientActive) { _ -> } else viewModel::onDownPaymentChanged,
                        readOnly = isClientActive,
                        trailingIcon = if (isClientActive) {
                            { Icon(Icons.Default.Lock, contentDescription = "Protegido", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(15.dp)) }
                        } else null,
                        label = { Text("Entrada") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val price = state.priceInput.toDoubleOrNull() ?: 0.0
                val downPayment = state.downPaymentInput.toDoubleOrNull() ?: 0.0
                val financed = maxOf(0.0, price - downPayment)
                val rate = state.annualRateInput.toDoubleOrNull() ?: 0.0
                val minMonthlyInterest = financed * (rate / 100.0 / 12.0)
                val desiredNum = state.desiredMonthlyPaymentInput.toDoubleOrNull() ?: 0.0
                val currentMonths = ((state.yearsInput.toDoubleOrNull() ?: 0.0) * 12.0).let { kotlin.math.round(it).toInt() }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.annualRateInput,
                        onValueChange = if (isClientActive) { _ -> } else viewModel::onRateChanged,
                        readOnly = isClientActive,
                        trailingIcon = if (isClientActive) {
                            { Icon(Icons.Default.Lock, contentDescription = "Protegido", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(15.dp)) }
                        } else null,
                        label = { Text("Tasa Anual (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.yearsInput,
                        onValueChange = if (isClientActive) { _ -> } else viewModel::onYearsChanged,
                        readOnly = isClientActive,
                        trailingIcon = if (isClientActive) {
                            { Icon(Icons.Default.Lock, contentDescription = "Protegido", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(15.dp)) }
                        } else null,
                        label = { Text("Plazo (Años)") },
                        supportingText = {
                            if (currentMonths > 0) {
                                Text("= $currentMonths meses", fontSize = 11.sp, maxLines = 1)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Campo Rápido: Cuota Mensual Deseada ($) (Solo visible en Modo Simulador Libre para evitar cruces de cálculo con clientes ya guardados)
                if (state.activeClient == null) {
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = state.desiredMonthlyPaymentInput,
                        onValueChange = viewModel::onDesiredMonthlyPaymentChanged,
                        label = { Text("Cuota Mensual Deseada ($) — Simulación Rápida") },
                        placeholder = { Text("Ej: 1000 (Calcula el plazo automáticamente)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (state.desiredMonthlyPaymentInput.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearDesiredMonthlyPayment) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Limpiar cuota",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        supportingText = {
                            if (state.desiredMonthlyPaymentInput.isNotEmpty()) {
                                if (desiredNum > minMonthlyInterest && currentMonths > 0) {
                                    Text(
                                        "⚡ Plazo ajustado a $currentMonths meses (${formatMonthsToYears(currentMonths)})",
                                        fontSize = 11.sp,
                                        color = GreenSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (desiredNum > 0.0 && desiredNum <= minMonthlyInterest) {
                                    Text(
                                        "⚠️ La cuota debe ser mayor a ${formatCurrency(minMonthlyInterest)} para amortizar capital",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Banner de Abono Extraordinario Activo (si existe)
                AnimatedVisibility(visible = state.extraPaymentAmount > 0 && state.extraPaymentMonth > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Abono: ${formatCurrency(state.extraPaymentAmount)} en Mes ${state.extraPaymentMonth} (¡Ahorras ${state.summary?.monthsSaved ?: 0} cuotas!)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearExtraPayment() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar abono",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Tarjetas de Resumen
                state.summary?.let { summary ->
                    SummaryCards(summary, hideInterest = state.hideInterest)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botones de acción rápida para exportar y simular abono / meta de cuota
                if (state.summary != null && state.summary!!.schedule.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clientPrefix = state.activeClient?.name?.replace(" ", "_") ?: "Tabla_Amortizacion"
                                saveExcelLauncher.launch("${clientPrefix}_${System.currentTimeMillis()}.xlsx")
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Excel", fontSize = 11.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { showEarlyPaymentDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Savings,
                                contentDescription = null,
                                tint = CreditHomeEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Abono", fontSize = 11.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { showTargetMonthlyPaymentDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Meta Cuota", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Tabla centrada y adaptada con trazabilidad de pagos y scroll fluido
                val schedule = state.summary?.schedule ?: emptyList()
                if (schedule.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        val tableModifier = if (state.hideInterest) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxHeight()
                                .horizontalScroll(scrollHorizontalState)
                        }

                        Column(modifier = tableModifier) {
                            TableHeader(hideInterest = state.hideInterest)

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (state.hideInterest) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                            ) {
                                items(
                                    count = schedule.size,
                                    key = { index -> schedule[index].month },
                                    contentType = { _ -> "amortization_row" }
                                ) { index ->
                                    val item = schedule[index]
                                    val record = state.paymentRecords[item.month]
                                    TableRow(
                                        item = item,
                                        isEven = index % 2 == 0,
                                        hideInterest = state.hideInterest,
                                        paymentRecord = record,
                                        onPaymentClick = {
                                            selectedPaymentItem = item
                                        },
                                        onReceiptClick = {
                                            viewingReceiptItem = item
                                        }
                                    )
                                }
                            }

                            state.summary?.let { summary ->
                                TableFooter(
                                    summary = summary,
                                    hideInterest = state.hideInterest,
                                    paidCount = state.paymentRecords.values.count { it.isPaid }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}
}

@Composable
private fun SummaryCards(summary: AmortizationSummary, hideInterest: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Cuota Mensual", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = formatCurrency(summary.monthlyPayment),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GreenAccent
                )
            }
        }

        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (hideInterest) {
                    Text("Costo Total", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = formatCurrency(summary.totalCost),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CreditHomeSky
                    )
                } else {
                    Text("Interés Total", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = formatCurrency(summary.totalInterest),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OrangeLight
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeader(hideInterest: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CellText("Pago", modifier = Modifier.width(42.dp), isHeader = true, align = TextAlign.Center)
        if (hideInterest) {
            CellText("Mes", modifier = Modifier.weight(0.5f), isHeader = true, align = TextAlign.Center)
            CellText("Fecha", modifier = Modifier.weight(1.35f), isHeader = true, align = TextAlign.Center)
            CellText("Cuota", modifier = Modifier.weight(1.15f), isHeader = true)
            CellText("Saldo", modifier = Modifier.weight(1.15f), isHeader = true)
        } else {
            CellText("Mes", modifier = Modifier.width(36.dp), isHeader = true, align = TextAlign.Center)
            CellText("Fecha", modifier = Modifier.width(96.dp), isHeader = true, align = TextAlign.Center)
            CellText("Cuota", modifier = Modifier.width(76.dp), isHeader = true)
            CellText("Interés", modifier = Modifier.width(76.dp), isHeader = true)
            CellText("Capital", modifier = Modifier.width(76.dp), isHeader = true)
            CellText("Saldo", modifier = Modifier.width(80.dp), isHeader = true)
        }
    }
}

@Composable
private fun DualDateCell(
    dueDate: String,
    paymentRecord: PaymentRecord?,
    modifier: Modifier = Modifier
) {
    val isPaid = paymentRecord?.isPaid == true
    val payDateShort = remember(paymentRecord?.paymentDate, isPaid) {
        if (!isPaid) null
        else {
            val raw = paymentRecord?.paymentDate?.trim().orEmpty().substringBefore(" ").trim()
            if (raw.matches(Regex("""\d{2}/\d{2}/\d{4}"""))) {
                val parts = raw.split("/")
                "${parts[0]}/${parts[1]}/${parts[2].takeLast(2)}"
            } else raw.ifBlank {
                null
            }
        }
    }

    val (isOverdue, isDueToday) = remember(dueDate, isPaid) {
        if (!isPaid && dueDate.isNotBlank()) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            try {
                val d = sdf.parse(dueDate)
                if (d != null) {
                    val todayCal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val todayStart = todayCal.time
                    val overdue = d.before(todayStart)
                    val dueToday = d == todayStart
                    Pair(overdue, dueToday)
                } else Pair(false, false)
            } catch (_: Exception) {
                Pair(false, false)
            }
        } else Pair(false, false)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Línea 1: Fecha Límite Programada (Cuándo tiene que pagar)
        Text(
            text = if (dueDate.isNotBlank()) dueDate else "-",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = when {
                isOverdue -> RedError
                isDueToday -> AmberWarningDark
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Línea 2: Fecha Real de Pago (Cuándo pagó) / Estado
        if (isPaid) {
            val displayText = if (payDateShort != null) "Pagó: $payDateShort" else "✓ Pagado"
            Text(
                text = displayText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = GreenSuccess,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        } else if (isOverdue) {
            Text(
                text = "¡Vencido!",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = RedError,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        } else if (isDueToday) {
            Text(
                text = "¡Vence Hoy!",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = AmberWarningDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Pendiente",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TableRow(
    item: AmortizationItem,
    isEven: Boolean,
    hideInterest: Boolean,
    paymentRecord: PaymentRecord?,
    onPaymentClick: () -> Unit,
    onReceiptClick: () -> Unit
) {
    val isPaid = paymentRecord?.isPaid == true

    val bg = if (isPaid) {
        GreenSuccess.copy(alpha = 0.12f)
    } else if (isEven) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(enabled = isPaid) { onReceiptClick() }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón / Ícono de Estado de Pago
        IconButton(
            onClick = onPaymentClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isPaid) "Pagado (toca para editar)" else "Pendiente",
                tint = if (isPaid) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(19.dp)
            )
        }

        if (hideInterest) {
            CellText(item.month.toString(), modifier = Modifier.weight(0.5f), align = TextAlign.Center)
            DualDateCell(dueDate = item.dueDate, paymentRecord = paymentRecord, modifier = Modifier.weight(1.35f))
            CellText(formatCurrency(item.payment), modifier = Modifier.weight(1.15f))
            CellText(formatCurrency(item.remainingBalance), modifier = Modifier.weight(1.15f))
        } else {
            CellText(item.month.toString(), modifier = Modifier.width(36.dp), align = TextAlign.Center)
            DualDateCell(dueDate = item.dueDate, paymentRecord = paymentRecord, modifier = Modifier.width(96.dp))
            CellText(formatCurrency(item.payment), modifier = Modifier.width(76.dp))
            CellText(formatCurrency(item.interest), modifier = Modifier.width(76.dp), color = OrangeLight)
            CellText(formatCurrency(item.principal), modifier = Modifier.width(76.dp), color = GreenAccent)
            CellText(formatCurrency(item.remainingBalance), modifier = Modifier.width(80.dp))
        }
    }
}

@Composable
private fun TableFooter(
    summary: AmortizationSummary,
    hideInterest: Boolean,
    paidCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CellText("✅ $paidCount", modifier = Modifier.width(42.dp), isHeader = true, align = TextAlign.Center)
        if (hideInterest) {
            CellText("Totales", modifier = Modifier.weight(0.5f), isHeader = true, align = TextAlign.Center)
            CellText("", modifier = Modifier.weight(1.35f), isHeader = true, align = TextAlign.Center)
            CellText(formatCurrency(summary.totalPaid), modifier = Modifier.weight(1.15f), isHeader = true)
            CellText(formatCurrency(0.0), modifier = Modifier.weight(1.15f), isHeader = true)
        } else {
            CellText("Totales", modifier = Modifier.width(36.dp), isHeader = true, align = TextAlign.Center)
            CellText("", modifier = Modifier.width(96.dp), isHeader = true, align = TextAlign.Center)
            CellText(formatCurrency(summary.totalPaid), modifier = Modifier.width(76.dp), isHeader = true)
            CellText(formatCurrency(summary.totalInterest), modifier = Modifier.width(76.dp), isHeader = true, color = OrangeLight)
            CellText(formatCurrency(summary.totalPrincipalPaid), modifier = Modifier.width(76.dp), isHeader = true, color = GreenAccent)
            CellText(formatCurrency(0.0), modifier = Modifier.width(80.dp), isHeader = true)
        }
    }
}

@Composable
private fun CellText(
    text: String,
    modifier: Modifier = Modifier,
    isHeader: Boolean = false,
    color: Color = Color.Unspecified,
    align: TextAlign = TextAlign.End
) {
    Text(
        text = text,
        modifier = modifier,
        style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        textAlign = align,
        color = color,
        maxLines = 1
    )
}

private val currencyFormatter = ThreadLocal.withInitial {
    java.text.DecimalFormat("$#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale.US))
}

private fun formatCurrency(amount: Double): String {
    return currencyFormatter.get()?.format(amount) ?: String.format(Locale.US, "$%,.2f", amount)
}

private fun formatMonthsToYears(months: Int): String {
    val years = months / 12
    val remainingMonths = months % 12
    return when {
        years > 0 && remainingMonths > 0 -> "$years año(s) y $remainingMonths mes(es)"
        years > 0 -> "$years año(s)"
        else -> "$months mes(es)"
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
