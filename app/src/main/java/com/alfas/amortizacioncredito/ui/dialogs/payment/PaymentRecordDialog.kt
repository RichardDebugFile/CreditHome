package com.alfas.amortizacioncredito.ui.dialogs.payment

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.ui.theme.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.domain.usecase.PaymentReceiptOcrUseCase
import com.alfas.amortizacioncredito.ui.components.ZoomableImageViewerDialog
import com.alfas.amortizacioncredito.ui.dialogs.common.ImageCropDialog
import com.alfas.amortizacioncredito.ui.dialogs.common.ImageSourceSelectorSheet
import com.alfas.amortizacioncredito.util.ValidationUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.alfas.amortizacioncredito.data.model.VoucherConfig
import com.alfas.amortizacioncredito.ui.dialogs.business.SignatureCaptureDialog

data class ScannedReceiptItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val imagePath: String,
    val detectedAmount: Double? = null,
    val reference: String? = null,
    val bank: String? = null,
    val method: String? = null,
    val date: String? = null,
    val payer: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentRecordDialog(
    month: Int,
    totalMonths: Int = 1,
    expectedAmount: Double,
    itemInterest: Double = 0.0,
    itemPrincipal: Double = 0.0,
    previousBalance: Double = 0.0,
    itemRemainingBalance: Double = 0.0,
    scheduledDueDate: String = "",
    currentRecord: PaymentRecord?,
    clientName: String?,
    clientDocumentOrPhone: String? = null,
    businessProfile: BusinessProfile = BusinessProfile(),
    voucherConfig: com.alfas.amortizacioncredito.data.model.VoucherConfig = com.alfas.amortizacioncredito.data.model.VoucherConfig(),
    onEditBusinessProfile: () -> Unit = {},
    onSaveVoucherConfig: ((com.alfas.amortizacioncredito.data.model.VoucherConfig) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (record: PaymentRecord) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ocrUseCase = remember { PaymentReceiptOcrUseCase() }

    val currentDateTimeFormatted = remember {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date())
    }

    val totalPayoffAmount = remember(itemInterest, itemPrincipal, itemRemainingBalance) {
        itemPrincipal + itemRemainingBalance + itemInterest
    }

    val initialIsPaid = remember { currentRecord?.isPaid ?: true }
    val initialDueDate = remember { currentRecord?.dueDate?.ifEmpty { scheduledDueDate } ?: scheduledDueDate }
    val initialPaymentDate = remember { currentRecord?.paymentDate?.ifEmpty { currentDateTimeFormatted } ?: currentDateTimeFormatted }
    val initialAmountStr = remember {
        val initial = if (currentRecord != null && currentRecord.amountPaid > 0) currentRecord.amountPaid else expectedAmount
        String.format(Locale.US, "%.2f", initial)
    }
    val initialMethod = remember { currentRecord?.paymentMethod?.ifEmpty { "Transferencia" } ?: "Transferencia" }
    val initialRef = remember { currentRecord?.referenceNumber ?: "" }
    val initialBank = remember { currentRecord?.bankOrEntity ?: "" }
    val initialPayer = remember { currentRecord?.payerName ?: "" }
    val initialReceiptImage = remember { currentRecord?.receiptImagePath ?: "" }
    val initialNote = remember { currentRecord?.note ?: "" }
    val initialSigPath = remember { currentRecord?.signaturePath ?: "" }
    val initialSigType = remember { currentRecord?.signatureType ?: "" }
    val initialSignedBy = remember { currentRecord?.signedBy ?: "" }
    val initialSignedAt = remember { currentRecord?.signedAt ?: 0L }
    val initialSigDetails = remember { currentRecord?.signatureDetails ?: "" }

    var isPaid by remember { mutableStateOf(initialIsPaid) }
    var dueDateInput by remember { mutableStateOf(initialDueDate) }
    var paymentDate by remember { mutableStateOf(initialPaymentDate) }
    var amountInput by remember { mutableStateOf(initialAmountStr) }
    var paymentMethod by remember { mutableStateOf(initialMethod) }
    var referenceNumber by remember { mutableStateOf(initialRef) }
    var bankOrEntity by remember { mutableStateOf(initialBank) }
    var payerName by remember { mutableStateOf(initialPayer) }
    var noteInput by remember { mutableStateOf(initialNote) }

    // Firma digital / física
    var signaturePath by remember { mutableStateOf(initialSigPath) }
    var signatureType by remember { mutableStateOf(initialSigType) }
    var signedBy by remember { mutableStateOf(initialSignedBy) }
    var signedAt by remember { mutableLongStateOf(initialSignedAt) }
    var signatureDetails by remember { mutableStateOf(initialSigDetails) }
    var showSignatureDialog by remember { mutableStateOf(false) }

    // Lista de comprobantes escaneados / adjuntos (soporte multicomprobante)
    val scannedReceipts = remember {
        mutableStateListOf<ScannedReceiptItem>().apply {
            if (initialReceiptImage.isNotBlank()) {
                val paths = initialReceiptImage.split("|").filter { it.isNotBlank() }
                paths.forEach { path ->
                    add(
                        ScannedReceiptItem(
                            imagePath = path,
                            detectedAmount = if (paths.size == 1 && currentRecord != null && currentRecord.amountPaid > 0) currentRecord.amountPaid else null,
                            reference = initialRef,
                            bank = initialBank,
                            method = initialMethod,
                            payer = initialPayer
                        )
                    )
                }
            }
        }
    }

    val receiptImagePath by remember {
        derivedStateOf { scannedReceipts.joinToString("|") { it.imagePath } }
    }

    var isScanningOcr by remember { mutableStateOf(false) }
    var ocrStatusMessage by remember { mutableStateOf<String?>(null) }
    var lastOcrDetectedDate by remember { mutableStateOf<String?>(null) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }

    var showConfirmDiscard by remember { mutableStateOf(false) }
    var showReceiptPreview by remember { mutableStateOf(false) }

    val hasChanges = remember(
        isPaid, paymentDate, amountInput, paymentMethod, referenceNumber, bankOrEntity, payerName, receiptImagePath, noteInput,
        signaturePath, signatureType, signedBy, signedAt,
        initialIsPaid, initialPaymentDate, initialAmountStr, initialMethod, initialRef, initialBank, initialPayer, initialReceiptImage, initialNote,
        initialSigPath, initialSigType, initialSignedBy, initialSignedAt
    ) {
        isPaid != initialIsPaid ||
                amountInput.trim() != initialAmountStr.trim() ||
                paymentMethod != initialMethod ||
                referenceNumber.trim() != initialRef.trim() ||
                bankOrEntity.trim() != initialBank.trim() ||
                payerName.trim() != initialPayer.trim() ||
                receiptImagePath != initialReceiptImage ||
                noteInput.trim() != initialNote.trim() ||
                signaturePath != initialSigPath ||
                signatureType != initialSigType ||
                (isPaid && paymentDate.trim() != initialPaymentDate.trim())
    }

    val attemptDismiss = {
        if (hasChanges) {
            showConfirmDiscard = true
        } else {
            onDismiss()
        }
    }

    val parsedAmount = amountInput.toDoubleOrNull() ?: expectedAmount

    // Estados para selección de imagen y herramienta de recorte
    var showImageSourcePicker by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var editingReceiptIndex by remember { mutableStateOf<Int?>(null) }
    var editingReceiptAmountInput by remember { mutableStateOf("") }

    fun processOcrForUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        isScanningOcr = true
        ocrStatusMessage = if (uris.size > 1) "🔍 Procesando ${uris.size} comprobantes con OCR..." else null
        coroutineScope.launch {
            for (uri in uris) {
                val result = ocrUseCase.processReceiptImage(context, uri, month)
                val newItem = ScannedReceiptItem(
                    imagePath = result.savedImagePath,
                    detectedAmount = result.amount,
                    reference = result.referenceNumber,
                    bank = result.bankOrEntity,
                    method = result.paymentMethod,
                    date = result.date,
                    payer = result.payerName
                )
                scannedReceipts.add(newItem)

                // Sumar todos los comprobantes adjuntos
                val validAmounts = scannedReceipts.mapNotNull { r -> r.detectedAmount }
                val totalSum = validAmounts.sum()

                if (totalSum > 0) {
                    amountInput = String.format(Locale.US, "%.2f", totalSum)
                } else if (result.amount != null && result.amount > 0) {
                    amountInput = String.format(Locale.US, "%.2f", result.amount)
                }

                // Concatenar referencias
                val allRefs = scannedReceipts.mapNotNull { r -> r.reference?.takeIf { it.isNotBlank() } }.distinct()
                if (allRefs.isNotEmpty()) {
                    referenceNumber = allRefs.joinToString(", ")
                }

                // Concatenar bancos
                val allBanks = scannedReceipts.mapNotNull { r -> r.bank?.takeIf { it.isNotBlank() } }.distinct()
                if (allBanks.isNotEmpty()) {
                    bankOrEntity = allBanks.joinToString(" / ")
                }

                if (!result.paymentMethod.isNullOrBlank()) {
                    paymentMethod = result.paymentMethod
                }

                // Cuando se ingresan múltiples facturas / comprobantes, seleccionar la fecha más reciente o cercana a hoy
                val allDates = scannedReceipts.mapNotNull { r -> r.date?.takeIf { d -> d.isNotBlank() } }
                val bestDate = PaymentReceiptOcrUseCase.selectMostRecentOrClosestDate(allDates)
                if (bestDate != null) {
                    paymentDate = bestDate
                    lastOcrDetectedDate = bestDate
                } else if (!result.date.isNullOrBlank()) {
                    paymentDate = result.date
                    lastOcrDetectedDate = result.date
                }

                if (!result.payerName.isNullOrBlank() && payerName.isBlank()) {
                    payerName = result.payerName
                }
            }

            isPaid = true
            isScanningOcr = false
            val validAmounts = scannedReceipts.mapNotNull { r -> r.detectedAmount }
            val totalSum = validAmounts.sum()
            ocrStatusMessage = if (scannedReceipts.size > 1) {
                "✨ ${scannedReceipts.size} comprobantes agregados. Suma total: $${String.format(Locale.US, "%.2f", totalSum)}"
            } else {
                "✨ Comprobante escaneado: Datos completados automáticamente"
            }
        }
    }

    fun processOcrForUri(uri: Uri) = processOcrForUris(listOf(uri))

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                pendingCropUri = uris.first()
            } else {
                processOcrForUris(uris)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            pendingCropUri = tempCameraUri
        }
    }

    fun launchCamera() {
        try {
            val tempFile = File(context.cacheDir, "camera_receipt_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al abrir la cámara: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara para fotografiar el comprobante", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndLaunchCamera() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Selector de Cámara / Galería
    if (showImageSourcePicker) {
        ImageSourceSelectorSheet(
            title = "Adjuntar Comprobante",
            subtitle = "Selecciona cómo deseas cargar el recibo para lectura OCR",
            onSelectCamera = { checkAndLaunchCamera() },
            onSelectGallery = { galleryPickerLauncher.launch("image/*") },
            onDismiss = { showImageSourcePicker = false }
        )
    }

    // Modal interactivo de Recorte de Imagen
    if (pendingCropUri != null) {
        ImageCropDialog(
            sourceUri = pendingCropUri!!,
            title = "Recortar Comprobante",
            subtitle = "Encuadra el comprobante para mejorar la precisión del OCR",
            onCropSuccess = { croppedUri, _ ->
                val uriToProcess = croppedUri
                pendingCropUri = null
                processOcrForUri(uriToProcess)
            },
            onUseOriginal = {
                val uriToProcess = pendingCropUri!!
                pendingCropUri = null
                processOcrForUri(uriToProcess)
            },
            onDismiss = { pendingCropUri = null }
        )
    }

    // Modal de edición manual de monto de comprobante
    if (editingReceiptIndex != null && editingReceiptIndex!! in scannedReceipts.indices) {
        val targetIdx = editingReceiptIndex!!
        val currentReceipt = scannedReceipts[targetIdx]
        var tempAmountStr by remember(targetIdx) {
            mutableStateOf(currentReceipt.detectedAmount?.let { String.format(Locale.US, "%.2f", it) } ?: "")
        }

        AlertDialog(
            onDismissRequest = { editingReceiptIndex = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Monto de Comprobante #${targetIdx + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Si el OCR no reconoció la cantidad correcta de este comprobante, puedes cambiarla manualmente aquí:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempAmountStr,
                        onValueChange = { newVal ->
                            val filtered = newVal.replace(',', '.')
                            if (filtered.isEmpty() || filtered.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                                tempAmountStr = filtered
                            }
                        },
                        label = { Text("Monto del comprobante ($)") },
                        placeholder = { Text("Ej: 400.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedVal = tempAmountStr.toDoubleOrNull()
                        scannedReceipts[targetIdx] = currentReceipt.copy(detectedAmount = parsedVal)
                        // Recalcular la suma total
                        val validAmounts = scannedReceipts.mapNotNull { it.detectedAmount }
                        val newTotal = validAmounts.sum()
                        if (newTotal > 0) {
                            amountInput = String.format(Locale.US, "%.2f", newTotal)
                        } else if (parsedVal != null && parsedVal > 0) {
                            amountInput = String.format(Locale.US, "%.2f", parsedVal)
                        }
                        editingReceiptIndex = null
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingReceiptIndex = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal de imagen de comprobante a pantalla completa con soporte de ZOOM interactivo y barra de herramientas
    if (fullScreenImagePath != null) {
        ZoomableImageViewerDialog(
            imagePath = fullScreenImagePath,
            title = "Vista de Comprobante",
            onDismiss = { fullScreenImagePath = null }
        )
    }

    // Preview del Voucher / Recibo Digital
    if (showReceiptPreview) {
        val tempRecord = PaymentRecord(
            month = month,
            isPaid = isPaid,
            paymentDate = paymentDate,
            amountPaid = parsedAmount,
            paymentMethod = paymentMethod,
            referenceNumber = referenceNumber.trim(),
            bankOrEntity = bankOrEntity.trim(),
            payerName = payerName.trim(),
            receiptImagePath = receiptImagePath,
            note = noteInput.trim(),
            signaturePath = signaturePath,
            signatureType = signatureType,
            signedBy = signedBy,
            signedAt = signedAt,
            signatureDetails = signatureDetails,
            emailSentCount = currentRecord?.emailSentCount ?: 0,
            lastEmailSentAt = currentRecord?.lastEmailSentAt ?: 0L
        )
        PaymentReceiptDialog(
            record = tempRecord,
            month = month,
            totalMonths = totalMonths,
            scheduledPayment = expectedAmount,
            interest = itemInterest,
            principal = itemPrincipal,
            previousBalance = previousBalance,
            remainingBalance = itemRemainingBalance,
            clientName = clientName,
            clientDocumentOrPhone = clientDocumentOrPhone,
            businessProfile = businessProfile,
            voucherConfig = voucherConfig,
            onEditBusinessProfile = onEditBusinessProfile,
            onSaveVoucherConfig = onSaveVoucherConfig,
            onSaveRecord = { updatedRec ->
                signaturePath = updatedRec.signaturePath
                signatureType = updatedRec.signatureType
                signedBy = updatedRec.signedBy
                signedAt = updatedRec.signedAt
                signatureDetails = updatedRec.signatureDetails
                amountInput = String.format(Locale.US, "%.2f", updatedRec.amountPaid)
                paymentMethod = updatedRec.paymentMethod
                referenceNumber = updatedRec.referenceNumber
                bankOrEntity = updatedRec.bankOrEntity
                payerName = updatedRec.payerName
                paymentDate = updatedRec.paymentDate
                noteInput = updatedRec.note
            },
            onDismiss = { showReceiptPreview = false }
        )
    }

    if (showConfirmDiscard) {
        AlertDialog(
            onDismissRequest = { showConfirmDiscard = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Has modificado información del comprobante de este pago. Si sales ahora, se perderán las modificaciones.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDiscard = false
                        onDismiss()
                    }
                ) {
                    Text("Descartar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDiscard = false }) {
                    Text("Continuar Editando")
                }
            }
        )
    }

    val paymentMethodsList = listOf("Transferencia", "Depósito Bancario", "Efectivo", "Tarjeta", "Cheque")
    val bankSuggestions = listOf("Banco Pichincha", "Banco Guayaquil", "Banco del Pacífico", "Produbanco", "Banco Internacional", "Banco Bolivariano")

    AlertDialog(
        onDismissRequest = attemptDismiss,
        icon = {
            Icon(
                imageVector = if (isPaid) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (isPaid) GreenSuccess else AmberWarning,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Control de Pago & Comprobante",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Cuota Mes $month de $totalMonths",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!clientName.isNullOrEmpty()) {
                    Text(
                        text = "Cliente: $clientName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Selector de Estado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "¿Cuota Pagada?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = isPaid,
                        onCheckedChange = { isPaid = it }
                    )
                }

                HorizontalDivider()

                if (isPaid) {
                    // SECCIÓN DE LECTURA OCR Y GESTIÓN MULTICOMPROBANTE
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Lectura Automática (OCR)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Sube 1 o varios comprobantes de pago",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { showImageSourcePicker = true },
                                    enabled = !isScanningOcr,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    if (isScanningOcr) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Leyendo...", fontSize = 11.sp)
                                    } else {
                                        Icon(
                                            if (scannedReceipts.isEmpty()) Icons.Default.DocumentScanner else Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (scannedReceipts.isEmpty()) "Escanear" else "+ Añadir",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Mensaje de estado OCR si aplica
                            ocrStatusMessage?.let { msg ->
                                Surface(
                                    color = GreenBadgeBg,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = msg,
                                        color = GreenBadgeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }

                            // Lista de comprobantes escaneados con opción de eliminar individual
                            if (scannedReceipts.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    scannedReceipts.forEachIndexed { index, item ->
                                        val thumbBmp = remember(item.imagePath) {
                                            try {
                                                if (File(item.imagePath).exists()) {
                                                    BitmapFactory.decodeFile(item.imagePath)?.asImageBitmap()
                                                } else null
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        fullScreenImagePath = item.imagePath
                                                    }
                                            ) {
                                                if (thumbBmp != null) {
                                                    Image(
                                                        bitmap = thumbBmp,
                                                        contentDescription = "Comprobante ${index + 1}",
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(6.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "Comprobante #${index + 1}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = if (item.detectedAmount != null && item.detectedAmount > 0)
                                                                GreenBadgeBg
                                                            else
                                                                MaterialTheme.colorScheme.surfaceVariant,
                                                            shape = RoundedCornerShape(4.dp),
                                                            border = BorderStroke(
                                                                0.5.dp,
                                                                if (item.detectedAmount != null && item.detectedAmount > 0)
                                                                    GreenBadgeBorder
                                                                else
                                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                            ),
                                                            modifier = Modifier.clickable {
                                                                editingReceiptIndex = index
                                                                editingReceiptAmountInput = item.detectedAmount?.let {
                                                                    String.format(Locale.US, "%.2f", it)
                                                                } ?: ""
                                                            }
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (item.detectedAmount != null && item.detectedAmount > 0)
                                                                        formatCurrency(item.detectedAmount)
                                                                    else
                                                                        "Sin monto (Editar)",
                                                                    color = if (item.detectedAmount != null && item.detectedAmount > 0)
                                                                        GreenBadgeText
                                                                    else
                                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Icon(
                                                                    imageVector = Icons.Default.Edit,
                                                                    contentDescription = "Editar monto",
                                                                    tint = if (item.detectedAmount != null && item.detectedAmount > 0)
                                                                        GreenBadgeText
                                                                    else
                                                                        MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    val infoSub = listOfNotNull(
                                                        item.bank?.takeIf { it.isNotBlank() },
                                                        item.date?.takeIf { it.isNotBlank() }?.let { "Fecha: $it" },
                                                        item.reference?.takeIf { it.isNotBlank() }?.let { "Ref: $it" }
                                                    ).joinToString(" • ")

                                                    Text(
                                                        text = if (infoSub.isNotBlank()) infoSub else "Toca para ver en grande",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // Botón de eliminar este comprobante
                                            IconButton(
                                                onClick = {
                                                    scannedReceipts.removeAt(index)
                                                    val remainingAmounts = scannedReceipts.mapNotNull { it.detectedAmount }
                                                    val newTotal = remainingAmounts.sum()
                                                    if (newTotal > 0) {
                                                        amountInput = String.format(Locale.US, "%.2f", newTotal)
                                                    } else if (scannedReceipts.isEmpty()) {
                                                        amountInput = String.format(Locale.US, "%.2f", expectedAmount)
                                                    }
                                                    val remainingRefs = scannedReceipts.mapNotNull { it.reference?.takeIf { r -> r.isNotBlank() } }.distinct()
                                                    referenceNumber = remainingRefs.joinToString(", ")
                                                    val remainingBanks = scannedReceipts.mapNotNull { it.bank?.takeIf { b -> b.isNotBlank() } }.distinct()
                                                    bankOrEntity = remainingBanks.joinToString(" / ")
                                                    val remainingDates = scannedReceipts.mapNotNull { it.date?.takeIf { d -> d.isNotBlank() } }
                                                    val bestRemainingDate = PaymentReceiptOcrUseCase.selectMostRecentOrClosestDate(remainingDates)
                                                    if (bestRemainingDate != null) {
                                                        paymentDate = bestRemainingDate
                                                        lastOcrDetectedDate = bestRemainingDate
                                                    } else if (remainingDates.isEmpty()) {
                                                        lastOcrDetectedDate = null
                                                    }
                                                    if (scannedReceipts.isEmpty()) {
                                                        ocrStatusMessage = null
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Eliminar comprobante",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Si hay más de un comprobante, mostrar tarjeta de SUMA TOTAL (Σ)
                                    if (scannedReceipts.size > 1) {
                                        val totalSum = scannedReceipts.mapNotNull { it.detectedAmount }.sum()
                                        Surface(
                                            color = BlueBadgeBg,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, BlueBadgeBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Σ Suma de ${scannedReceipts.size} comprobantes:",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = BlueInfoDark
                                                    )
                                                    val breakdown = scannedReceipts.mapNotNull { it.detectedAmount }.joinToString(" + ") { formatCurrency(it) }
                                                    Text(
                                                        text = "$breakdown = ${formatCurrency(totalSum)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BlueInfoDeep
                                                    )
                                                }

                                                if (totalSum > 0 && kotlin.math.abs((amountInput.toDoubleOrNull() ?: 0.0) - totalSum) >= 0.01) {
                                                    Button(
                                                        onClick = { amountInput = String.format(Locale.US, "%.2f", totalSum) },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(24.dp)
                                                    ) {
                                                        Text("Aplicar Suma", fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Selección rápida de montos
                    Text(
                        text = "Monto a Registrar:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { amountInput = String.format(Locale.US, "%.2f", expectedAmount) },
                            label = { Text("Cuota (${formatCurrency(expectedAmount)})", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )

                        if (totalPayoffAmount > expectedAmount) {
                            SuggestionChip(
                                onClick = { amountInput = String.format(Locale.US, "%.2f", totalPayoffAmount) },
                                label = { Text("Pagar Todo (${formatCurrency(totalPayoffAmount)})", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Campo editable de Monto Pagado
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = ValidationUtils.filterDecimalInput(it) },
                        label = { Text("Monto Pagado / Abonado ($)") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // BANNER INLINE DE CONTRASTE Y VERIFICACIÓN OCR / CUOTA
                    val totalOcrDetected = scannedReceipts.mapNotNull { it.detectedAmount }.sum()
                    val hasOcrDetected = scannedReceipts.any { it.detectedAmount != null && it.detectedAmount > 0 }

                    if (hasOcrDetected) {
                        val diffOcr = kotlin.math.abs(parsedAmount - totalOcrDetected)
                        val matchesOcr = diffOcr < 0.01

                        if (matchesOcr) {
                            Surface(
                                color = GreenBadgeBg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GreenBadgeBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = GreenSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "✅ Monto verificado con comprobante (${formatCurrency(totalOcrDetected)})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenBadgeText
                                        )
                                        if (kotlin.math.abs(parsedAmount - expectedAmount) >= 0.01) {
                                            val diffCuota = parsedAmount - expectedAmount
                                            Text(
                                                text = if (diffCuota > 0)
                                                    "Supera la cuota en +${formatCurrency(diffCuota)} (Abono extra a capital)"
                                                else
                                                    "Faltan ${formatCurrency(-diffCuota)} para cubrir la cuota prevista",
                                                fontSize = 9.5.sp,
                                                color = GreenSuccessDark
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Alerta de Discrepancia con botón para aplicar monto del comprobante
                            Surface(
                                color = AmberBadgeBg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AmberBadgeBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WarningAmber,
                                                contentDescription = null,
                                                tint = AmberWarningDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Discrepancia con el comprobante",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberBadgeText
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Comprobante: ${formatCurrency(totalOcrDetected)} | Ingresado: ${formatCurrency(parsedAmount)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AmberTextDark
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                amountInput = String.format(Locale.US, "%.2f", totalOcrDetected)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarningDark),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Aplicar ${formatCurrency(totalOcrDetected)}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Mensajes dinámicos de saldo
                    if (parsedAmount >= totalPayoffAmount && totalPayoffAmount > 0) {
                        Surface(
                            color = GreenBadgeBg,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🌟 ¡Liquidación Total! La deuda quedará pagada al 100% en este mes.",
                                color = GreenBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else if (parsedAmount > expectedAmount && !hasOcrDetected) {
                        val extraToCapital = parsedAmount - expectedAmount
                        Surface(
                            color = BlueBadgeBg,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🚀 Abono extraordinario: Se aplicarán ${formatCurrency(extraToCapital)} extra a capital.",
                                color = CreditHomeBlueDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else if (parsedAmount < expectedAmount && parsedAmount > 0 && !hasOcrDetected) {
                        Surface(
                            color = AmberBadgeBg,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Pago parcial: Cubre parte de la cuota. El saldo restante se recalculará.",
                                color = AmberBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // SECCIÓN DATOS DEL COMPROBANTE BANCARIO
                    Text(
                        text = "Datos del Comprobante Bancario",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Método de Pago
                    Text(
                        text = "Medio de Pago:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        paymentMethodsList.forEach { method ->
                            FilterChip(
                                selected = paymentMethod.equals(method, ignoreCase = true),
                                onClick = { paymentMethod = method },
                                label = { Text(method, fontSize = 10.sp) }
                            )
                        }
                    }

                    // N° de Comprobante / Referencia
                    OutlinedTextField(
                        value = referenceNumber,
                        onValueChange = { referenceNumber = it },
                        label = { Text("N° Comprobante / Referencia") },
                        placeholder = { Text("Ej: REF-94821034 / #84920") },
                        leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Banco / Entidad
                    if (paymentMethod != "Efectivo") {
                        OutlinedTextField(
                            value = bankOrEntity,
                            onValueChange = { bankOrEntity = it },
                            label = { Text("Banco / Entidad Financiera") },
                            placeholder = { Text("Ej: Banco Pichincha") },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Sugerencias rápidas de bancos
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            bankSuggestions.forEach { bank ->
                                SuggestionChip(
                                    onClick = { bankOrEntity = bank },
                                    label = { Text(bank.removePrefix("Banco "), fontSize = 9.sp) }
                                )
                            }
                        }
                    }

                    // Titular o Depositante (Opcional)
                    OutlinedTextField(
                        value = payerName,
                        onValueChange = { payerName = it },
                        label = { Text("Titular / Depositante (Opcional)") },
                        placeholder = { Text("Por si pagó un tercero") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Fecha Límite / Programada de Pago
                    OutlinedTextField(
                        value = dueDateInput,
                        onValueChange = { dueDateInput = it },
                        label = { Text("Fecha Límite Programada (Cuándo debe pagar)") },
                        placeholder = { Text("dd/MM/yyyy") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val cal = Calendar.getInstance()
                                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                try {
                                    val d = sdf.parse(dueDateInput)
                                    if (d != null) cal.time = d
                                } catch (_: Exception) {}

                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        dueDateInput = String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha límite", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Fecha y hora de Pago Real
                    OutlinedTextField(
                        value = paymentDate,
                        onValueChange = { paymentDate = it },
                        label = { Text("Fecha y Hora Real de Pago (Cuándo pagó)") },
                        placeholder = { Text("dd/MM/yyyy HH:mm") },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val cal = Calendar.getInstance()
                                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                try {
                                    val d = sdf.parse(paymentDate.substringBefore(" "))
                                    if (d != null) cal.time = d
                                } catch (_: Exception) {}

                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val timePart = if (paymentDate.contains(" ")) " " + paymentDate.substringAfter(" ") else " 12:00"
                                        paymentDate = String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y) + timePart
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) {
                                Icon(Icons.Default.Today, contentDescription = "Elegir fecha de pago", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Acciones rápidas para la fecha y hora de pago
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Botón "Ahora (Hoy)"
                        AssistChip(
                            onClick = {
                                val nowFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                paymentDate = nowFormatted
                            },
                            label = { Text("⚡ Ahora (Hoy)", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                labelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.height(30.dp)
                        )

                        // 2. Botón "Fecha OCR" (si hay fecha detectada por OCR)
                        val ocrDateCandidate = lastOcrDetectedDate ?: scannedReceipts.mapNotNull { it.date?.takeIf { d -> d.isNotBlank() } }.firstOrNull()
                        if (!ocrDateCandidate.isNullOrBlank()) {
                            AssistChip(
                                onClick = {
                                    paymentDate = ocrDateCandidate
                                },
                                label = { Text("↺ Fecha OCR", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                                leadingIcon = {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.tertiary)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                    labelColor = MaterialTheme.colorScheme.tertiary
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f)
                                ),
                                modifier = Modifier.height(30.dp)
                            )
                        }

                        // 3. Botón "Fecha Programada"
                        if (dueDateInput.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    val nowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    paymentDate = "$dueDateInput $nowTime"
                                },
                                label = { Text("📅 Programada", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
                                ),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Control de Puntualidad (Compara Fecha Programada vs Fecha de Pago Real)
                    if (dueDateInput.isNotBlank() && paymentDate.isNotBlank()) {
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val dueD = try { sdf.parse(dueDateInput) } catch (_: Exception) { null }
                        val payD = try { sdf.parse(paymentDate.substringBefore(" ")) } catch (_: Exception) { null }

                        if (dueD != null && payD != null) {
                            val diffDays = ((payD.time - dueD.time) / (1000 * 60 * 60 * 24)).toInt()
                            val (bgColor, textColor, textMsg) = when {
                                diffDays < 0 -> Triple(GreenBadgeBg, GreenBadgeText, "🟢 Pago anticipado (${-diffDays} día(s) antes del vencimiento)")
                                diffDays == 0 -> Triple(GreenBadgeBg, GreenBadgeText, "🟢 Pago puntual en la fecha pactada")
                                else -> Triple(AmberBadgeBg, AmberBadgeText, "🟠 Pago registrado con $diffDays día(s) posteriores a la fecha programada")
                            }

                            Surface(
                                color = bgColor,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (diffDays <= 0) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(textMsg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                                }
                            }
                        }
                    }

                    // SECCIÓN DE FIRMA DIGITAL / FÍSICA (Diseño adaptado para Light & Dark Theme)
                    if (voucherConfig.showSignature) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = if (signaturePath.isNotBlank()) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (signaturePath.isNotBlank()) Icons.Default.VerifiedUser else Icons.Default.Draw,
                                        contentDescription = null,
                                        tint = if (signaturePath.isNotBlank()) CreditHomeEmerald else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (signaturePath.isNotBlank()) "Firmado Digitalmente ($signatureType)" else "Sin Firma Digital",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (signaturePath.isNotBlank()) "Por: $signedBy" else "Opcional para despacho y validez",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (signaturePath.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                signaturePath = ""
                                                signatureType = ""
                                                signedBy = ""
                                                signedAt = 0L
                                                signatureDetails = ""
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Quitar Firma",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    FilledTonalButton(
                                        onClick = { showSignatureDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = if (signaturePath.isNotBlank()) "Cambiar" else "Firmar",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Botón para previsualizar voucher antes de guardar
                    OutlinedButton(
                        onClick = { showReceiptPreview = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vista Previa del Voucher / Recibo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(2.dp))

                // Campo de Observaciones / Eventualidad (Disponible siempre)
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text(if (isPaid) "Observaciones Adicionales" else "Observaciones / Eventualidad") },
                    placeholder = {
                        Text(
                            if (isPaid) "Ej: Transferencia enviada por WhatsApp"
                            else "Ej: Cliente solicitó prórroga para el 15"
                        )
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )

                if (!isPaid) {
                    Text(
                        text = "Esta cuota se registrará como PENDIENTE de pago.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        PaymentRecord(
                            month = month,
                            isPaid = isPaid,
                            paymentDate = if (isPaid) paymentDate else "",
                            dueDate = dueDateInput.trim(),
                            amountPaid = if (isPaid) parsedAmount else 0.0,
                            paymentMethod = if (isPaid) paymentMethod else "Transferencia",
                            referenceNumber = if (isPaid) ValidationUtils.sanitizeText(referenceNumber) else "",
                            bankOrEntity = if (isPaid) ValidationUtils.sanitizeText(bankOrEntity) else "",
                            payerName = if (isPaid) ValidationUtils.sanitizeText(payerName) else "",
                            receiptImagePath = if (isPaid) receiptImagePath else "",
                            note = ValidationUtils.sanitizeText(noteInput),
                            signaturePath = if (isPaid) signaturePath else "",
                            signatureType = if (isPaid) signatureType else "",
                            signedBy = if (isPaid) ValidationUtils.sanitizeText(signedBy) else "",
                            signedAt = if (isPaid) signedAt else 0L,
                            signatureDetails = if (isPaid) ValidationUtils.sanitizeText(signatureDetails) else "",
                            emailSentCount = currentRecord?.emailSentCount ?: 0,
                            lastEmailSentAt = currentRecord?.lastEmailSentAt ?: 0L
                        )
                    )
                }
            ) {
                Text("Guardar Comprobante")
            }
        },
        dismissButton = {
            TextButton(onClick = attemptDismiss) {
                Text("Cancelar")
            }
        }
    )

    if (showSignatureDialog) {
        SignatureCaptureDialog(
            signerName = signedBy.ifBlank { businessProfile.businessName.ifBlank { "Administrador" } },
            voucherInfo = "Cuota #$month",
            onSignatureSuccess = { path, type, signer, _, details ->
                signaturePath = path
                signatureType = type
                signedBy = signer
                signedAt = System.currentTimeMillis()
                signatureDetails = details
                showSignatureDialog = false
            },
            onDismiss = { showSignatureDialog = false }
        )
    }
}

private val currencyFormatter = ThreadLocal.withInitial {
    DecimalFormat("$#,##0.00", DecimalFormatSymbols(Locale.US))
}

private fun formatCurrency(amount: Double): String {
    return currencyFormatter.get()?.format(amount) ?: String.format(Locale.US, "$%,.2f", amount)
}
