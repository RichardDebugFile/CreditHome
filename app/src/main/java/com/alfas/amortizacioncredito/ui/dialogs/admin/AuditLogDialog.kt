package com.alfas.amortizacioncredito.ui.dialogs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.AuditActionType
import com.alfas.amortizacioncredito.data.model.AuditLogEntry
import com.alfas.amortizacioncredito.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AuditLogDialog(
    logs: List<AuditLogEntry>,
    companyName: String,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf<AuditActionType?>(null) }
    val timeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = remember(logs, searchQuery, selectedAction) {
        logs.filter { entry ->
            val matchesQuery = searchQuery.isBlank() ||
                    entry.description.contains(searchQuery, ignoreCase = true) ||
                    entry.username.contains(searchQuery, ignoreCase = true)
            val matchesAction = selectedAction == null || entry.action == selectedAction
            matchesQuery && matchesAction
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HistoryEdu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Registro de Auditoría", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Trazabilidad de acciones de $companyName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Buscador
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por usuario o acción...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )

                // Filtros por tipo de acción
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedAction == null,
                            onClick = { selectedAction = null },
                            label = { Text("Todos (${logs.size})", fontSize = 10.5.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedAction == AuditActionType.RECORD_PAYMENT,
                            onClick = { selectedAction = if (selectedAction == AuditActionType.RECORD_PAYMENT) null else AuditActionType.RECORD_PAYMENT },
                            label = { Text("🟢 Pagos", fontSize = 10.5.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedAction == AuditActionType.CREATE_CLIENT,
                            onClick = { selectedAction = if (selectedAction == AuditActionType.CREATE_CLIENT) null else AuditActionType.CREATE_CLIENT },
                            label = { Text("👥 Clientes", fontSize = 10.5.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedAction == AuditActionType.BACKUP_EXPORT,
                            onClick = { selectedAction = if (selectedAction == AuditActionType.BACKUP_EXPORT) null else AuditActionType.BACKUP_EXPORT },
                            label = { Text("☁️ Copias", fontSize = 10.5.sp) }
                        )
                    }
                }

                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (logs.isEmpty()) "Aún no hay actividades registradas en auditoría" else "No se encontraron registros",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            val (badgeBg, badgeText) = when (log.action) {
                                AuditActionType.RECORD_PAYMENT -> Pair(GreenBadgeBg, GreenBadgeText)
                                AuditActionType.CANCEL_PAYMENT -> Pair(RedBadgeBg, RedBadgeText)
                                AuditActionType.CREATE_CLIENT -> Pair(BlueBadgeLightBg, BlueInfoDark)
                                AuditActionType.EDIT_CLIENT -> Pair(AmberBadgeBg, AmberBadgeText)
                                AuditActionType.DELETE_CLIENT -> Pair(RedBadgeBg, RedBadgeText)
                                AuditActionType.BACKUP_EXPORT, AuditActionType.BACKUP_RESTORE -> Pair(PurpleBadgeBg, PurpleDark)
                                else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(color = badgeBg, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = log.action.displayName,
                                                color = badgeText,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = timeFormat.format(Date(log.timestamp)),
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = log.description,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "👤 Por: ${log.username} (${log.userRole})",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
