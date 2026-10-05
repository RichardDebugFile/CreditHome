package com.alfas.amortizacioncredito.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.data.model.UserPermissions
import com.alfas.amortizacioncredito.data.model.UserRole
import com.alfas.amortizacioncredito.data.repository.AuthRepository
import com.alfas.amortizacioncredito.domain.security.SecurityManager
import com.alfas.amortizacioncredito.ui.theme.*
import java.util.UUID

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: (AppUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showImportTokenDialog by remember { mutableStateOf(false) }
    var invitationTokenInput by remember { mutableStateOf("") }
    var invitationPinInput by remember { mutableStateOf("") }
    var invitationError by remember { mutableStateOf<String?>(null) }

    // Registro inicial: no existe cuenta por defecto, el primer usuario crea la cuenta Máster
    var needsInitialSetup by remember { mutableStateOf(authRepository.needsInitialSetup()) }
    var setupDisplayName by remember { mutableStateOf("") }
    var setupPinConfirm by remember { mutableStateOf("") }

    // Modal para Importar Token de Invitación
    if (showImportTokenDialog) {
        AlertDialog(
            onDismissRequest = { showImportTokenDialog = false },
            icon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Vincular con Código de Invitación", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pega aquí el código de invitación que te proporcionó el usuario Máster de tu empresa:", fontSize = 11.sp)
                    OutlinedTextField(
                        value = invitationTokenInput,
                        onValueChange = { invitationTokenInput = it },
                        placeholder = { Text("Pega el código aquí...", fontSize = 11.sp) },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invitationPinInput,
                        onValueChange = { if (it.length <= 6) invitationPinInput = it },
                        label = { Text("PIN de acceso (4 a 6 dígitos)", fontSize = 11.sp) },
                        supportingText = { Text("Déjalo vacío si el Máster ya te asignó un PIN", fontSize = 9.5.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    invitationError?.let {
                        Text(it, color = RedBadgeText, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = importClick@{
                        val parsed = securityManager.parseInvitationToken(invitationTokenInput)
                        if (parsed != null) {
                            val uName = parsed.optString("u", "").trim()
                            val dName = parsed.optString("d", "")
                            val roleStr = parsed.optString("r", UserRole.OPERADOR.name)
                            val cRuc = parsed.optString("c", "")
                            val cName = parsed.optString("n", "CreditHome")
                            val pinVal = invitationPinInput.trim().ifBlank { parsed.optString("p", "").trim() }

                            if (uName.isBlank()) {
                                invitationError = "El código de invitación no contiene un usuario válido."
                                return@importClick
                            }
                            if (authRepository.getAllUsers().any { it.username.equals(uName, ignoreCase = true) }) {
                                invitationError = "El usuario @$uName ya existe en este dispositivo."
                                return@importClick
                            }
                            if (pinVal.isBlank()) {
                                invitationError = "Define un PIN de acceso para tu cuenta."
                                return@importClick
                            }
                            securityManager.validatePinStrength(pinVal)?.let {
                                invitationError = it
                                return@importClick
                            }

                            val perms = UserPermissions(
                                canEditClients = parsed.optBoolean("editClients", true),
                                canRecordPayments = parsed.optBoolean("recordPayments", true),
                                canDeleteRecords = false,
                                canViewReceipts = parsed.optBoolean("viewReceipts", true),
                                canExportReports = parsed.optBoolean("exportReports", true),
                                canManageBackups = parsed.optBoolean("manageBackups", false)
                            )

                            val importedUser = AppUser(
                                id = UUID.randomUUID().toString(),
                                username = uName,
                                displayName = dName,
                                role = UserRole.valueOf(roleStr),
                                companyRucOrId = cRuc,
                                companyName = cName,
                                permissions = perms,
                                pinHash = securityManager.hashPin(pinVal),
                                isActive = true
                            )
                            authRepository.saveUser(importedUser)
                            needsInitialSetup = authRepository.needsInitialSetup()
                            errorMessage = null
                            showImportTokenDialog = false
                            invitationTokenInput = ""
                            invitationPinInput = ""
                            invitationError = null
                            Toast.makeText(context, "✅ Cuenta vinculada exitosamente. Ingresa con tu PIN.", Toast.LENGTH_LONG).show()
                            username = uName
                            pin = ""
                        } else {
                            invitationError = "Código de invitación inválido."
                        }
                    }
                ) {
                    Text("Vincular Teléfono")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportTokenDialog = false
                    invitationError = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Logo & Header Oficial CreditHome
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Slate900),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.alfas.amortizacioncredito.R.drawable.ic_credithome_logo),
                        contentDescription = "CreditHome Logo",
                        modifier = Modifier.size(54.dp)
                    )
                }

                Text(
                    text = "CreditHome",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (needsInitialSetup) "Configuración Inicial • Crea tu cuenta Máster" else "Acceso Seguro • Softoken Activo",
                    fontSize = 11.5.sp,
                    color = GreenSuccess,
                    fontWeight = FontWeight.SemiBold
                )

                errorMessage?.let { msg ->
                    Surface(
                        color = RedBadgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = RedBadgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (needsInitialSetup) {
                    Text(
                        text = "No hay ninguna cuenta registrada en este dispositivo. Crea la cuenta Máster que administrará la empresa.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = setupDisplayName,
                        onValueChange = { setupDisplayName = it },
                        label = { Text("Nombre completo", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text(if (needsInitialSetup) "Nuevo PIN (4 a 6 dígitos)" else "PIN de Seguridad", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (needsInitialSetup) {
                    OutlinedTextField(
                        value = setupPinConfirm,
                        onValueChange = { if (it.length <= 6) setupPinConfirm = it },
                        label = { Text("Confirmar PIN", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = submitClick@{
                        if (needsInitialSetup) {
                            if (username.isBlank()) {
                                errorMessage = "Ingresa un nombre de usuario."
                                return@submitClick
                            }
                            securityManager.validatePinStrength(pin)?.let {
                                errorMessage = it
                                return@submitClick
                            }
                            if (pin != setupPinConfirm) {
                                errorMessage = "Los PIN no coinciden."
                                return@submitClick
                            }
                            authRepository.createInitialMaster(username, setupDisplayName, pin)
                            needsInitialSetup = false
                            setupPinConfirm = ""
                        }

                        val logged = authRepository.login(username, pin)
                        if (logged != null) {
                            errorMessage = null
                            onLoginSuccess(logged)
                        } else {
                            errorMessage = "Usuario o PIN incorrecto."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(if (needsInitialSetup) Icons.Default.PersonAdd else Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (needsInitialSetup) "Crear Cuenta Máster" else "Iniciar Sesión", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedButton(
                    onClick = { showImportTokenDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vincular con Código de Invitación", fontSize = 11.sp)
                }
            }
        }
    }
}
