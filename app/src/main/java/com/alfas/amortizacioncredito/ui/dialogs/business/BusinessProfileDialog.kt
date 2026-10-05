package com.alfas.amortizacioncredito.ui.dialogs.business

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.ui.components.CountryPhoneInputField
import com.alfas.amortizacioncredito.util.CountryPhoneCode
import com.alfas.amortizacioncredito.util.ValidationUtils
import com.alfas.amortizacioncredito.ui.theme.AmberWarningDark
import com.alfas.amortizacioncredito.ui.theme.RedError
import java.io.File

@Composable
fun BusinessProfileDialog(
    currentProfile: BusinessProfile,
    onSaveProfile: (profile: BusinessProfile) -> Unit,
    onSaveLogoUri: (uri: Uri) -> String,
    onRemoveLogo: () -> Unit,
    onOpenVoucherCustomization: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val parsedInitialPhone = remember(currentProfile.phone) {
        ValidationUtils.parsePhoneAndCountry(currentProfile.phone)
    }

    var businessName by remember { mutableStateOf(currentProfile.businessName) }
    var issuerName by remember { mutableStateOf(currentProfile.issuerName) }
    var ruc by remember { mutableStateOf(currentProfile.ruc) }
    var address by remember { mutableStateOf(currentProfile.address) }
    var selectedCountry by remember { mutableStateOf(parsedInitialPhone.first) }
    var phoneNumber by remember { mutableStateOf(parsedInitialPhone.second) }
    var email by remember { mutableStateOf(currentProfile.email) }
    var logoPath by remember { mutableStateOf(currentProfile.logoPath) }

    val formattedCurrentPhone = remember(selectedCountry, phoneNumber) {
        ValidationUtils.formatFullPhone(selectedCountry, phoneNumber)
    }

    val idValidation = remember(ruc, selectedCountry) {
        ValidationUtils.validateIdentification(ruc, selectedCountry)
    }

    val isEmailValid = remember(email) {
        ValidationUtils.isValidEmail(email)
    }

    val logoBitmap = remember(logoPath) {
        if (logoPath.isNotBlank() && File(logoPath).exists()) {
            try {
                BitmapFactory.decodeFile(logoPath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = onSaveLogoUri(it)
            if (savedPath.isNotBlank()) {
                logoPath = savedPath
            }
        }
    }

    var showError by remember { mutableStateOf(false) }
    var showConfirmSave by remember { mutableStateOf(false) }
    var showConfirmDiscard by remember { mutableStateOf(false) }
    var showConfirmRemoveLogo by remember { mutableStateOf(false) }

    val hasChanges = remember(
        businessName, issuerName, ruc, address, formattedCurrentPhone, email, logoPath,
        currentProfile
    ) {
        businessName.trim() != currentProfile.businessName.trim() ||
                issuerName.trim() != currentProfile.issuerName.trim() ||
                ruc.trim() != currentProfile.ruc.trim() ||
                address.trim() != currentProfile.address.trim() ||
                formattedCurrentPhone.trim() != currentProfile.phone.trim() ||
                email.trim() != currentProfile.email.trim() ||
                logoPath.trim() != currentProfile.logoPath.trim()
    }

    val attemptDismiss = {
        if (hasChanges) {
            showConfirmDiscard = true
        } else {
            onDismiss()
        }
    }

    // Modal 1: Confirmación de Descarte de Cambios
    if (showConfirmDiscard) {
        AlertDialog(
            onDismissRequest = { showConfirmDiscard = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = AmberWarningDark,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("¿Descartar Cambios?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Tienes modificaciones sin guardar en los datos de la empresa. Si sales ahora, se perderán los cambios.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDiscard = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Text("Sí, Descartar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDiscard = false }) {
                    Text("Seguir Editando")
                }
            }
        )
    }

    // Modal 2: Confirmación para Eliminar Logotipo
    if (showConfirmRemoveLogo) {
        AlertDialog(
            onDismissRequest = { showConfirmRemoveLogo = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("¿Eliminar Logotipo?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Se quitará el logotipo actual del perfil y de los futuros comprobantes de pago generados.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRemoveLogo = false
                        onRemoveLogo()
                        logoPath = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, Quitar Logo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRemoveLogo = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal 3: Confirmación Previa a Guardar Datos de Empresa
    if (showConfirmSave) {
        AlertDialog(
            onDismissRequest = { showConfirmSave = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Confirmar Datos de Empresa", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Verifica los datos que aparecerán en la cabecera de tus vouchers y tablas:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🏢 Empresa: ${businessName.trim().ifEmpty { "CreditHome" }}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (issuerName.isNotBlank()) Text("👤 Emisor: ${issuerName.trim()}", fontSize = 11.5.sp)
                            if (ruc.isNotBlank()) Text("🪪 ID/RUC: ${ruc.trim()}", fontSize = 11.5.sp)
                            if (formattedCurrentPhone.isNotBlank()) Text("📞 Contacto: $formattedCurrentPhone", fontSize = 11.5.sp)
                            if (email.isNotBlank()) Text("✉️ Correo: ${email.trim()}", fontSize = 11.5.sp)
                            if (address.isNotBlank()) Text("📍 Dirección: ${address.trim()}", fontSize = 11.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSave = false
                        onSaveProfile(
                            BusinessProfile(
                                businessName = ValidationUtils.sanitizeText(businessName).ifEmpty { "CreditHome" },
                                issuerName = ValidationUtils.sanitizeText(issuerName),
                                ruc = ValidationUtils.sanitizeText(ruc),
                                address = ValidationUtils.sanitizeText(address),
                                phone = formattedCurrentPhone,
                                email = ValidationUtils.sanitizeText(email),
                                logoPath = logoPath
                            )
                        )
                        onDismiss()
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

    AlertDialog(
        onDismissRequest = attemptDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Datos de Empresa / Emisor",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selector / Previsualizador de Logotipo
                Text(
                    text = "Logotipo de la Empresa / Marca:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap,
                            contentDescription = "Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Subir Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Subir Logo",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (logoBitmap != null) "Cambiar Logo" else "Seleccionar Logo", fontSize = 10.sp)
                    }

                    if (logoBitmap != null) {
                        TextButton(
                            onClick = {
                                showConfirmRemoveLogo = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quitar", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Nombre Comercial o Razón Social de la Empresa
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Nombre de la Empresa / Entidad *") },
                    placeholder = { Text("Ej: CreditHome / Concesionario") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Nombre del Emisor / Asesor / Usuario
                OutlinedTextField(
                    value = issuerName,
                    onValueChange = { issuerName = it },
                    label = { Text("Nombre del Emisor / Asesor") },
                    placeholder = { Text("Ej: Lcdo. Carlos Alarcón - Asesor") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                // RUC o Cédula / Identificación del Emisor
                OutlinedTextField(
                    value = ruc,
                    onValueChange = { input ->
                        val sanitized = ValidationUtils.sanitizeText(input)
                        if (selectedCountry == CountryPhoneCode.ECUADOR) {
                            val digitsOnly = sanitized.filter { it.isDigit() }
                            if (digitsOnly.length <= 13) ruc = digitsOnly
                        } else {
                            if (sanitized.length <= 20) ruc = sanitized
                        }
                    },
                    label = {
                        Text(if (selectedCountry == CountryPhoneCode.ECUADOR) "RUC / Cédula del Emisor" else "Identificación / Tax ID")
                    },
                    placeholder = {
                        Text(if (selectedCountry == CountryPhoneCode.ECUADOR) "Ej: 0992384729001 (13 dígitos)" else "Ej: 123456789")
                    },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (selectedCountry == CountryPhoneCode.ECUADOR) KeyboardType.Number else KeyboardType.Text
                    ),
                    isError = !idValidation.isValid,
                    supportingText = if (!idValidation.isValid && idValidation.errorMessage != null) {
                        { Text(idValidation.errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Dirección y Ciudad
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección / Sucursal / Ciudad") },
                    placeholder = { Text("Ej: Av. 9 de Octubre y Boyacá, Guayaquil") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de País y Teléfono de Contacto (+593 por defecto)
                CountryPhoneInputField(
                    selectedCountry = selectedCountry,
                    phoneNumber = phoneNumber,
                    onCountryChange = { selectedCountry = it },
                    onPhoneChange = { phoneNumber = it },
                    label = "Teléfono de Contacto / WhatsApp"
                )

                // Correo Electrónico
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo Electrónico") },
                    placeholder = { Text("Ej: pagos@credithome.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = !isEmailValid,
                    supportingText = if (!isEmailValid) {
                        { Text("Formato de correo no válido (ej: contacto@empresa.com)", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (onOpenVoucherCustomization != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    OutlinedButton(
                        onClick = onOpenVoucherCustomization,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Personalizar Campos del Voucher", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isEmailValid && idValidation.isValid) {
                        showConfirmSave = true
                    }
                },
                enabled = isEmailValid && idValidation.isValid
            ) {
                Text("Guardar Datos")
            }
        },
        dismissButton = {
            TextButton(onClick = attemptDismiss) {
                Text("Cancelar")
            }
        }
    )
}
