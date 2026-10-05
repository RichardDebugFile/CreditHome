package com.alfas.amortizacioncredito.ui.dialogs.client

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.ui.components.CountryPhoneInputField
import com.alfas.amortizacioncredito.util.CountryPhoneCode
import com.alfas.amortizacioncredito.util.ValidationUtils
import com.alfas.amortizacioncredito.ui.theme.BlueBadgeLightBg
import com.alfas.amortizacioncredito.ui.theme.CreditHomeBlueDark
import com.alfas.amortizacioncredito.ui.theme.BlueInfoDark
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SaveClientDialog(
    initialName: String = "",
    initialDocument: String = "",
    initialPhone: String = "",
    initialAddress: String = "",
    initialEmail: String = "",
    initialNotes: String = "",
    initialPrice: String = "",
    initialDownPayment: String = "",
    initialAnnualRate: String = "",
    initialYears: String = "",
    initialStartDate: String = "",
    initialIsFixedPriceLoan: Boolean = false,
    paidMonthsCount: Int = 0,
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, documentNumber: String, phone: String, address: String, email: String, notes: String, price: String, downPayment: String, annualRate: String, years: String, startDate: String, isFixedPriceLoan: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val todayFormatted = remember {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date())
    }

    val parsedInitialPhone = remember(initialPhone) {
        ValidationUtils.parsePhoneAndCountry(initialPhone)
    }

    var name by remember { mutableStateOf(initialName) }
    var documentNumber by remember { mutableStateOf(initialDocument) }
    var selectedCountry by remember { mutableStateOf(parsedInitialPhone.first) }
    var phoneNumber by remember { mutableStateOf(parsedInitialPhone.second) }
    var address by remember { mutableStateOf(initialAddress) }
    var email by remember { mutableStateOf(initialEmail) }
    var notes by remember { mutableStateOf(initialNotes) }
    var price by remember { mutableStateOf(initialPrice) }
    var downPayment by remember { mutableStateOf(initialDownPayment) }
    var annualRate by remember { mutableStateOf(initialAnnualRate) }
    var years by remember { mutableStateOf(initialYears) }
    var startDate by remember { mutableStateOf(initialStartDate.ifEmpty { todayFormatted }) }
    var isFixedPriceLoan by remember { mutableStateOf(initialIsFixedPriceLoan) }

    val formattedCurrentPhone = remember(selectedCountry, phoneNumber) {
        ValidationUtils.formatFullPhone(selectedCountry, phoneNumber)
    }

    var showError by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showConfirmDiscard by remember { mutableStateOf(false) }
    var showConfirmSave by remember { mutableStateOf(false) }

    val isEmailValid = remember(email) {
        ValidationUtils.isValidEmail(email)
    }

    val idValidation = remember(documentNumber, selectedCountry) {
        ValidationUtils.validateIdentification(documentNumber, selectedCountry)
    }

    val hasChanges = remember(
        name, documentNumber, formattedCurrentPhone, address, email, notes, price, downPayment, annualRate, years, startDate, isFixedPriceLoan,
        initialName, initialDocument, initialPhone, initialAddress, initialEmail, initialNotes, initialPrice, initialDownPayment, initialAnnualRate, initialYears, initialStartDate, initialIsFixedPriceLoan
    ) {
        name.trim() != initialName.trim() ||
                documentNumber.trim() != initialDocument.trim() ||
                formattedCurrentPhone.trim() != initialPhone.trim() ||
                address.trim() != initialAddress.trim() ||
                email.trim() != initialEmail.trim() ||
                notes.trim() != initialNotes.trim() ||
                price.trim() != initialPrice.trim() ||
                downPayment.trim() != initialDownPayment.trim() ||
                annualRate.trim() != initialAnnualRate.trim() ||
                years.trim() != initialYears.trim() ||
                startDate.trim() != initialStartDate.trim() ||
                isFixedPriceLoan != initialIsFixedPriceLoan
    }

    val attemptDismiss = {
        if (hasChanges) {
            showConfirmDiscard = true
        } else {
            onDismiss()
        }
    }

    val parsedPrice = price.toDoubleOrNull() ?: 0.0
    val parsedDownPayment = downPayment.toDoubleOrNull() ?: 0.0
    val financed = maxOf(0.0, parsedPrice - parsedDownPayment)

    // 1. Modal de Confirmación para Descartar Cambios
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
            text = { Text("Tienes datos modificados sin guardar. Si sales ahora, se perderán los cambios realizados.") },
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

    // 2. Modal de Confirmación para Eliminar Cliente
    if (showConfirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("¿Eliminar Cliente?") },
            text = { Text("¿Estás seguro de que deseas eliminar a '$initialName' y todo su historial de pagos registrados?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 3. Modal de Doble Confirmación con Resumen de Datos a Guardar
    if (showConfirmSave) {
        AlertDialog(
            onDismissRequest = { showConfirmSave = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isEditing) "¿Confirmar Actualización de Datos?" else "¿Confirmar y Registrar Cliente?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Por favor, verifica que la siguiente información sea la correcta antes de aplicar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cliente: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(name.trim(), fontSize = 12.sp)
                            }

                            if (documentNumber.trim().isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cédula / RUC: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(documentNumber.trim(), fontSize = 12.sp)
                                }
                            }

                            if (formattedCurrentPhone.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Contacto: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(formattedCurrentPhone, fontSize = 12.sp)
                                }
                            }

                            if (address.trim().isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dirección: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(address.trim(), fontSize = 12.sp)
                                }
                            }

                            if (email.trim().isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Correo: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(email.trim(), fontSize = 12.sp)
                                }
                            }

                            if (price.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Financiamiento: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        "${formatCurrency(financed)} (Precio ${formatCurrency(parsedPrice)} - Entrada ${formatCurrency(parsedDownPayment)})",
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (annualRate.isNotEmpty() && years.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tasa y Plazo: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("$annualRate% anual | $years año(s)", fontSize = 12.sp)
                                }
                            }

                            if (startDate.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inicio / 1er Pago: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(startDate.trim(), fontSize = 12.sp)
                                }
                            }

                            if (notes.trim().isNotEmpty()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Notas: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(notes.trim(), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (isEditing && paidMonthsCount > 0) {
                        Surface(
                            color = BlueBadgeLightBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = CreditHomeBlueDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Este cliente tiene $paidMonthsCount cuota(s) registradas. La tabla y el saldo se recalcularán con estos nuevos datos.",
                                    fontSize = 11.sp,
                                    color = BlueInfoDark,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSave = false
                        onSave(
                            ValidationUtils.sanitizeText(name),
                            ValidationUtils.sanitizeText(documentNumber),
                            formattedCurrentPhone,
                            ValidationUtils.sanitizeText(address),
                            ValidationUtils.sanitizeText(email),
                            ValidationUtils.sanitizeText(notes),
                            price.trim().ifEmpty { initialPrice },
                            downPayment.trim().ifEmpty { initialDownPayment },
                            annualRate.trim().ifEmpty { initialAnnualRate },
                            years.trim().ifEmpty { initialYears },
                            startDate.trim().ifEmpty { todayFormatted },
                            isFixedPriceLoan
                        )
                    }
                ) {
                    Text("Confirmar y Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSave = false }) {
                    Text("Volver y Modificar")
                }
            }
        )
    }

    // Modal Principal del Formulario
    AlertDialog(
        onDismissRequest = attemptDismiss,
        icon = {
            Icon(
                imageVector = if (isEditing) Icons.Default.Edit else Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isEditing) "Editar Cliente y Condiciones" else "Guardar como Cliente",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isEditing) "Modifica los datos del cliente y los parámetros del crédito pactado."
                    else "Asigna esta simulación a un cliente para dar seguimiento y control a sus pagos mensuales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) showError = false
                    },
                    label = { Text("Nombre o Razón Social *") },
                    placeholder = { Text("Ej: Carlos Mendoza o Importadora S.A.") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("El nombre es obligatorio") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Campo Cédula / RUC (Facturación)
                OutlinedTextField(
                    value = documentNumber,
                    onValueChange = { input ->
                        val sanitized = ValidationUtils.sanitizeText(input)
                        val filtered = if (selectedCountry == CountryPhoneCode.ECUADOR) {
                            sanitized.filter { it.isDigit() }.take(13)
                        } else {
                            sanitized.take(20)
                        }
                        documentNumber = filtered
                    },
                    label = {
                        Text(
                            if (selectedCountry == CountryPhoneCode.ECUADOR)
                                "Cédula o RUC (Facturación / Opcional)"
                            else
                                "Identificación / Tax ID (Opcional)"
                        )
                    },
                    placeholder = {
                        Text(
                            if (selectedCountry == CountryPhoneCode.ECUADOR)
                                "Ej: 0912345678 (10) o 0912345678001 (13)"
                            else
                                "Ej: 123456789"
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (selectedCountry == CountryPhoneCode.ECUADOR) KeyboardType.Number else KeyboardType.Text
                    ),
                    isError = documentNumber.isNotBlank() && !idValidation.isValid,
                    supportingText = if (documentNumber.isNotBlank() && !idValidation.isValid && idValidation.errorMessage != null) {
                        { Text(idValidation.errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de País y Teléfono/Celular Internacional (+593 Ecuador por defecto)
                CountryPhoneInputField(
                    selectedCountry = selectedCountry,
                    phoneNumber = phoneNumber,
                    onCountryChange = { selectedCountry = it },
                    onPhoneChange = { phoneNumber = it },
                    label = "Celular / Teléfono (Opcional)"
                )

                // Campo Dirección / Domicilio Fiscal (Facturación)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección / Domicilio (Facturación / Opcional)") },
                    placeholder = { Text("Ej: Av. 9 de Octubre 102 y Boyacá, Guayaquil") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo Electrónico (Facturación / Opcional)", maxLines = 1) },
                    placeholder = { Text("cliente@correo.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = !isEmailValid,
                    supportingText = if (!isEmailValid) {
                        { Text("Formato de correo no válido (ej: cliente@correo.com)", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de Fecha de Primer Pago
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Fecha de Inicio / Primer Pago") },
                    placeholder = { Text("dd/MM/yyyy") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            val cal = Calendar.getInstance()
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            try {
                                val d = sdf.parse(startDate)
                                if (d != null) cal.time = d
                            } catch (_: Exception) {}

                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    startDate = String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEditing) {
                    Text(
                        text = "Parámetros del Crédito:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = ValidationUtils.filterDecimalInput(it) },
                            label = { Text("Precio Total ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = downPayment,
                            onValueChange = { downPayment = ValidationUtils.filterDecimalInput(it) },
                            label = { Text("Entrada ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = annualRate,
                            onValueChange = { annualRate = ValidationUtils.filterDecimalInput(it) },
                            label = { Text("Tasa Anual (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = years,
                            onValueChange = { years = ValidationUtils.filterDecimalInput(it) },
                            label = { Text("Plazo (Años)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Modalidad al adelantar cuotas o hacer abonos
                Text(
                    text = "Modalidad al Adelantar Cuotas / Abonos:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isFixedPriceLoan) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (!isFixedPriceLoan) 1.5.dp else 0.5.dp,
                        color = if (!isFixedPriceLoan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFixedPriceLoan = false }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isFixedPriceLoan,
                            onClick = { isFixedPriceLoan = false }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Reducir intereses de cuotas futuras",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Amortización estándar: un pago anticipado reduce el saldo y descuenta intereses futuros (ahorro para el cliente).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFixedPriceLoan) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isFixedPriceLoan) 1.5.dp else 0.5.dp,
                        color = if (isFixedPriceLoan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFixedPriceLoan = true }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isFixedPriceLoan,
                            onClick = { isFixedPriceLoan = true }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Monto fijo pactado (Sin quitar intereses)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Se cancelan las letras faltantes manteniendo fijos los intereses y el precio acordado del crédito.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones del Cliente (Opcional)") },
                    placeholder = { Text("Ej: Garante: Juan Pérez, cuotas quincenales, etc.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isEditing && onDelete != null) {
                    TextButton(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar")
                    }
                }

                Button(
                    onClick = {
                        val trimmed = ValidationUtils.sanitizeText(name)
                        if (trimmed.isNotEmpty()) {
                            if (isEmailValid && idValidation.isValid) {
                                showConfirmSave = true
                            }
                        } else {
                            showError = true
                        }
                    }
                ) {
                    Text(if (isEditing) "Actualizar" else "Guardar Cliente")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = attemptDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private val currencyFormatter = ThreadLocal.withInitial {
    DecimalFormat("$#,##0.00", DecimalFormatSymbols(Locale.US))
}

private fun formatCurrency(amount: Double): String {
    return currencyFormatter.get()?.format(amount) ?: String.format(Locale.US, "$%,.2f", amount)
}
