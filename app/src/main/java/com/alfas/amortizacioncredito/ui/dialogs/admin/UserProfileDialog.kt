package com.alfas.amortizacioncredito.ui.dialogs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.data.model.UserRole
import com.alfas.amortizacioncredito.ui.theme.AmberBadgeBg
import com.alfas.amortizacioncredito.ui.theme.AmberBadgeText
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeBg
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeText
import com.alfas.amortizacioncredito.ui.theme.GreenSuccess
import com.alfas.amortizacioncredito.ui.theme.PurpleBadgeBg
import com.alfas.amortizacioncredito.ui.theme.PurpleDark
import com.alfas.amortizacioncredito.ui.theme.RedBadgeText
import com.alfas.amortizacioncredito.ui.theme.RedError
import com.alfas.amortizacioncredito.ui.theme.SkyBadgeBg
import com.alfas.amortizacioncredito.ui.theme.SkyBadgeText

@Composable
fun UserProfileDialog(
    user: AppUser,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = RedError) },
            title = { Text("¿Cerrar Sesión?", fontWeight = FontWeight.Bold) },
            text = { Text("Se cerrará tu sesión activa en este dispositivo.", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Text("Sí, Cerrar Sesión", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Rol y Empresa
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rol Asignado:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                color = when (user.role) {
                                    UserRole.MASTER -> AmberBadgeBg
                                    UserRole.SUPERADMIN -> PurpleBadgeBg
                                    UserRole.OPERADOR -> GreenBadgeBg
                                    UserRole.VISUALIZADOR -> SkyBadgeBg
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = user.role.badgeText,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (user.role) {
                                        UserRole.MASTER -> AmberBadgeText
                                        UserRole.SUPERADMIN -> PurpleDark
                                        UserRole.OPERADOR -> GreenBadgeText
                                        UserRole.VISUALIZADOR -> SkyBadgeText
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Empresa:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(user.companyName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Permisos Activos
                Text("Permisos Activos:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        PermissionItem("Crear / Editar Clientes", user.permissions.canEditClients)
                        PermissionItem("Registrar / Modificar Pagos", user.permissions.canRecordPayments)
                        PermissionItem("Eliminar Registros", user.permissions.canDeleteRecords)
                        PermissionItem("Ver Comprobantes OCR", user.permissions.canViewReceipts)
                        PermissionItem("Exportar Reportes Excel / PDF", user.permissions.canExportReports)
                        PermissionItem("Gestionar Copias de Seguridad", user.permissions.canManageBackups)
                    }
                }

                // Estado de Softoken
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = GreenSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Softoken Activo (Dispositivo Seguro)", fontSize = 10.5.sp, color = GreenSuccess, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Volver")
            }
        }
    )
}

@Composable
private fun PermissionItem(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 10.sp, color = if (granted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(text = if (granted) "✅ Concedido" else "❌ No permitido", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = if (granted) GreenBadgeText else RedBadgeText)
    }
}
