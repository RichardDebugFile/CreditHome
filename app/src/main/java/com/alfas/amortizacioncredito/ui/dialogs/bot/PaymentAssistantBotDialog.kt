package com.alfas.amortizacioncredito.ui.dialogs.bot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.alfas.amortizacioncredito.data.model.AuditActionType
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.data.model.SavedSignatureItem
import com.alfas.amortizacioncredito.data.model.bot.*
import com.alfas.amortizacioncredito.data.repository.AuditRepository
import com.alfas.amortizacioncredito.data.repository.ClientRepository
import com.alfas.amortizacioncredito.data.repository.SavedSignatureRepository
import com.alfas.amortizacioncredito.domain.bot.PaymentIntentParser
import com.alfas.amortizacioncredito.domain.bot.PaymentScheduleHelper
import com.alfas.amortizacioncredito.domain.usecase.ExportPaymentReceiptPdfUseCase
import com.alfas.amortizacioncredito.domain.usecase.NotificationManagerUseCase
import com.alfas.amortizacioncredito.domain.usecase.PaymentReceiptOcrUseCase
import com.alfas.amortizacioncredito.ui.dialogs.business.DigitalSignaturePad
import com.alfas.amortizacioncredito.ui.dialogs.business.SignatureCaptureDialog
import com.alfas.amortizacioncredito.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentAssistantBotDialog(
    initialClient: ClientProfile? = null,
    onPaymentRegistered: (ClientProfile) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val clientRepo = remember { ClientRepository(context) }
    val signatureRepo = remember { SavedSignatureRepository(context) }
    val scheduleHelper = remember { PaymentScheduleHelper() }
    val intentParser = remember { PaymentIntentParser() }
    val exportPdfUseCase = remember { ExportPaymentReceiptPdfUseCase() }
    val ocrUseCase = remember { PaymentReceiptOcrUseCase() }
    val notificationManager = remember { NotificationManagerUseCase(context) }
    val auditRepo = remember { AuditRepository(context) }

    val allClients by remember { mutableStateOf(clientRepo.getAllClients()) }

    var inputText by remember { mutableStateOf("") }
    var session by remember { mutableStateOf(PaymentBotSession(client = initialClient)) }
    val messages = remember { mutableStateListOf<PaymentBotMessage>() }

    fun sendMessage() {
        if (inputText.isNotBlank()) {
            val textToSend = inputText.trim()
            inputText = ""
            handleUserUtterance(
                input = textToSend,
                allClients = allClients,
                session = session,
                onSessionUpdate = { session = it },
                messages = messages,
                scheduleHelper = scheduleHelper,
                intentParser = intentParser,
                signatureRepo = signatureRepo
            )
        }
    }

    // Diálogos secundarios
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showSignaturePadDialog by remember { mutableStateOf(false) }
    var showSignatureVaultDialog by remember { mutableStateOf(false) }

    // Variables temporales para fotos de váucher
    var tempCameraVoucherUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraVoucherFile by remember { mutableStateOf<File?>(null) }

    // Función que procesa uno o más comprobantes con IA OCR (soporte de subida múltiple o consecutiva)
    fun processVouchers(uris: List<Uri>, sourceLabel: String) {
        if (uris.isEmpty()) return
        coroutineScope.launch {
            clearPreviousChips(messages)
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.USER,
                    text = sourceLabel,
                    thumbnailPath = uris.joinToString("|") { it.toString() }
                )
            )
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = if (uris.size > 1) "🔍 Escaneando ${uris.size} comprobantes con inteligencia artificial OCR..." else "🔍 Escaneando comprobante con inteligencia artificial OCR...",
                )
            )

            // Procesar cada comprobante con OCR
            val ocrResults = uris.map { uri ->
                val res = ocrUseCase.processReceiptImage(
                    context = context,
                    imageUri = uri,
                    month = session.selectedMonth ?: 1
                )
                val finalPath = res.savedImagePath.ifEmpty {
                    if (uri == tempCameraVoucherUri) tempCameraVoucherFile?.absolutePath ?: uri.toString() else uri.toString()
                }
                res.copy(savedImagePath = finalPath)
            }

            // Remover el mensaje temporal de "Escaneando..."
            if (messages.isNotEmpty() && messages.last().text.contains("Escaneando comprobante")) {
                messages.removeAt(messages.size - 1)
            }

            // Acumular comprobantes existentes con los nuevos (soporte consecutivo o múltiple)
            val newSavedPaths = ocrResults.map { it.savedImagePath }.filter { it.isNotBlank() }
            val existingPaths = session.voucherPath.split("|").filter { it.isNotBlank() }
            val allPaths = (existingPaths + newSavedPaths).distinct()
            val combinedVoucherPath = allPaths.joinToString("|")

            val newRefs = ocrResults.mapNotNull { it.referenceNumber?.takeIf { r -> r.isNotBlank() } }
            val existingRefs = session.referenceNumber.split(", ").filter { it.isNotBlank() }
            val combinedRefs = (existingRefs + newRefs).distinct().joinToString(", ")

            val newBanks = ocrResults.mapNotNull { it.bankOrEntity?.takeIf { b -> b.isNotBlank() } }
            val existingBanks = session.bankOrEntity.split(" / ").filter { it.isNotBlank() }
            val combinedBanks = (existingBanks + newBanks).distinct().joinToString(" / ")

            val newAmounts = ocrResults.mapNotNull { it.amount?.takeIf { a -> a > 0 } }
            val allDetectedAmounts = session.detectedAmounts + newAmounts
            val scheduledAmount = session.client?.let { cl ->
                scheduleHelper.getAmortizationSchedule(cl).find { it.month == session.selectedMonth }?.payment
            } ?: session.installmentAmount
            val totalDetectedAmount = if (allDetectedAmounts.isNotEmpty()) allDetectedAmounts.sum() else null

            session = session.copy(
                voucherPath = combinedVoucherPath,
                referenceNumber = combinedRefs,
                bankOrEntity = combinedBanks,
                detectedAmounts = allDetectedAmounts
            )

            val totalCount = allPaths.size

            if (totalDetectedAmount != null && totalDetectedAmount > 0.0) {
                if (abs(totalDetectedAmount - scheduledAmount) > 0.01) {
                    val amountDetails = if (allDetectedAmounts.size > 1) " (Suma: ${allDetectedAmounts.joinToString(" + ") { formatCurrency(it) }})" else ""
                    messages.add(
                        PaymentBotMessage(
                            sender = MessageSender.BOT,
                            text = "📷 **${if (totalCount > 1) "$totalCount comprobantes procesados" else "Comprobante procesado"} con éxito.**\n\n• Monto detectado: **${formatCurrency(totalDetectedAmount)}**$amountDetails\n• Cuota programada del sistema: **${formatCurrency(scheduledAmount)}**${if (combinedRefs.isNotBlank()) "\n• Ref: **$combinedRefs**" else ""}${if (combinedBanks.isNotBlank()) "\n• Banco: **$combinedBanks**" else ""}\n\n¿Deseas aplicar el monto detectado (${formatCurrency(totalDetectedAmount)}), conservar la cuota programada (${formatCurrency(scheduledAmount)}), **modificarlo manualmente**, o **adjuntar otro comprobante**?",
                            actionChips = listOf(
                                BotActionChip(
                                    id = "APPLY_OCR_AMOUNT",
                                    label = "✅ Aplicar ${formatCurrency(totalDetectedAmount)}",
                                    payload = totalDetectedAmount.toString(),
                                    isPrimary = true
                                ),
                                BotActionChip(
                                    id = "KEEP_SCHEDULED_AMOUNT",
                                    label = "Conservar ${formatCurrency(scheduledAmount)}"
                                ),
                                BotActionChip(
                                    id = "CUSTOM_AMOUNT_PROMPT",
                                    label = "✏️ Cambiar monto"
                                ),
                                BotActionChip(
                                    id = "ADD_MORE_VOUCHERS",
                                    label = "➕ Adjuntar otro comprobante"
                                )
                            )
                        )
                    )
                    return@launch
                } else {
                    // Monto idéntico / coincide exactamente con la cuota
                    messages.add(
                        PaymentBotMessage(
                            sender = MessageSender.BOT,
                            isSuccess = true,
                            text = "📷 **${if (totalCount > 1) "$totalCount comprobantes verificados" else "Comprobante verificado"} con éxito.**\n• Monto confirmado: **${formatCurrency(totalDetectedAmount)}**${if (combinedRefs.isNotBlank()) "\n• Ref: **$combinedRefs**" else ""}${if (combinedBanks.isNotBlank()) "\n• Banco: **$combinedBanks**" else ""}",
                            actionChips = listOf(
                                BotActionChip(
                                    id = "PROCEED_AFTER_VOUCHER",
                                    label = "✍️ Continuar a la firma",
                                    isPrimary = true
                                ),
                                BotActionChip(
                                    id = "CUSTOM_AMOUNT_PROMPT",
                                    label = "✏️ Cambiar monto"
                                ),
                                BotActionChip(
                                    id = "ADD_MORE_VOUCHERS",
                                    label = "➕ Adjuntar otro comprobante"
                                )
                            )
                        )
                    )
                    return@launch
                }
            } else {
                // OCR no encontró monto numérico con certeza
                messages.add(
                    PaymentBotMessage(
                        sender = MessageSender.BOT,
                        text = "📷 **${if (totalCount > 1) "$totalCount comprobantes adjuntados" else "Comprobante adjuntado"}.**${if (combinedBanks.isNotBlank()) "\n• Banco: **$combinedBanks**" else ""}${if (combinedRefs.isNotBlank()) "\n• Ref: **$combinedRefs**" else ""}\n\nNo se detectó un monto visible con certeza en la imagen.\n¿Deseas mantener la cuota programada de **${formatCurrency(scheduledAmount)}**, modificar el monto, o adjuntar otro comprobante?",
                        actionChips = listOf(
                            BotActionChip(
                                id = "KEEP_SCHEDULED_AMOUNT",
                                label = "✅ Mantener ${formatCurrency(scheduledAmount)}",
                                isPrimary = true
                            ),
                            BotActionChip(
                                id = "ADD_MORE_VOUCHERS",
                                label = "➕ Adjuntar otro comprobante"
                            ),
                            BotActionChip(
                                id = "CUSTOM_AMOUNT_PROMPT",
                                label = "✏️ Modificar monto"
                            )
                        )
                    )
                )
                return@launch
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraVoucherUri != null) {
            processVouchers(listOf(tempCameraVoucherUri!!), "📷 Comprobante tomado con la cámara.")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val label = if (uris.size == 1) "🖼️ Comprobante seleccionado de la galería." else "🖼️ ${uris.size} comprobantes seleccionados de la galería."
            processVouchers(uris, label)
        }
    }

    // Launcher de reconocimiento de voz nativo de Android
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                handleUserUtterance(
                    input = spoken,
                    allClients = allClients,
                    session = session,
                    onSessionUpdate = { session = it },
                    messages = messages,
                    scheduleHelper = scheduleHelper,
                    intentParser = intentParser,
                    signatureRepo = signatureRepo
                )
            }
        }
    }

    fun launchVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "EC"))
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora... ej. 'Se acaba de pagar la cuota de Gabriel'")
            }
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Reconocimiento de voz no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraForVoucher() {
        try {
            val file = File(context.cacheDir, "voucher_cam_${System.currentTimeMillis()}.jpg")
            tempCameraVoucherFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraVoucherUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    // Inicializar chat con bienvenida y sugerencias
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            if (initialClient != null) {
                val nextPending = scheduleHelper.getNextPendingInstallment(initialClient)
                if (nextPending != null) {
                    session = session.copy(
                        client = initialClient,
                        selectedMonth = nextPending.month,
                        installmentAmount = nextPending.payment,
                        dueDate = nextPending.dueDate,
                        step = BotStep.CONFIRM_INIT
                    )
                    messages.add(
                        PaymentBotMessage(
                            sender = MessageSender.BOT,
                            text = "¡Hola! 🤖 Detecté a **${initialClient.name}**.\n\nSe va a pagar la **Cuota #${nextPending.month} (${formatCurrency(nextPending.payment)})** con vencimiento ${nextPending.dueDate}.\n\n¿Deseas proseguir?",
                            actionChips = listOf(
                                BotActionChip("PROCEED_INIT", "✅ Sí, proseguir", isPrimary = true),
                                BotActionChip("CHANGE_MONTH", "🔄 Cambiar cuota / letra")
                            )
                        )
                    )
                } else {
                    messages.add(
                        PaymentBotMessage(
                            sender = MessageSender.BOT,
                            text = "¡Hola! 🤖 **${initialClient.name}** tiene todas sus cuotas al día."
                        )
                    )
                }
            } else {
                val clientChips = allClients.take(3).map {
                    BotActionChip("SELECT_CLIENT_${it.id}", "👤 ${it.name}", payload = it.id)
                }
                messages.add(
                    PaymentBotMessage(
                        sender = MessageSender.BOT,
                        text = "¡Hola! 🤖 Soy tu **Asistente Inteligente de Cobros y Pagos**.\n\nPuedes pulsar el micrófono 🎙️ o escribir directamente algo como:\n• 'Se acaba de pagar la cuota de Gabriel'\n• 'Usuario 3 ya pagó la cuota'\n• 'Carlos pagó este mes'",
                        actionChips = clientChips + listOf(
                            BotActionChip("VOICE_TRIGGER", "🎙️ Hablar ahora", isPrimary = true)
                        )
                    )
                )
            }
        }
    }

    // Auto-scroll al recibir nuevos mensajes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-scroll cuando se despliega el teclado (IME) para mantener visible el chat y el campo de entrada
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Modal inmersivo a Pantalla Completa
    Dialog(
        onDismissRequest = {
            if (session.step != BotStep.IDLE && session.step != BotStep.COMPLETED) {
                showCancelConfirmDialog = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Asegurar que la ventana del Dialog responda y se redimensione con el teclado virtual (IME)
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            var parent = dialogView.parent
            while (parent != null && parent !is DialogWindowProvider) {
                parent = parent.parent
            }
            val window = (parent as? DialogWindowProvider)?.window
                ?: ((dialogView.context as? Activity)?.window)
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose {}
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Header / Barra de Navegación del Asistente
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Asistente de Pagos",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (session.client != null) "Cliente: ${session.client?.name}" else "Reconocimiento por voz y texto",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (session.step != BotStep.IDLE && session.step != BotStep.COMPLETED) {
                                    showCancelConfirmDialog = true
                                } else {
                                    onDismiss()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Lista de Mensajes del Chat
                val latestMessageWithChipsId = messages.lastOrNull { it.actionChips.isNotEmpty() }?.id

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            showChips = (msg.id == latestMessageWithChipsId),
                            onChipClick = { chip ->
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                handleChipAction(
                                    chip = chip,
                                    session = session,
                                    onSessionUpdate = { session = it },
                                    messages = messages,
                                    allClients = allClients,
                                    scheduleHelper = scheduleHelper,
                                    signatureRepo = signatureRepo,
                                    clientRepo = clientRepo,
                                    notificationManager = notificationManager,
                                    auditRepo = auditRepo,
                                    exportPdfUseCase = exportPdfUseCase,
                                    context = context,
                                    onOpenSignaturePad = { showSignaturePadDialog = true },
                                    onOpenSignatureVault = { showSignatureVaultDialog = true },
                                    onLaunchCamera = { launchCameraForVoucher() },
                                    onLaunchGallery = { galleryLauncher.launch("image/*") },
                                    onLaunchVoice = { launchVoiceRecognition() },
                                    onFinish = onDismiss,
                                    onPaymentRegistered = onPaymentRegistered
                                )
                            }
                        )
                    }
                }

                // Barra Inferior de Entrada (Texto y Micrófono)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de Micrófono
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                launchVoiceRecognition()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Hablar",
                                tint = GreenSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Campo de Texto
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Escribe o pulsa el micrófono...", fontSize = 12.5.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                                keyboardType = KeyboardType.Text
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { sendMessage() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Botón Enviar
                        IconButton(
                            onClick = { sendMessage() },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Diálogo de confirmación para cancelar / salir seguro
            if (showCancelConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showCancelConfirmDialog = false },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning) },
                    title = { Text("¿Deseas cancelar el registro?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Si sales ahora, los datos del pago actual no se guardarán en el sistema.",
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCancelConfirmDialog = false
                                session = PaymentBotSession()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedError)
                        ) {
                            Text("Sí, cancelar y salir")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showCancelConfirmDialog = false }) {
                            Text("Continuar registro")
                        }
                    }
                )
            }

            // Diálogo de firma táctil en pantalla
            if (showSignaturePadDialog) {
                Dialog(
                    onDismissRequest = { showSignaturePadDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        DigitalSignaturePad(
                            signerName = session.client?.name ?: "Administrador",
                            onSignatureCaptured = { path, _, title ->
                                showSignaturePadDialog = false
                                clearPreviousChips(messages)
                                val savedItem = SavedSignatureItem(
                                    title = title,
                                    signerName = session.client?.name ?: "Administrador",
                                    signatureType = "DIGITAL_CANVAS",
                                    filePath = path
                                )
                                session = session.copy(signatureItem = savedItem)
                                messages.add(
                                    PaymentBotMessage(
                                        sender = MessageSender.USER,
                                        text = "✍️ Firma estampada en pantalla (${savedItem.signerName})"
                                    )
                                )
                                executePaymentSave(
                                    session = session,
                                    clientRepo = clientRepo,
                                    notificationManager = notificationManager,
                                    auditRepo = auditRepo,
                                    messages = messages,
                                    onSessionUpdate = { session = it },
                                    onPaymentRegistered = onPaymentRegistered
                                )
                            },
                            onCancel = { showSignaturePadDialog = false }
                        )
                    }
                }
            }

            // Diálogo de catálogo de firmas
            if (showSignatureVaultDialog) {
                SignatureCaptureDialog(
                    signerName = session.client?.name ?: "Administrador",
                    onSignatureSuccess = { path, type, signer, _, details ->
                        showSignatureVaultDialog = false
                        clearPreviousChips(messages)
                        val item = SavedSignatureItem(
                            title = "Firma $signer",
                            signerName = signer,
                            signatureType = type,
                            filePath = path,
                            details = details
                        )
                        session = session.copy(signatureItem = item)
                        messages.add(
                            PaymentBotMessage(
                                sender = MessageSender.USER,
                                text = "📂 Firma de bóveda seleccionada: $signer"
                            )
                        )
                        executePaymentSave(
                            session = session,
                            clientRepo = clientRepo,
                            notificationManager = notificationManager,
                            auditRepo = auditRepo,
                            messages = messages,
                            onSessionUpdate = { session = it },
                            onPaymentRegistered = onPaymentRegistered
                        )
                    },
                    onDismiss = { showSignatureVaultDialog = false }
                )
            }
        }
    }
}

/**
 * Parsea texto con formato **negrita** o *negrita* eliminando los asteriscos literales y aplicando estilo negrita.
 */
private fun parseMarkdownToAnnotated(text: String): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Pattern.compile("(\\*\\*|\\*)(.+?)\\1")
        val matcher = pattern.matcher(text)
        var lastEnd = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val content = matcher.group(2) ?: ""

            if (start > lastEnd) {
                append(text.substring(lastEnd, start).replace("*", ""))
            }

            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(content.replace("*", ""))
            pop()

            lastEnd = end
        }

        if (lastEnd < text.length) {
            append(text.substring(lastEnd).replace("*", ""))
        }
    }
}

/**
 * Elimina los chips interactivos de mensajes previos para que el usuario no pueda hacer clics desfasados.
 */
private fun clearPreviousChips(messages: MutableList<PaymentBotMessage>) {
    for (i in 0 until messages.size) {
        if (messages[i].actionChips.isNotEmpty()) {
            messages[i] = messages[i].copy(actionChips = emptyList())
        }
    }
}

/**
 * Renderizado de una burbuja de chat (Usuario o Asistente Bot) con sus Chips de acción.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubbleItem(
    message: PaymentBotMessage,
    showChips: Boolean = true,
    onChipClick: (BotActionChip) -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val context = LocalContext.current

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.85f,
            transformOrigin = if (isUser) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + slideInVertically(
            initialOffsetY = { 35 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.95f),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                if (!isUser) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column(
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        ),
                        color = when {
                            isUser -> MaterialTheme.colorScheme.primary
                            message.isWarning -> AmberWarning.copy(alpha = 0.15f)
                            message.isSuccess -> GreenSuccess.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = when {
                            message.isWarning -> BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                            message.isSuccess -> BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.5f))
                            else -> null
                        }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(
                                text = parseMarkdownToAnnotated(message.text),
                                fontSize = 13.sp,
                                color = when {
                                    isUser -> Color.White
                                    message.isWarning -> AmberWarningDark
                                    message.isSuccess -> GreenSuccessDark
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                lineHeight = 18.sp
                            )

                            // Si tiene miniatura(s) de comprobante(s)
                            if (message.thumbnailPath != null) {
                                val paths = remember(message.thumbnailPath) {
                                    message.thumbnailPath.split("|").filter { it.isNotBlank() }
                                }
                                if (paths.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (paths.size == 1) {
                                        val singleBitmap = remember(paths[0]) {
                                            try {
                                                val path = paths[0]
                                                if (path.startsWith("content://")) {
                                                    context.contentResolver.openInputStream(Uri.parse(path))?.use {
                                                        BitmapFactory.decodeStream(it)
                                                    }
                                                } else {
                                                    BitmapFactory.decodeFile(path)
                                                }
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                        if (singleBitmap != null) {
                                            Image(
                                                bitmap = singleBitmap.asImageBitmap(),
                                                contentDescription = "Comprobante",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(width = 160.dp, height = 100.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            )
                                        }
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            paths.take(3).forEachIndexed { idx, p ->
                                                val multiBmp = remember(p) {
                                                    try {
                                                        if (p.startsWith("content://")) {
                                                            context.contentResolver.openInputStream(Uri.parse(p))?.use {
                                                                BitmapFactory.decodeStream(it)
                                                            }
                                                        } else {
                                                            BitmapFactory.decodeFile(p)
                                                        }
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                }
                                                if (multiBmp != null) {
                                                    Image(
                                                        bitmap = multiBmp.asImageBitmap(),
                                                        contentDescription = "Comprobante ${idx + 1}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(width = 75.dp, height = 65.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                                    )
                                                }
                                            }
                                            if (paths.size > 3) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 40.dp, height = 65.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "+${paths.size - 3}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Chips de Acción Interactiva (animados al aparecer y desaparecer)
                    AnimatedVisibility(
                        visible = showChips && message.actionChips.isNotEmpty(),
                        enter = fadeIn(animationSpec = tween(durationMillis = 280, delayMillis = 100)) +
                                expandVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) +
                                slideInVertically(
                                    initialOffsetY = { 20 },
                                    animationSpec = tween(durationMillis = 250, delayMillis = 100)
                                ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                               shrinkVertically(animationSpec = tween(durationMillis = 150))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                message.actionChips.forEach { chip ->
                                    ActionChipItem(chip = chip, onClick = { onChipClick(chip) })
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
private fun ActionChipItem(
    chip: BotActionChip,
    onClick: () -> Unit
) {
    if (chip.isPrimary) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.height(34.dp)
        ) {
            Text(chip.label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    } else if (chip.isDanger) {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
            border = BorderStroke(1.dp, RedError.copy(alpha = 0.5f)),
            modifier = Modifier.height(32.dp)
        ) {
            Text(chip.label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    } else {
        SuggestionChip(
            onClick = onClick,
            label = { Text(chip.label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium) },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(32.dp),
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

private fun extractNumericAmountFromText(text: String): Double? {
    val regex = Pattern.compile("(?i)(?:USD|\\$|US\\$)?\\s*([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2}|[0-9]{1,7})\\s*(?:USD|\\$|d[oó]lares|ctvs)?")
    val matcher = regex.matcher(text)
    while (matcher.find()) {
        val numStr = matcher.group(1) ?: continue
        val cleaned = numStr.trim()
        val num = if (cleaned.contains(".") && cleaned.contains(",")) {
            val dotIdx = cleaned.indexOf(".")
            val commaIdx = cleaned.indexOf(",")
            if (dotIdx < commaIdx) {
                cleaned.replace(".", "").replace(",", ".").toDoubleOrNull()
            } else {
                cleaned.replace(",", "").toDoubleOrNull()
            }
        } else if (cleaned.contains(",")) {
            cleaned.replace(",", ".").toDoubleOrNull()
        } else {
            cleaned.toDoubleOrNull()
        }
        if (num != null && num > 0.0 && num < 10000000.0) {
            return num
        }
    }
    return null
}

/**
 * Procesa la frase de voz o texto escrita por el usuario.
 */
private fun handleUserUtterance(
    input: String,
    allClients: List<ClientProfile>,
    session: PaymentBotSession,
    onSessionUpdate: (PaymentBotSession) -> Unit,
    messages: MutableList<PaymentBotMessage>,
    scheduleHelper: PaymentScheduleHelper,
    intentParser: PaymentIntentParser,
    signatureRepo: SavedSignatureRepository
) {
    clearPreviousChips(messages)
    messages.add(PaymentBotMessage(sender = MessageSender.USER, text = input))

    // Si ya hay un cliente seleccionado y el usuario está ajustando el monto o ingresó un número
    if (session.client != null && (session.voucherPath.isNotBlank() || session.step == BotStep.ASKING_SIGNATURE || session.step == BotStep.SELECTING_METHOD)) {
        val parsedNum = extractNumericAmountFromText(input)
        if (parsedNum != null && parsedNum > 0.0) {
            val updated = session.copy(installmentAmount = parsedNum)
            onSessionUpdate(updated)
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    isSuccess = true,
                    text = "✅ Monto modificado correctamente a **${formatCurrency(parsedNum)}**.\n\n¿Deseas proseguir con este monto?",
                    actionChips = listOf(
                        BotActionChip("PROCEED_AFTER_VOUCHER", "✍️ Continuar a la firma", isPrimary = true),
                        BotActionChip("CUSTOM_AMOUNT_PROMPT", "✏️ Cambiar monto de nuevo"),
                        BotActionChip("ADD_MORE_VOUCHERS", "➕ Adjuntar otro comprobante")
                    )
                )
            )
            return
        }
    }

    val parsed = intentParser.parse(input, allClients)
    val client = parsed.client ?: session.client

    if (client == null) {
        val clientChips = allClients.take(4).map {
            BotActionChip("SELECT_CLIENT_${it.id}", "👤 ${it.name}", payload = it.id)
        }
        messages.add(
            PaymentBotMessage(
                sender = MessageSender.BOT,
                text = "No logré identificar al cliente. ¿A cuál de tus clientes deseas registrarle el pago?",
                actionChips = clientChips
            )
        )
        return
    }

    val specificMonth = parsed.specificMonth
    val nextPending = scheduleHelper.getNextPendingInstallment(client)

    if (nextPending == null) {
        messages.add(
            PaymentBotMessage(
                sender = MessageSender.BOT,
                text = "El cliente **${client.name}** ya tiene todas sus cuotas pagadas y está al día."
            )
        )
        return
    }

    // Verificar si el usuario pidió un mes que YA está pagado
    if (specificMonth != null && scheduleHelper.isInstallmentPaid(client, specificMonth)) {
        val paidRecord = scheduleHelper.getExistingPaymentRecord(client, specificMonth)
        val dateInfo = paidRecord?.paymentDate?.ifEmpty { "anteriormente" } ?: "anteriormente"
        messages.add(
            PaymentBotMessage(
                sender = MessageSender.BOT,
                isWarning = true,
                text = "⚠️ **Atención:** La Cuota #$specificMonth de **${client.name}** ya fue pagada el $dateInfo.\n\nLa siguiente cuota pendiente es la **Cuota #${nextPending.month} (${formatCurrency(nextPending.payment)})**.\n\n¿Deseas proseguir con esta cuota?",
                actionChips = listOf(
                    BotActionChip("PROCEED_INIT", "✅ Sí, pagar Cuota #${nextPending.month}", isPrimary = true),
                    BotActionChip("CHANGE_MONTH", "🔄 Ver otras cuotas")
                )
            )
        )
        onSessionUpdate(
            session.copy(
                client = client,
                selectedMonth = nextPending.month,
                installmentAmount = nextPending.payment,
                dueDate = nextPending.dueDate,
                step = BotStep.CONFIRM_INIT
            )
        )
        return
    }

    // Mes válido o siguiente pendiente sugerido
    val targetMonth = specificMonth ?: nextPending.month
    val targetAmount = parsed.amount ?: nextPending.payment
    val targetDueDate = nextPending.dueDate

    onSessionUpdate(
        session.copy(
            client = client,
            selectedMonth = targetMonth,
            installmentAmount = targetAmount,
            dueDate = targetDueDate,
            paymentMethod = parsed.paymentMethod ?: session.paymentMethod,
            step = BotStep.CONFIRM_INIT
        )
    )

    messages.add(
        PaymentBotMessage(
            sender = MessageSender.BOT,
            text = "Detecté que se va a pagar la **Cuota #$targetMonth (${formatCurrency(targetAmount)})** de **${client.name}**.\n\n¿Deseas proseguir?",
            actionChips = listOf(
                BotActionChip("PROCEED_INIT", "✅ Sí, proseguir", isPrimary = true),
                BotActionChip("CHANGE_MONTH", "🔄 Cambiar cuota / letra")
            )
        )
    )
}

/**
 * Maneja los toques en los Chips de acción interactiva.
 */
private fun handleChipAction(
    chip: BotActionChip,
    session: PaymentBotSession,
    onSessionUpdate: (PaymentBotSession) -> Unit,
    messages: MutableList<PaymentBotMessage>,
    allClients: List<ClientProfile>,
    scheduleHelper: PaymentScheduleHelper,
    signatureRepo: SavedSignatureRepository,
    clientRepo: ClientRepository,
    notificationManager: NotificationManagerUseCase,
    auditRepo: AuditRepository,
    exportPdfUseCase: ExportPaymentReceiptPdfUseCase,
    context: Context,
    onOpenSignaturePad: () -> Unit,
    onOpenSignatureVault: () -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onLaunchVoice: () -> Unit,
    onFinish: () -> Unit,
    onPaymentRegistered: (ClientProfile) -> Unit
) {
    when {
        chip.id == "VOICE_TRIGGER" -> onLaunchVoice()

        chip.id.startsWith("SELECT_CLIENT_") -> {
            clearPreviousChips(messages)
            val clientId = chip.payload
            val client = allClients.find { it.id == clientId }
            if (client != null) {
                messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "👤 ${client.name}"))
                val nextPending = scheduleHelper.getNextPendingInstallment(client)
                if (nextPending != null) {
                    onSessionUpdate(
                        session.copy(
                            client = client,
                            selectedMonth = nextPending.month,
                            installmentAmount = nextPending.payment,
                            dueDate = nextPending.dueDate,
                            step = BotStep.CONFIRM_INIT
                        )
                    )
                    messages.add(
                        PaymentBotMessage(
                            sender = MessageSender.BOT,
                            text = "Detecté a **${client.name}**. Se va a pagar la **Cuota #${nextPending.month} (${formatCurrency(nextPending.payment)})**.\n\n¿Deseas proseguir?",
                            actionChips = listOf(
                                BotActionChip("PROCEED_INIT", "✅ Sí, proseguir", isPrimary = true),
                                BotActionChip("CHANGE_MONTH", "🔄 Cambiar cuota / letra")
                            )
                        )
                    )
                }
            }
        }

        chip.id == "PROCEED_INIT" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "✅ Sí, proseguir"))
            onSessionUpdate(session.copy(step = BotStep.SELECTING_METHOD))
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = "Excelente. ¿Cómo realizó el pago **${session.client?.name}**?",
                    actionChips = listOf(
                        BotActionChip("METHOD_TRANSFERENCIA", "💳 Transferencia", isPrimary = true),
                        BotActionChip("METHOD_EFECTIVO", "💵 Efectivo"),
                        BotActionChip("METHOD_VOUCHER", "📷 Subir Comprobante")
                    )
                )
            )
        }

        chip.id == "CHANGE_MONTH" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "🔄 Cambiar cuota / letra"))
            val client = session.client
            if (client != null) {
                val pending = scheduleHelper.getPendingInstallments(client)
                val chips = pending.take(6).map { item ->
                    BotActionChip(
                        id = "SET_MONTH_${item.month}",
                        label = "Cuota #${item.month} (${formatCurrency(item.payment)})",
                        payload = item.month.toString()
                    )
                }
                messages.add(
                    PaymentBotMessage(
                        sender = MessageSender.BOT,
                        text = "Elige la cuota o letra que deseas pagar para **${client.name}**:",
                        actionChips = chips
                    )
                )
            }
        }

        chip.id.startsWith("SET_MONTH_") -> {
            clearPreviousChips(messages)
            val month = chip.payload.toIntOrNull() ?: 1
            val client = session.client
            val schedule = if (client != null) scheduleHelper.getAmortizationSchedule(client) else emptyList()
            val item = schedule.find { it.month == month }
            val amount = item?.payment ?: session.installmentAmount
            val dueDate = item?.dueDate ?: session.dueDate

            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "Cuota #$month (${formatCurrency(amount)})"))

            onSessionUpdate(
                session.copy(
                    selectedMonth = month,
                    installmentAmount = amount,
                    dueDate = dueDate,
                    step = BotStep.SELECTING_METHOD
                )
            )
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = "Cuota seleccionada: **Cuota #$month (${formatCurrency(amount)})**.\n\n¿Cómo realizó el pago?",
                    actionChips = listOf(
                        BotActionChip("METHOD_TRANSFERENCIA", "💳 Transferencia", isPrimary = true),
                        BotActionChip("METHOD_EFECTIVO", "💵 Efectivo"),
                        BotActionChip("METHOD_VOUCHER", "📷 Subir Comprobante")
                    )
                )
            )
        }

        chip.id == "METHOD_TRANSFERENCIA" || chip.id == "METHOD_EFECTIVO" -> {
            clearPreviousChips(messages)
            val method = if (chip.id == "METHOD_TRANSFERENCIA") "Transferencia" else "Efectivo"
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = if (chip.id == "METHOD_TRANSFERENCIA") "💳 Transferencia" else "💵 Efectivo"))
            val updated = session.copy(paymentMethod = method, step = BotStep.ASKING_SIGNATURE)
            onSessionUpdate(updated)
            promptForSignature(messages, updated, signatureRepo)
        }

        chip.id == "METHOD_VOUCHER" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "📷 Subir Comprobante"))
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = "¿Desde dónde deseas adjuntar el váucher de pago?",
                    actionChips = listOf(
                        BotActionChip("CAPTURE_CAM", "📸 Tomar Foto con Cámara", isPrimary = true),
                        BotActionChip("CAPTURE_GAL", "🖼️ Elegir de la Galería"),
                        BotActionChip("SKIP_VOUCHER", "⏩ Omitir foto")
                    )
                )
            )
        }

        chip.id == "CAPTURE_CAM" -> {
            onLaunchCamera()
        }
        chip.id == "CAPTURE_GAL" -> {
            onLaunchGallery()
        }
        chip.id == "SKIP_VOUCHER" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "⏩ Omitir foto"))
            promptForSignature(messages, session, signatureRepo)
        }

        chip.id == "CUSTOM_AMOUNT_PROMPT" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "✏️ Cambiar monto manualmente"))
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = "Por favor escribe el monto deseado en el chat (por ejemplo: **1130** o **$1,130.00**) o díctalo con el micrófono 🎙️:"
                )
            )
        }

        chip.id == "APPLY_OCR_AMOUNT" -> {
            clearPreviousChips(messages)
            val amount = chip.payload.toDoubleOrNull() ?: session.installmentAmount
            val updated = session.copy(installmentAmount = amount)
            onSessionUpdate(updated)
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.USER,
                    text = "Aplicar ${formatCurrency(amount)}"
                )
            )
            promptForSignature(messages, updated, signatureRepo)
        }

        chip.id == "KEEP_SCHEDULED_AMOUNT" -> {
            clearPreviousChips(messages)
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.USER,
                    text = "Conservar ${formatCurrency(session.installmentAmount)}"
                )
            )
            promptForSignature(messages, session, signatureRepo)
        }

        chip.id == "ADD_MORE_VOUCHERS" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "➕ Adjuntar otro comprobante"))
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = "¿Cómo deseas adjuntar el siguiente comprobante?",
                    actionChips = listOf(
                        BotActionChip("CAPTURE_CAM", "📸 Tomar Foto con Cámara", isPrimary = true),
                        BotActionChip("CAPTURE_GAL", "🖼️ Elegir de la Galería"),
                        BotActionChip("FINISH_VOUCHERS", "⏩ No adjuntar más / Continuar a la firma")
                    )
                )
            )
        }

        chip.id == "FINISH_VOUCHERS" || chip.id == "PROCEED_AFTER_VOUCHER" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "✍️ Continuar a la firma"))
            promptForSignature(messages, session, signatureRepo)
        }

        chip.id == "PROMPT_SIGN_YES" -> {
            clearPreviousChips(messages)
            val defaultSig = signatureRepo.getDefaultSignature()
            val sigChips = mutableListOf<BotActionChip>()
            if (defaultSig != null) {
                sigChips.add(
                    BotActionChip(
                        id = "USE_DEFAULT_SIG",
                        label = "⭐ Usar Predeterminada (${defaultSig.signerName})",
                        isPrimary = true
                    )
                )
            }
            sigChips.add(BotActionChip("SIGN_ON_PAD", "✍️ Firmar en pantalla"))
            sigChips.add(BotActionChip("SIGN_FROM_VAULT", "📂 Otra de la bóveda"))
            sigChips.add(BotActionChip("SKIP_SIGNATURE", "⏩ Omitir firma"))

            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = "Elige la firma para estampar en el comprobante:",
                    actionChips = sigChips
                )
            )
        }

        chip.id == "SKIP_SIGNATURE" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "⏩ Omitir firma"))
            executePaymentSave(
                session = session.copy(signatureItem = null),
                clientRepo = clientRepo,
                notificationManager = notificationManager,
                auditRepo = auditRepo,
                messages = messages,
                onSessionUpdate = onSessionUpdate,
                onPaymentRegistered = onPaymentRegistered
            )
        }

        chip.id == "USE_DEFAULT_SIG" -> {
            clearPreviousChips(messages)
            val defaultSig = signatureRepo.getDefaultSignature()
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = chip.label))
            val updated = session.copy(signatureItem = defaultSig)
            onSessionUpdate(updated)
            executePaymentSave(
                session = updated,
                clientRepo = clientRepo,
                notificationManager = notificationManager,
                auditRepo = auditRepo,
                messages = messages,
                onSessionUpdate = onSessionUpdate,
                onPaymentRegistered = onPaymentRegistered
            )
        }

        chip.id == "SIGN_ON_PAD" -> {
            onOpenSignaturePad()
        }

        chip.id == "SIGN_FROM_VAULT" -> {
            onOpenSignatureVault()
        }

        chip.id == "DOWNLOAD_PDF" || chip.id == "SHARE_WHATSAPP" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = if (chip.id == "DOWNLOAD_PDF") "📄 Descargar PDF" else "📲 Compartir WhatsApp"))
            val client = session.client
            val record = session.registeredRecord
            if (client != null && record != null) {
                try {
                    val pdfFile = exportPdfUseCase.generatePdfFile(
                        context = context,
                        record = record,
                        month = record.month,
                        totalMonths = (client.years.toDoubleOrNull() ?: 1.0).toInt() * 12,
                        scheduledPayment = record.amountPaid,
                        interest = 0.0,
                        principal = record.amountPaid,
                        previousBalance = 0.0,
                        remainingBalance = 0.0,
                        clientName = client.name,
                        clientDocumentOrPhone = client.displayDocumentOrPhone
                    )
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (chip.id == "SHARE_WHATSAPP") {
                            setPackage("com.whatsapp")
                        }
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir Comprobante"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Generando PDF...", Toast.LENGTH_SHORT).show()
                }
            }
            promptWhatNext(messages)
        }

        chip.id == "SKIP_DOWNLOAD" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "⏩ Omitir descarga"))
            promptWhatNext(messages)
        }

        chip.id == "RESTART_FLOW" -> {
            clearPreviousChips(messages)
            messages.add(PaymentBotMessage(sender = MessageSender.USER, text = "➕ Registrar otro pago"))
            onSessionUpdate(PaymentBotSession())
            messages.add(
                PaymentBotMessage(
                    sender = MessageSender.BOT,
                    text = "¡Listo! ¿A qué cliente deseas registrarle el siguiente pago? Puedes hablar por el micrófono 🎙️ o escribir su nombre.",
                    actionChips = listOf(
                        BotActionChip("VOICE_TRIGGER", "🎙️ Hablar ahora", isPrimary = true)
                    )
                )
            )
        }

        chip.id == "CLOSE_CHAT" -> onFinish()
    }
}

private fun promptForSignature(
    messages: MutableList<PaymentBotMessage>,
    session: PaymentBotSession,
    signatureRepo: SavedSignatureRepository
) {
    val defaultSig = signatureRepo.getDefaultSignature()
    val sigChips = mutableListOf<BotActionChip>()
    if (defaultSig != null) {
        sigChips.add(
            BotActionChip("USE_DEFAULT_SIG", "⭐ Usar Predeterminada (${defaultSig.signerName})", isPrimary = true)
        )
    }
    sigChips.add(BotActionChip("SIGN_ON_PAD", "✍️ Firmar en pantalla"))
    sigChips.add(BotActionChip("SIGN_FROM_VAULT", "📂 Otra de la bóveda"))
    sigChips.add(BotActionChip("SKIP_SIGNATURE", "⏩ Omitir firma"))

    messages.add(
        PaymentBotMessage(
            sender = MessageSender.BOT,
            text = "Método registrado: **${session.paymentMethod}**.\n\n¿Deseas firmar el comprobante de pago?",
            actionChips = sigChips
        )
    )
}

private fun executePaymentSave(
    session: PaymentBotSession,
    clientRepo: ClientRepository,
    notificationManager: NotificationManagerUseCase,
    auditRepo: AuditRepository,
    messages: MutableList<PaymentBotMessage>,
    onSessionUpdate: (PaymentBotSession) -> Unit,
    onPaymentRegistered: (ClientProfile) -> Unit
) {
    val client = session.client ?: return
    val month = session.selectedMonth ?: 1
    val amount = session.installmentAmount
    val today = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

    val record = PaymentRecord(
        month = month,
        isPaid = true,
        paymentDate = today,
        dueDate = session.dueDate,
        amountPaid = amount,
        paymentMethod = session.paymentMethod,
        referenceNumber = session.referenceNumber,
        bankOrEntity = session.bankOrEntity,
        receiptImagePath = session.voucherPath,
        signaturePath = session.signatureItem?.filePath ?: "",
        signatureType = session.signatureItem?.signatureType ?: "",
        signedBy = session.signatureItem?.signerName ?: "",
        signedAt = if (session.signatureItem != null) System.currentTimeMillis() else 0L,
        signatureDetails = session.signatureItem?.details ?: ""
    )

    val updatedClient = clientRepo.updatePaymentRecord(client.id, record)

    // Emitir notificación en bandeja interna y notificación emergente del sistema (Android push)
    notificationManager.addPaymentReceivedNotification(
        clientName = client.name,
        month = record.month,
        amount = record.amountPaid,
        clientId = client.id
    )

    // Registrar en auditoría de acciones
    auditRepo.log(
        action = AuditActionType.RECORD_PAYMENT,
        description = "Pago cuota #${record.month} registrado ($${String.format(Locale.US, "%.2f", record.amountPaid)}) para '${client.name}' (Asistente IA)",
        username = "Asistente IA",
        userRole = "BOT",
        companyName = "CreditHome",
        details = "Método: ${record.paymentMethod} | Ref: ${record.referenceNumber.ifBlank { "N/A" }}"
    )

    if (updatedClient != null) {
        onPaymentRegistered(updatedClient)
    }

    val updatedSession = session.copy(
        step = BotStep.ASKING_DOWNLOAD_PDF,
        registeredRecord = record
    )
    onSessionUpdate(updatedSession)

    val isSigned = record.signaturePath.isNotBlank()

    messages.add(
        PaymentBotMessage(
            sender = MessageSender.BOT,
            isSuccess = true,
            text = "🎉 **¡Pago Registrado con Éxito!**\n\n• Cliente: **${client.name}**\n• Cuota: **#$month**\n• Monto: **${formatCurrency(amount)}**\n• Método: **${session.paymentMethod}**${if (record.referenceNumber.isNotBlank()) "\n• Ref: **${record.referenceNumber}**" else ""}\n• Firma: ${if (isSigned) "✅ Estampada (${record.signedBy})" else "⏩ Sin firma"}\n\n¿Deseas descargar o compartir el comprobante en PDF?",
            actionChips = listOf(
                BotActionChip("DOWNLOAD_PDF", "📄 Descargar PDF", isPrimary = true),
                BotActionChip("SHARE_WHATSAPP", "📲 Compartir WhatsApp"),
                BotActionChip("SKIP_DOWNLOAD", "⏩ Omitir descarga")
            )
        )
    )
}

private fun promptWhatNext(messages: MutableList<PaymentBotMessage>) {
    messages.add(
        PaymentBotMessage(
            sender = MessageSender.BOT,
            text = "¿Deseas hacer algo más?",
            actionChips = listOf(
                BotActionChip("RESTART_FLOW", "➕ Registrar otro pago", isPrimary = true),
                BotActionChip("CLOSE_CHAT", "❌ Cerrar chat")
            )
        )
    )
}

private fun formatCurrency(amount: Double): String {
    return String.format(Locale.US, "$%,.2f", amount)
}
