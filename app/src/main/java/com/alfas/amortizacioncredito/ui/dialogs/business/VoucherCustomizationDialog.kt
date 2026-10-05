package com.alfas.amortizacioncredito.ui.dialogs.business

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfas.amortizacioncredito.data.model.CustomVoucherField
import com.alfas.amortizacioncredito.data.model.VoucherConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherCustomizationDialog(
    initialConfig: VoucherConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (VoucherConfig) -> Unit
) {
    var currentConfig by remember { mutableStateOf(initialConfig) }
    var undoStack by remember { mutableStateOf(listOf<VoucherConfig>()) }
    var redoStack by remember { mutableStateOf(listOf<VoucherConfig>()) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    // Form states for adding a custom field
    var newFieldLabel by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

    fun pushConfigChange(newConfig: VoucherConfig) {
        undoStack = undoStack + currentConfig
        redoStack = emptyList()
        currentConfig = newConfig
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + currentConfig
            currentConfig = prev
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + currentConfig
            currentConfig = next
        }
    }

    fun handleReset() {
        pushConfigChange(VoucherConfig.DEFAULT)
        showResetConfirmation = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Fila 1: Título Principal + Botón de Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Personalizar Voucher",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Fila 2: Subtítulo Explicativo + Barra de Acciones (Deshacer, Rehacer, Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activa o quita campos y ajusta el recibo.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Undo Button
                        IconButton(
                            onClick = { handleUndo() },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Deshacer",
                                tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Redo Button
                        IconButton(
                            onClick = { handleRedo() },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Rehacer",
                                tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Reset Button
                        IconButton(
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reiniciar campos",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Financiero", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.5.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Transacción", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 12.5.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Empresa/Cliente", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 12.5.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Campos Extra (${currentConfig.customFields.size})", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal, fontSize = 12.5.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // 1. Financiero
                                Text(
                                    text = "DESGLOSE DE CUOTA Y SALDOS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                VoucherToggleCard(
                                    title = "📉 Saldo Restante / Falta por pagar",
                                    description = "Muestra el nuevo saldo pendiente después de registrar el abono.",
                                    checked = currentConfig.showRemainingBalance,
                                    isHighlighted = true,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showRemainingBalance = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Saldo Anterior",
                                    description = "Muestra el saldo antes de aplicar este abono.",
                                    checked = currentConfig.showPreviousBalance,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showPreviousBalance = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Cuota Programada / Prevista",
                                    description = "Muestra el valor fijado en la tabla de amortización.",
                                    checked = currentConfig.showScheduledPayment,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showScheduledPayment = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Amortización a Capital",
                                    description = "Porción de la cuota dirigida a reducir el capital adeudado.",
                                    checked = currentConfig.showPrincipal,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showPrincipal = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Interés del Período",
                                    description = "Porción de interés cobrado en la cuota.",
                                    checked = currentConfig.showInterest,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showInterest = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Abono Extraordinario",
                                    description = "Muestra si hubo un anticipo adicional directo a capital.",
                                    checked = currentConfig.showExtraPayment,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showExtraPayment = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Total Recibido / Abonado",
                                    description = "Monto final pagado en la transacción.",
                                    checked = currentConfig.showTotalReceived,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showTotalReceived = it)) }
                                )
                            }
                            1 -> {
                                // 2. Transacción
                                Text(
                                    text = "SOPORTE BANCARIO Y REGISTRO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                VoucherToggleCard(
                                    title = "Medio de Pago",
                                    description = "Efectivo, Transferencia, Depósito, Tarjeta, etc.",
                                    checked = currentConfig.showPaymentMethod,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showPaymentMethod = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Entidad Financiera / Banco",
                                    description = "Nombre del banco o cooperativa donde se recibió el pago.",
                                    checked = currentConfig.showBankEntity,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showBankEntity = it)) }
                                )

                                VoucherToggleCard(
                                    title = "N° de Referencia / Comprobante",
                                    description = "Código de transacción bancaria.",
                                    checked = currentConfig.showReferenceNumber,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showReferenceNumber = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Titular / Depositante",
                                    description = "Nombre de la persona que realizó el depósito o transferencia.",
                                    checked = currentConfig.showPayerName,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showPayerName = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Observaciones y Notas",
                                    description = "Notas adicionales redactadas al registrar el pago.",
                                    checked = currentConfig.showNotes,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showNotes = it)) }
                                )
                            }
                            2 -> {
                                // 3. Empresa y Cliente
                                Text(
                                    text = "DATOS DE CABECERA Y CLIENTE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                VoucherToggleCard(
                                    title = "Logotipo / Isotipo de la Empresa",
                                    description = "Muestra el logotipo o el cuadro de iniciales en la cabecera.",
                                    checked = currentConfig.showLogo,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showLogo = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Nombre de la Empresa / Negocio",
                                    description = "Razón social o marca en el encabezado del voucher.",
                                    checked = currentConfig.showBusinessName,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showBusinessName = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Nombre del Emisor / Asesor",
                                    description = "Nombre del responsable de emitir el comprobante.",
                                    checked = currentConfig.showIssuerName,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showIssuerName = it)) }
                                )

                                VoucherToggleCard(
                                    title = "RUC / Cédula del Emisor",
                                    description = "Identificación tributaria del negocio.",
                                    checked = currentConfig.showRuc,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showRuc = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Dirección del Negocio",
                                    description = "Ubicación o sucursal del negocio.",
                                    checked = currentConfig.showAddress,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showAddress = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Cédula / Teléfono del Cliente",
                                    description = "Información de contacto del cliente en el recibo.",
                                    checked = currentConfig.showClientDocOrPhone,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showClientDocOrPhone = it)) }
                                )

                                VoucherToggleCard(
                                    title = "✍️ Firma y Validación",
                                    description = "Muestra u oculta la sección de firma manuscrita, digital o física en el voucher.",
                                    checked = currentConfig.showSignature,
                                    isHighlighted = true,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showSignature = it)) }
                                )

                                VoucherToggleCard(
                                    title = "Pie de Página Institucional",
                                    description = "Mensaje de cierre 'Documento digital generado por...'",
                                    checked = currentConfig.showFooterNote,
                                    onCheckedChange = { pushConfigChange(currentConfig.copy(showFooterNote = it)) }
                                )

                                OutlinedTextField(
                                    value = currentConfig.customFooterText,
                                    onValueChange = { pushConfigChange(currentConfig.copy(customFooterText = it)) },
                                    label = { Text("Nota de pie personalizada (Opcional)") },
                                    placeholder = { Text("Ej: ¡Gracias por su puntual pago!") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            3 -> {
                                // 4. Campos Personalizados (Aumentar / Quitar campos)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "➕ Agregar Campo Personalizado",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Crea campos adicionales como Garante, Sucursal, Asesor de Crédito, Nota Legal, etc.",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = newFieldLabel,
                                                onValueChange = { newFieldLabel = it },
                                                label = { Text("Etiqueta") },
                                                placeholder = { Text("Ej: Garante") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                singleLine = true
                                            )

                                            OutlinedTextField(
                                                value = newFieldValue,
                                                onValueChange = { newFieldValue = it },
                                                label = { Text("Valor / Detalle") },
                                                placeholder = { Text("Ej: Juan Pérez") },
                                                modifier = Modifier.weight(1.3f),
                                                shape = RoundedCornerShape(10.dp),
                                                singleLine = true
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (newFieldLabel.isNotBlank()) {
                                                    val newField = CustomVoucherField(
                                                        label = newFieldLabel.trim(),
                                                        value = newFieldValue.trim(),
                                                        isEnabled = true
                                                    )
                                                    pushConfigChange(
                                                        currentConfig.copy(
                                                            customFields = currentConfig.customFields + newField
                                                        )
                                                    )
                                                    newFieldLabel = ""
                                                    newFieldValue = ""
                                                }
                                            },
                                            enabled = newFieldLabel.isNotBlank(),
                                            modifier = Modifier.align(Alignment.End),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Añadir Campo", fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (currentConfig.customFields.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No has agregado campos personalizados aún.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "CAMPOS PERSONALIZADOS ACTIVOS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )

                                    currentConfig.customFields.forEachIndexed { index, field ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = field.label,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (field.value.isNotBlank()) {
                                                        Text(
                                                            text = field.value,
                                                            fontSize = 11.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Switch(
                                                        checked = field.isEnabled,
                                                        onCheckedChange = { checked ->
                                                            val updatedList = currentConfig.customFields.toMutableList()
                                                            updatedList[index] = field.copy(isEnabled = checked)
                                                            pushConfigChange(currentConfig.copy(customFields = updatedList))
                                                        }
                                                    )

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    IconButton(
                                                        onClick = {
                                                            val updatedList = currentConfig.customFields.filterIndexed { i, _ -> i != index }
                                                            pushConfigChange(currentConfig.copy(customFields = updatedList))
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Eliminar",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(20.dp)
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

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Bottom Buttons (Cancelar / Guardar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            onSaveConfig(currentConfig)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Reset
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¿Restablecer al Diseño Original?")
                }
            },
            text = {
                Text("Se reestablecerán todos los campos del comprobante a los valores predeterminados de fábrica. Podrás usar el botón Deshacer (Undo) si deseas volver atrás.")
            },
            confirmButton = {
                Button(
                    onClick = { handleReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, Restablecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun VoucherToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    isHighlighted: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isHighlighted) 1.dp else 0.dp,
                color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
