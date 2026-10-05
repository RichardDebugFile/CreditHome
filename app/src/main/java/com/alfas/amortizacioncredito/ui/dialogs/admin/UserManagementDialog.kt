package com.alfas.amortizacioncredito.ui.dialogs.admin

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.ui.theme.RedError
import com.alfas.amortizacioncredito.data.model.UserPermissions
import com.alfas.amortizacioncredito.data.model.UserRole
import com.alfas.amortizacioncredito.domain.security.SecurityManager
import java.util.UUID

@Composable
fun UserManagementDialog(
    users: List<AppUser>,
    companyName: String,
    companyRuc: String,
    allowSuperadminAudit: Boolean,
    onSaveUser: (AppUser) -> Unit,
    onDeleteUser: (userId: String) -> Unit,
    onToggleSuperadminAudit: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    var showCreateUserForm by remember { mutableStateOf(false) }

    // Formulario de creación
    var newUsername by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    val newPinError = if (newPin.isBlank()) null else securityManager.validatePinStrength(newPin.trim())
    var selectedRole by remember { mutableStateOf(UserRole.OPERADOR) }

    // Permisos personalizados
    var canEditClients by remember { mutableStateOf(true) }
    var canRecordPayments by remember { mutableStateOf(true) }
    var canDeleteRecords by remember { mutableStateOf(false) }
    var canViewReceipts by remember { mutableStateOf(true) }
    var canExportReports by remember { mutableStateOf(true) }
    var canManageBackups by remember { mutableStateOf(false) }

    var generatedTokenForShare by remember { mutableStateOf<String?>(null) }

    // Modal de compartir credenciales
    if (generatedTokenForShare != null) {
        AlertDialog(
            onDismissRequest = { generatedTokenForShare = null },
            icon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Credenciales de Acceso Generadas", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Puedes enviar este código a la persona para que vincule su teléfono e inicie sesión directamente:", fontSize = 11.sp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = generatedTokenForShare!!,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Acceso a CreditHome - $companyName")
                            putExtra(Intent.EXTRA_TEXT, "Hola, aquí tienes tu código de acceso para CreditHome ($companyName):\n\n${generatedTokenForShare!!}\n\nIngresa con este código desde la app.")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartir Acceso"))
                        generatedTokenForShare = null
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enviar por WhatsApp / Mensaje")
                }
            },
            dismissButton = {
                TextButton(onClick = { generatedTokenForShare = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Gestión de Usuarios y Accesos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Empresa: $companyName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Configuración de Privacidad para Superusuario
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permitir Auditoría a Superusuario", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            Text("Habilita que el soporte central/Superusuario pueda auditar registros en caso de emergencia.", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = allowSuperadminAudit,
                            onCheckedChange = onToggleSuperadminAudit
                        )
                    }
                }

                if (!showCreateUserForm) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Usuarios Vinculados (${users.size}):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Button(
                            onClick = { showCreateUserForm = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nuevo Usuario", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (user in users) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("@${user.username} • ${user.role.badgeText}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                generatedTokenForShare = securityManager.exportInvitationToken(
                                                    username = user.username,
                                                    displayName = user.displayName,
                                                    role = user.role.name,
                                                    companyRuc = companyRuc,
                                                    companyName = companyName,
                                                    // Sin PIN: quien vincula el teléfono define el suyo
                                                    pin = "",
                                                    permissions = user.permissions
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Compartir Acceso", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }

                                        if (user.role != UserRole.MASTER) {
                                            IconButton(
                                                onClick = { onDeleteUser(user.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = RedError, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Formulario para Crear Nuevo Usuario
                    Text("Crear Nuevo Usuario / Operador", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text("Nombre Completo (ej. Juan Pérez)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Nombre de Usuario (ej. juanp)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6) newPin = it },
                        label = { Text("PIN de Acceso (4 a 6 dígitos)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = newPinError != null,
                        supportingText = newPinError?.let { msg -> { Text(msg, fontSize = 9.5.sp) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Permisos Personalizados:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    SwitchItem("Crear / Editar Clientes", canEditClients) { canEditClients = it }
                    SwitchItem("Registrar / Modificar Pagos", canRecordPayments) { canRecordPayments = it }
                    SwitchItem("Ver Comprobantes OCR", canViewReceipts) { canViewReceipts = it }
                    SwitchItem("Exportar Reportes Excel / PDF", canExportReports) { canExportReports = it }
                    SwitchItem("Gestionar Copias de Seguridad", canManageBackups) { canManageBackups = it }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (newUsername.isNotBlank() && newDisplayName.isNotBlank() && newPin.isNotBlank() && newPinError == null) {
                                    val userPerms = UserPermissions(
                                        canEditClients = canEditClients,
                                        canRecordPayments = canRecordPayments,
                                        canDeleteRecords = canDeleteRecords,
                                        canViewReceipts = canViewReceipts,
                                        canExportReports = canExportReports,
                                        canManageBackups = canManageBackups
                                    )
                                    val createdUser = AppUser(
                                        id = UUID.randomUUID().toString(),
                                        username = newUsername.trim().lowercase(),
                                        displayName = newDisplayName.trim(),
                                        role = selectedRole,
                                        companyRucOrId = companyRuc,
                                        companyName = companyName,
                                        permissions = userPerms,
                                        pinHash = securityManager.hashPin(newPin.trim()),
                                        isActive = true
                                    )
                                    onSaveUser(createdUser)
                                    generatedTokenForShare = securityManager.exportInvitationToken(
                                        username = createdUser.username,
                                        displayName = createdUser.displayName,
                                        role = createdUser.role.name,
                                        companyRuc = companyRuc,
                                        companyName = companyName,
                                        pin = newPin.trim(),
                                        permissions = userPerms
                                    )
                                    showCreateUserForm = false
                                }
                            },
                            enabled = newUsername.isNotBlank() && newDisplayName.isNotBlank() && newPin.isNotBlank() && newPinError == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Guardar y Compartir")
                        }

                        OutlinedButton(
                            onClick = { showCreateUserForm = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showCreateUserForm) {
                Button(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        }
    )
}

@Composable
private fun SwitchItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 10.5.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.height(28.dp))
    }
}
