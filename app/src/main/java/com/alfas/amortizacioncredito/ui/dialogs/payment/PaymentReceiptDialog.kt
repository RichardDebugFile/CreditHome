package com.alfas.amortizacioncredito.ui.dialogs.payment

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.ui.theme.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.domain.usecase.ExportPaymentReceiptPdfUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.alfas.amortizacioncredito.data.feature.FeatureFlagManager
import com.alfas.amortizacioncredito.data.feature.FeatureKey
import com.alfas.amortizacioncredito.data.model.VoucherConfig
import com.alfas.amortizacioncredito.ui.components.*
import com.alfas.amortizacioncredito.ui.dialogs.business.SignatureCaptureDialog
import com.alfas.amortizacioncredito.ui.dialogs.business.VoucherCustomizationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentReceiptDialog(
    record: PaymentRecord,
    month: Int,
    totalMonths: Int,
    scheduledPayment: Double,
    interest: Double,
    principal: Double,
    previousBalance: Double,
    remainingBalance: Double,
    clientName: String?,
    clientDocumentOrPhone: String?,
    clientEmail: String? = null,
    businessProfile: BusinessProfile = BusinessProfile(),
    voucherConfig: com.alfas.amortizacioncredito.data.model.VoucherConfig = com.alfas.amortizacioncredito.data.model.VoucherConfig(),
    onEditBusinessProfile: () -> Unit = {},
    onSaveVoucherConfig: ((com.alfas.amortizacioncredito.data.model.VoucherConfig) -> Unit)? = null,
    onSaveRecord: ((record: PaymentRecord) -> Unit)? = null,
    onSendEmail: ((record: PaymentRecord) -> Unit)? = null,
    onOpenFullEditor: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val pdfUseCase = remember { ExportPaymentReceiptPdfUseCase() }
    val isSmtpEnabled = remember { FeatureFlagManager(context).isEnabled(FeatureKey.SMTP_EMAIL) }

    var activeVoucherConfig by remember(voucherConfig) { mutableStateOf(voucherConfig) }
    var showVoucherCustomizationDialog by remember { mutableStateOf(false) }

    // Estados editables en tiempo real
    var amountInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", if (record.amountPaid > 0) record.amountPaid else scheduledPayment)) }
    var paymentMethod by remember { mutableStateOf(record.paymentMethod.ifEmpty { "Transferencia" }) }
    var referenceNumber by remember { mutableStateOf(record.referenceNumber) }
    var bankOrEntity by remember { mutableStateOf(record.bankOrEntity) }
    var payerName by remember { mutableStateOf(record.payerName) }
    var paymentDate by remember { mutableStateOf(record.paymentDate) }
    var noteInput by remember { mutableStateOf(record.note) }

    // Estados de firma digital / física
    var signaturePath by remember { mutableStateOf(record.signaturePath) }
    var signatureType by remember { mutableStateOf(record.signatureType) }
    var signedBy by remember { mutableStateOf(record.signedBy) }
    var signedAt by remember { mutableLongStateOf(record.signedAt) }
    var signatureDetails by remember { mutableStateOf(record.signatureDetails) }
    var showSignatureDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Vista Voucher, 1: Vista Previa PDF
    var showEditFields by remember { mutableStateOf(false) }
    var fullScreenImageToShow by remember { mutableStateOf<String?>(null) }
    var pdfScale by remember { mutableFloatStateOf(1f) }
    var pdfOffsetX by remember { mutableFloatStateOf(0f) }
    var pdfOffsetY by remember { mutableFloatStateOf(0f) }
    var showFullScreenPdfViewer by remember { mutableStateOf(false) }
    var receiptImagePath by remember { mutableStateOf(record.receiptImagePath) }
    var receiptToDeleteIndex by remember { mutableStateOf<Int?>(null) }

    val parsedAmount = amountInput.toDoubleOrNull() ?: scheduledPayment
    val extraPayment = if (parsedAmount > scheduledPayment) parsedAmount - scheduledPayment else 0.0

    val currentWorkingRecord = remember(
        record.month, record.isPaid, paymentDate, parsedAmount, paymentMethod, referenceNumber, bankOrEntity, payerName, receiptImagePath, noteInput,
        signaturePath, signatureType, signedBy, signedAt, signatureDetails, record.emailSentCount, record.lastEmailSentAt
    ) {
        record.copy(
            amountPaid = parsedAmount,
            paymentMethod = paymentMethod,
            referenceNumber = referenceNumber.trim(),
            bankOrEntity = bankOrEntity.trim(),
            payerName = payerName.trim(),
            paymentDate = paymentDate,
            receiptImagePath = receiptImagePath,
            note = noteInput.trim(),
            signaturePath = signaturePath,
            signatureType = signatureType,
            signedBy = signedBy,
            signedAt = signedAt,
            signatureDetails = signatureDetails
        )
    }

    val receiptNumber = remember(currentWorkingRecord, month) {
        if (currentWorkingRecord.referenceNumber.isNotBlank()) {
            currentWorkingRecord.referenceNumber
        } else {
            "VOU-${String.format(Locale.US, "%04d", month * 73 + 1042)}"
        }
    }

    if (showSignatureDialog) {
        SignatureCaptureDialog(
            signerName = signedBy.ifBlank { businessProfile.businessName.ifBlank { "Administrador" } },
            voucherInfo = "Cuota #$month - $receiptNumber",
            onSignatureSuccess = { path, type, signer, _, details ->
                val nowMs = System.currentTimeMillis()
                signaturePath = path
                signatureType = type
                signedBy = signer
                signedAt = nowMs
                signatureDetails = details
                showSignatureDialog = false
                val updated = currentWorkingRecord.copy(
                    signaturePath = path,
                    signatureType = type,
                    signedBy = signer,
                    signedAt = nowMs,
                    signatureDetails = details
                )
                onSaveRecord?.invoke(updated)
                Toast.makeText(context, "Firma registrada exitosamente", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSignatureDialog = false }
        )
    }

    // Renderizado en tiempo real de la página PDF para previsualización
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(currentWorkingRecord, businessProfile, activeVoucherConfig) {
        isRenderingPdf = true
        withContext(Dispatchers.IO) {
            val bmp = pdfUseCase.renderPdfToBitmap(
                context = context,
                record = currentWorkingRecord,
                month = month,
                totalMonths = totalMonths,
                scheduledPayment = scheduledPayment,
                interest = interest,
                principal = principal,
                previousBalance = previousBalance,
                remainingBalance = remainingBalance,
                clientName = clientName,
                clientDocumentOrPhone = clientDocumentOrPhone,
                businessProfile = businessProfile,
                voucherConfig = activeVoucherConfig
            )
            withContext(Dispatchers.Main) {
                pdfBitmap = bmp
                isRenderingPdf = false
            }
        }
    }

    val logoBitmap = remember(businessProfile.logoPath) {
        if (businessProfile.logoPath.isNotBlank() && File(businessProfile.logoPath).exists()) {
            try {
                BitmapFactory.decodeFile(businessProfile.logoPath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    // Modal de personalización de campos del voucher
    if (showVoucherCustomizationDialog) {
        VoucherCustomizationDialog(
            initialConfig = activeVoucherConfig,
            onDismiss = { showVoucherCustomizationDialog = false },
            onSaveConfig = { newCfg ->
                activeVoucherConfig = newCfg
                onSaveVoucherConfig?.invoke(newCfg)
                showVoucherCustomizationDialog = false
            }
        )
    }

    // Modal de imagen de comprobante a pantalla completa con soporte de ZOOM interactivo y barra de herramientas
    if (fullScreenImageToShow != null) {
        ZoomableImageViewerDialog(
            imagePath = fullScreenImageToShow,
            title = "Vista de Comprobante",
            onDismiss = { fullScreenImageToShow = null }
        )
    }

    // Modal de Visor de PDF a Pantalla Completa con Zoom y Paneo Avanzado
    if (showFullScreenPdfViewer && pdfBitmap != null) {
        FullScreenDocumentViewerDialog(
            bitmap = pdfBitmap,
            title = "Comprobante PDF Oficial",
            subtitle = "Mes $month • $receiptNumber",
            onShare = {
                pdfUseCase.sharePdf(
                    context = context,
                    record = currentWorkingRecord,
                    month = month,
                    totalMonths = totalMonths,
                    scheduledPayment = scheduledPayment,
                    interest = interest,
                    principal = principal,
                    previousBalance = previousBalance,
                    remainingBalance = remainingBalance,
                    clientName = clientName,
                    clientDocumentOrPhone = clientDocumentOrPhone,
                    businessProfile = businessProfile,
                    voucherConfig = activeVoucherConfig
                )
            },
            onDismiss = { showFullScreenPdfViewer = false }
        )
    }

    // Launcher para guardar PDF en la memoria del dispositivo
    val safeClient = (clientName ?: "Cliente").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
    val defaultPdfFileName = "Comprobante_Pago_${safeClient}_Mes_${month}.pdf"

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            try {
                context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                    pdfUseCase.exportToStream(
                        outputStream = outputStream,
                        record = currentWorkingRecord,
                        month = month,
                        totalMonths = totalMonths,
                        scheduledPayment = scheduledPayment,
                        interest = interest,
                        principal = principal,
                        previousBalance = previousBalance,
                        remainingBalance = remainingBalance,
                        clientName = clientName,
                        clientDocumentOrPhone = clientDocumentOrPhone,
                        businessProfile = businessProfile,
                        voucherConfig = activeVoucherConfig
                    )
                }
                onSaveRecord?.invoke(currentWorkingRecord)
                Toast.makeText(context, "Comprobante PDF guardado exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                Toast.makeText(context, "Error al guardar PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Texto formateado para compartir por WhatsApp u otras vías
    val shareableReceiptText = remember(currentWorkingRecord, clientName, clientDocumentOrPhone, parsedAmount, receiptNumber, businessProfile, activeVoucherConfig) {
        buildString {
            appendLine("═══════════════════════════")
            appendLine("   🧾 COMPROBANTE DE PAGO")
            if (activeVoucherConfig.showBusinessName && businessProfile.businessName.isNotBlank()) {
                appendLine("   🏢 ${businessProfile.businessName.uppercase()}")
            }
            if (activeVoucherConfig.showIssuerName && businessProfile.issuerName.isNotBlank()) {
                appendLine("   👤 Emisor: ${businessProfile.issuerName}")
            }
            if (activeVoucherConfig.showRuc && businessProfile.ruc.isNotBlank()) {
                appendLine("   RUC: ${businessProfile.ruc}")
            }
            if (activeVoucherConfig.showAddress && businessProfile.address.isNotBlank()) {
                appendLine("   📍 ${businessProfile.address}")
            }
            appendLine("═══════════════════════════")
            appendLine("N° Comprobante: $receiptNumber")
            appendLine("Fecha y Hora: ${currentWorkingRecord.paymentDate.ifEmpty { "N/D" }}")
            appendLine("Estado: ✅ PAGADO Y REGISTRADO")
            appendLine("---------------------------")
            if (!clientName.isNullOrBlank()) {
                appendLine("👤 Cliente: $clientName")
            }
            if (activeVoucherConfig.showClientDocOrPhone && !clientDocumentOrPhone.isNullOrBlank()) {
                appendLine("📄 Identificación / Tel: $clientDocumentOrPhone")
            }
            appendLine("---------------------------")
            appendLine("📌 DETALLE DE LA CUOTA:")
            appendLine("• Cuota: Mes $month de $totalMonths")
            if (activeVoucherConfig.showPreviousBalance && previousBalance > 0.01) {
                appendLine("• Saldo Anterior: ${formatCurrency(previousBalance)}")
            }
            if (activeVoucherConfig.showScheduledPayment) {
                appendLine("• Cuota Programada: ${formatCurrency(scheduledPayment)}")
            }
            if (activeVoucherConfig.showPrincipal) {
                appendLine("• Capital: ${formatCurrency(principal)}")
            }
            if (activeVoucherConfig.showInterest) {
                appendLine("• Interés: ${formatCurrency(interest)}")
            }
            if (activeVoucherConfig.showExtraPayment && extraPayment > 0.01) {
                appendLine("• Abono Extra a Capital: ${formatCurrency(extraPayment)}")
            }
            if (activeVoucherConfig.showTotalReceived) {
                appendLine("---------------------------")
                appendLine("💵 TOTAL ABONADO: ${formatCurrency(parsedAmount)}")
            }
            if (activeVoucherConfig.showRemainingBalance) {
                appendLine("📉 SALDO RESTANTE: ${formatCurrency(remainingBalance)}")
            }
            appendLine("---------------------------")
            appendLine("🏦 DATOS DE TRANSACCIÓN:")
            if (activeVoucherConfig.showPaymentMethod) {
                appendLine("• Medio de Pago: ${currentWorkingRecord.paymentMethod.ifEmpty { "Transferencia" }}")
            }
            if (activeVoucherConfig.showBankEntity && currentWorkingRecord.bankOrEntity.isNotBlank()) {
                appendLine("• Entidad / Banco: ${currentWorkingRecord.bankOrEntity}")
            }
            if (activeVoucherConfig.showReferenceNumber && currentWorkingRecord.referenceNumber.isNotBlank()) {
                appendLine("• Ref / N° Transacción: ${currentWorkingRecord.referenceNumber}")
            }
            if (activeVoucherConfig.showPayerName && currentWorkingRecord.payerName.isNotBlank()) {
                appendLine("• Titular / Depositante: ${currentWorkingRecord.payerName}")
            }
            if (activeVoucherConfig.showNotes && currentWorkingRecord.note.isNotBlank()) {
                appendLine("• Observaciones: ${currentWorkingRecord.note}")
            }

            val activeCustomFields = activeVoucherConfig.customFields.filter { it.isEnabled }
            if (activeCustomFields.isNotEmpty()) {
                appendLine("---------------------------")
                appendLine("📋 INFORMACIÓN ADICIONAL:")
                activeCustomFields.forEach { cf ->
                    if (cf.value.isNotBlank()) {
                        appendLine("• ${cf.label}: ${cf.value}")
                    } else {
                        appendLine("• ${cf.label}")
                    }
                }
            }

            if (activeVoucherConfig.showSignature && currentWorkingRecord.isSigned) {
                appendLine("---------------------------")
                appendLine("✍️ VALIDACIÓN Y FIRMA:")
                appendLine("• Firmado por: ${currentWorkingRecord.signedBy}")
                appendLine("• Tipo: ${currentWorkingRecord.signatureType.ifEmpty { "Digital" }}")
                if (currentWorkingRecord.signatureDetails.isNotBlank()) {
                    appendLine("• Certificación: ${currentWorkingRecord.signatureDetails}")
                }
            }

            appendLine("═══════════════════════════")
            if (activeVoucherConfig.showFooterNote) {
                val footer = if (activeVoucherConfig.customFooterText.isNotBlank()) activeVoucherConfig.customFooterText else "Documento digital generado por ${businessProfile.businessName}"
                appendLine(footer)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header con pestañas de visualización: Voucher vs Previsualización PDF
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Voucher", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Vista Previa PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // VISTA 1: PREVISUALIZACIÓN DEL DOCUMENTO PDF EN TIEMPO REAL
                if (selectedTab == 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Documento PDF Oficial:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Barra de Herramientas de Zoom y Pantalla Completa
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    pdfScale = (pdfScale - 0.25f).coerceAtLeast(0.75f)
                                    if (pdfScale <= 1f) { pdfOffsetX = 0f; pdfOffsetY = 0f }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Alejar", modifier = Modifier.size(16.dp))
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable {
                                    pdfScale = 1f
                                    pdfOffsetX = 0f
                                    pdfOffsetY = 0f
                                }
                            ) {
                                Text(
                                    text = "${(pdfScale * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    pdfScale = (pdfScale + 0.25f).coerceAtMost(3.5f)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Acercar", modifier = Modifier.size(16.dp))
                            }

                            IconButton(
                                onClick = {
                                    pdfScale = 1f
                                    pdfOffsetX = 0f
                                    pdfOffsetY = 0f
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reiniciar Zoom", modifier = Modifier.size(16.dp))
                            }

                            IconButton(
                                onClick = { showFullScreenPdfViewer = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla Completa", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Text(
                        text = "💡 Pellizca para hacer zoom o arrastra para desplazarte por el documento.",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate200)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    pdfScale = (pdfScale * zoom).coerceIn(0.75f, 4f)
                                    val maxOffset = (pdfScale - 1f) * 600f
                                    if (pdfScale > 1f) {
                                        pdfOffsetX = (pdfOffsetX + pan.x * pdfScale).coerceIn(-maxOffset, maxOffset)
                                        pdfOffsetY = (pdfOffsetY + pan.y * pdfScale).coerceIn(-maxOffset * 1.5f, maxOffset * 1.5f)
                                    } else {
                                        pdfOffsetX = 0f
                                        pdfOffsetY = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRenderingPdf) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Generando vista previa...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (pdfBitmap != null) {
                            Image(
                                bitmap = pdfBitmap!!.asImageBitmap(),
                                contentDescription = "Previsualización PDF",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = pdfScale
                                        scaleY = pdfScale
                                        translationX = pdfOffsetX
                                        translationY = pdfOffsetY
                                    }
                                    .shadow(4.dp, RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("No se pudo generar la vista previa", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    // VISTA 0: VOUCHER DIGITAL
                    // Header Institucional de Empresa (Ecuador Style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (logoBitmap != null) {
                                Image(
                                    bitmap = logoBitmap,
                                    contentDescription = "Logo Empresa",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column {
                                Text(
                                    text = businessProfile.businessName.ifEmpty { "CreditHome" },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (businessProfile.issuerName.isNotBlank()) {
                                    Text(
                                        text = "Emisor: ${businessProfile.issuerName}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (businessProfile.ruc.isNotBlank()) {
                                    Text(
                                        text = "RUC/CI: ${businessProfile.ruc}",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showVoucherCustomizationDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Personalizar Campos del Voucher",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onEditBusinessProfile,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Storefront,
                                    contentDescription = "Configurar Logo y Datos de Empresa",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Fila con N° de Voucher y Badge Pagado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "COMPROBANTE DE PAGO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.3.sp
                            )
                            Text(
                                text = "N° $receiptNumber",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AppStatusBadge(
                            text = "PAGADO",
                            type = StatusBadgeType.SUCCESS
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cuerpo del Voucher
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                            if (!clientName.isNullOrBlank()) {
                                VoucherRow(label = "Cliente:", value = clientName, isBold = true)
                            }
                            if (activeVoucherConfig.showClientDocOrPhone && !clientDocumentOrPhone.isNullOrBlank()) {
                                VoucherRow(label = "Doc / Teléfono:", value = clientDocumentOrPhone)
                            }
                            VoucherRow(label = "Fecha de Pago:", value = currentWorkingRecord.paymentDate.ifEmpty { "Registrado" })

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            VoucherRow(label = "Cuota:", value = "Mes $month de $totalMonths", isBold = true)
                            if (activeVoucherConfig.showPreviousBalance && previousBalance > 0.01) {
                                VoucherRow(label = "Saldo Anterior:", value = formatCurrency(previousBalance))
                            }
                            if (activeVoucherConfig.showScheduledPayment) {
                                VoucherRow(label = "Cuota Prevista:", value = formatCurrency(scheduledPayment))
                            }
                            if (activeVoucherConfig.showPrincipal) {
                                VoucherRow(label = "Abono a Capital:", value = formatCurrency(principal))
                            }
                            if (activeVoucherConfig.showInterest) {
                                VoucherRow(label = "Interés del Mes:", value = formatCurrency(interest))
                            }

                            if (activeVoucherConfig.showExtraPayment && extraPayment > 0.01) {
                                VoucherRow(
                                    label = "Abono Extraordinario:",
                                    value = "+ ${formatCurrency(extraPayment)}",
                                    valueColor = GreenSuccess,
                                    isBold = true
                                )
                            }

                            if (activeVoucherConfig.showRemainingBalance) {
                                VoucherRow(
                                    label = "Nuevo Saldo Deuda:",
                                    value = formatCurrency(remainingBalance),
                                    valueColor = if (remainingBalance <= 0.01) GreenSuccess else MaterialTheme.colorScheme.primary,
                                    isBold = true
                                )
                            }

                            if (activeVoucherConfig.showTotalReceived) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TOTAL PAGADO:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatCurrency(parsedAmount),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = GreenSuccess
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            Text(
                                text = "SOPORTE DE LA TRANSACCIÓN",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (activeVoucherConfig.showPaymentMethod) {
                                VoucherRow(label = "Medio de Pago:", value = currentWorkingRecord.paymentMethod.ifEmpty { "Transferencia" })
                            }

                            if (activeVoucherConfig.showBankEntity && currentWorkingRecord.bankOrEntity.isNotBlank()) {
                                VoucherRow(label = "Banco / Entidad:", value = currentWorkingRecord.bankOrEntity)
                            }

                            if (activeVoucherConfig.showReferenceNumber && currentWorkingRecord.referenceNumber.isNotBlank()) {
                                VoucherRow(label = "N° Referencia / Comp:", value = currentWorkingRecord.referenceNumber, isBold = true)
                            }

                            if (activeVoucherConfig.showPayerName && currentWorkingRecord.payerName.isNotBlank()) {
                                VoucherRow(label = "Titular / Depositante:", value = currentWorkingRecord.payerName)
                            }

                            if (activeVoucherConfig.showNotes && currentWorkingRecord.note.isNotBlank()) {
                                VoucherRow(label = "Observaciones:", value = currentWorkingRecord.note)
                            }

                            // Campos Personalizados Dinámicos
                            activeVoucherConfig.customFields.filter { it.isEnabled }.forEach { cf ->
                                VoucherRow(label = "${cf.label}:", value = cf.value)
                            }

                            // SECCIÓN DE FIRMA DIGITAL / ELECTRÓNICA / FÍSICA
                            if (activeVoucherConfig.showSignature) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "FIRMA DEL COMPROBANTE",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (currentWorkingRecord.isSigned) {
                                            TextButton(
                                                onClick = {
                                                    signaturePath = ""
                                                    signatureType = ""
                                                    signedBy = ""
                                                    signedAt = 0L
                                                    signatureDetails = ""
                                                    val clearedRec = currentWorkingRecord.copy(
                                                        signaturePath = "",
                                                        signatureType = "",
                                                        signedBy = "",
                                                        signedAt = 0L,
                                                        signatureDetails = ""
                                                    )
                                                    onSaveRecord?.invoke(clearedRec)
                                                },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Quitar Firma",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "Quitar",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        TextButton(
                                            onClick = { showSignatureDialog = true },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (currentWorkingRecord.isSigned) "Cambiar" else "Firmar Ahora",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (currentWorkingRecord.isSigned) {
                                    val sigThumbBmp = remember(currentWorkingRecord.signaturePath) {
                                        val p = currentWorkingRecord.signaturePath.removePrefix("file://")
                                        try {
                                            com.alfas.amortizacioncredito.domain.security.SignatureSecurityVault.loadSignatureBitmap(p)?.asImageBitmap()
                                        } catch (e: Exception) { null }
                                    }
                                    Surface(
                                        color = GreenBadgeBg,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = null,
                                                tint = GreenBadgeText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Comprobante Firmado (${currentWorkingRecord.signatureType.ifEmpty { "Digital" }})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = GreenBadgeText
                                                )
                                                val signedDateStr = if (currentWorkingRecord.signedAt > 0L) java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(currentWorkingRecord.signedAt)) else ""
                                                Text(
                                                    text = "Firmado por: ${currentWorkingRecord.signedBy}${if (signedDateStr.isNotBlank()) " - $signedDateStr" else ""}",
                                                    fontSize = 9.5.sp,
                                                    color = GreenBadgeText.copy(alpha = 0.85f)
                                                )
                                                if (currentWorkingRecord.signatureDetails.isNotBlank()) {
                                                    Text(
                                                        text = currentWorkingRecord.signatureDetails,
                                                        fontSize = 8.5.sp,
                                                        color = GreenBadgeText.copy(alpha = 0.7f),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            if (sigThumbBmp != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Image(
                                                    bitmap = sigThumbBmp,
                                                    contentDescription = "Firma",
                                                    modifier = Modifier
                                                        .height(34.dp)
                                                        .widthIn(max = 80.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.White)
                                                        .border(1.dp, GreenBadgeText.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .clickable { fullScreenImageToShow = currentWorkingRecord.signaturePath }
                                                        .padding(2.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        color = AmberBadgeBg,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WarningAmber,
                                                contentDescription = null,
                                                tint = AmberBadgeText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Sin firmar. Se requiere firma para validez legal y despacho por correo.",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AmberBadgeText
                                            )
                                        }
                                    }
                                }
                            }

                            // Contador de envíos por correo (Solo si SMTP está habilitado)
                            if (isSmtpEnabled && currentWorkingRecord.emailSentCount > 0) {
                                val lastSentStr = if (currentWorkingRecord.lastEmailSentAt > 0L) java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(currentWorkingRecord.lastEmailSentAt)) else ""
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MarkEmailRead,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Enviado por correo: ${currentWorkingRecord.emailSentCount} vez/veces${if (lastSentStr.isNotBlank()) " (Último: $lastSentStr)" else ""}",
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            // Comprobantes Originales Adjuntos (soporte multicomprobante)
                            val attachedImagePaths = remember(receiptImagePath) {
                                receiptImagePath.split("|").filter { it.isNotBlank() && File(it).exists() }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (attachedImagePaths.size > 1) "COMPROBANTES ORIGINALES ADJUNTOS (${attachedImagePaths.size})" else if (attachedImagePaths.isNotEmpty()) "COMPROBANTE ORIGINAL ADJUNTO" else "COMPROBANTES DE PAGO",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (onOpenFullEditor != null) {
                                    TextButton(
                                        onClick = {
                                            onSaveRecord?.invoke(currentWorkingRecord)
                                            onOpenFullEditor.invoke()
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (attachedImagePaths.isEmpty()) "Adjuntar foto" else "+ Añadir otro", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (attachedImagePaths.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    attachedImagePaths.forEachIndexed { idx, path ->
                                        val scannedBmp = remember(path) {
                                            try {
                                                BitmapFactory.decodeFile(path)?.asImageBitmap()
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        if (scannedBmp != null) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .clickable { fullScreenImageToShow = path }
                                                    .padding(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Image(
                                                    bitmap = scannedBmp,
                                                    contentDescription = "Comprobante #${idx + 1}",
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (attachedImagePaths.size > 1) "Comprobante #${idx + 1}" else "Captura / Recibo Bancario",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text("Toca para ampliar imagen", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(
                                                    onClick = { receiptToDeleteIndex = idx },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Eliminar comprobante",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Diálogo de confirmación para eliminar un comprobante adjunto
                            if (receiptToDeleteIndex != null && receiptToDeleteIndex!! in attachedImagePaths.indices) {
                                val deleteIdx = receiptToDeleteIndex!!
                                AlertDialog(
                                    onDismissRequest = { receiptToDeleteIndex = null },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    title = {
                                        Text("¿Eliminar Comprobante #${deleteIdx + 1}?")
                                    },
                                    text = {
                                        Text("¿Estás seguro de que deseas eliminar este comprobante adjunto del recibo? Esta acción quitará la imagen del registro de pago.")
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                val currentList = attachedImagePaths.toMutableList()
                                                if (deleteIdx in currentList.indices) {
                                                    currentList.removeAt(deleteIdx)
                                                    val newReceiptPath = currentList.joinToString("|")
                                                    receiptImagePath = newReceiptPath
                                                    val updatedRecord = currentWorkingRecord.copy(receiptImagePath = newReceiptPath)
                                                    onSaveRecord?.invoke(updatedRecord)
                                                    Toast.makeText(context, "Comprobante #${deleteIdx + 1} eliminado", Toast.LENGTH_SHORT).show()
                                                }
                                                receiptToDeleteIndex = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Eliminar")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { receiptToDeleteIndex = null }) {
                                            Text("Cancelar")
                                        }
                                    }
                                )
                            }

                            if (activeVoucherConfig.showFooterNote) {
                                val footer = if (activeVoucherConfig.customFooterText.isNotBlank()) activeVoucherConfig.customFooterText else "Documento digital generado por ${businessProfile.businessName.ifEmpty { "CreditHome" }}"
                                Text(
                                    text = footer,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SECCIÓN DESPLEGABLE: MODIFICAR DATOS EN TIEMPO REAL
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEditFields = !showEditFields },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showEditFields) "Ocultar Edición de Datos" else "Modificar Datos para el PDF en Tiempo Real",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = if (showEditFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = showEditFields) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (onOpenFullEditor != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSaveRecord?.invoke(currentWorkingRecord)
                                                    onOpenFullEditor.invoke()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddPhotoAlternate,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = "Añadir / Gestionar Comprobantes",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "Abre el registro completo para escanear nuevos recibos con OCR y recalcular el pago.",
                                                        fontSize = 9.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = amountInput,
                                    onValueChange = { amountInput = it },
                                    label = { Text("Monto Pagado ($)", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = referenceNumber,
                                    onValueChange = { referenceNumber = it },
                                    label = { Text("N° Referencia / Comprobante", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = bankOrEntity,
                                    onValueChange = { bankOrEntity = it },
                                    label = { Text("Banco / Entidad", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = payerName,
                                    onValueChange = { payerName = it },
                                    label = { Text("Titular / Depositante", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = paymentDate,
                                    onValueChange = { paymentDate = it },
                                    label = { Text("Fecha y Hora", fontSize = 11.sp) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            paymentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                        }) {
                                            Icon(Icons.Default.AccessTime, contentDescription = "Poner fecha y hora actual", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = noteInput,
                                    onValueChange = { noteInput = it },
                                    label = { Text("Observaciones", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BOTONES DE ACCIÓN (Diseño refinado, armónico y adaptado a temas Claro/Oscuro)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Exportar / Compartir PDF (Acción Primaria Principal)
                    Button(
                        onClick = {
                            onSaveRecord?.invoke(currentWorkingRecord)
                            val shareIntent = pdfUseCase.createShareIntent(
                                context = context,
                                record = currentWorkingRecord,
                                month = month,
                                totalMonths = totalMonths,
                                scheduledPayment = scheduledPayment,
                                interest = interest,
                                principal = principal,
                                previousBalance = previousBalance,
                                remainingBalance = remainingBalance,
                                clientName = clientName,
                                clientDocumentOrPhone = clientDocumentOrPhone,
                                businessProfile = businessProfile,
                                voucherConfig = activeVoucherConfig
                            )
                            if (shareIntent != null) {
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir Comprobante PDF"))
                            } else {
                                Toast.makeText(context, "Error al generar archivo PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar / Compartir en PDF", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    // 2. Guardar PDF en el dispositivo (Acción Secundaria con estilo Tonal / Borde Primario elegante)
                    Button(
                        onClick = {
                            savePdfLauncher.launch(defaultPdfFileName)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar PDF en Dispositivo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // 3. Compartir por WhatsApp (Tonalidad Verde Esmeralda Suave y Elegante)
                    Button(
                        onClick = {
                            onSaveRecord?.invoke(currentWorkingRecord)
                            shareReceipt(context, shareableReceiptText)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CreditHomeEmeraldDark,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir Texto / WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // 4. Enviar por Correo (Solo visible si SMTP está activo en Feature Flags)
                    if (isSmtpEnabled) {
                        OutlinedButton(
                            onClick = {
                                if (!currentWorkingRecord.isSigned) {
                                    Toast.makeText(
                                        context,
                                        "⚠️ El comprobante debe estar firmado antes de enviarlo por correo.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    showSignatureDialog = true
                                } else {
                                    onSaveRecord?.invoke(currentWorkingRecord)
                                    onSendEmail?.invoke(currentWorkingRecord)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentWorkingRecord.isSigned) "Enviar Comprobante por Correo" else "Firmar y Enviar por Correo",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    TextButton(
                        onClick = {
                            onSaveRecord?.invoke(currentWorkingRecord)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Text("Cerrar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


private fun shareReceipt(context: Context, text: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Compartir Comprobante de Pago")
    context.startActivity(shareIntent)
}

private val currencyFormatter = ThreadLocal.withInitial {
    DecimalFormat("$#,##0.00", DecimalFormatSymbols(Locale.US))
}

private fun formatCurrency(amount: Double): String {
    return currencyFormatter.get()?.format(amount) ?: String.format(Locale.US, "$%,.2f", amount)
}
