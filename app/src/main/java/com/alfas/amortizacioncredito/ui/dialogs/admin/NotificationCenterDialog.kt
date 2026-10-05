package com.alfas.amortizacioncredito.ui.dialogs.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.alfas.amortizacioncredito.data.model.AppNotification
import com.alfas.amortizacioncredito.data.model.NotificationType
import com.alfas.amortizacioncredito.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationCenterDialog(
    notifications: List<AppNotification>,
    onMarkAllAsRead: () -> Unit,
    onMarkAsRead: (String) -> Unit = {},
    onDeleteNotification: (String) -> Unit = {},
    onClearAllNotifications: () -> Unit = {},
    onNotificationClick: (AppNotification) -> Unit,
    onSendEmailReminder: ((AppNotification) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<NotificationType?>(null) }
    var showDeleteBulkConfirmDialog by remember { mutableStateOf(false) }

    val filteredList = remember(notifications, selectedFilter) {
        if (selectedFilter == null) notifications else notifications.filter { it.type == selectedFilter }
    }

    val unreadCount = remember(notifications) {
        notifications.count { !it.isRead }
    }

    val timeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notificaciones", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (unreadCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "$unreadCount nuevas",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (notifications.isNotEmpty()) {
                            IconButton(
                                onClick = { showDeleteBulkConfirmDialog = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Eliminar todas",
                                    tint = RedError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Barra de herramientas: Filtros y Botón de Marcar Todas Leídas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filtros rápidos
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null },
                            label = { Text("Todas (${notifications.size})", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == NotificationType.OVERDUE,
                            onClick = { selectedFilter = if (selectedFilter == NotificationType.OVERDUE) null else NotificationType.OVERDUE },
                            label = { Text("🔴 Vencidas", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == NotificationType.DUE_SOON,
                            onClick = { selectedFilter = if (selectedFilter == NotificationType.DUE_SOON) null else NotificationType.DUE_SOON },
                            label = { Text("⚠️ Por Vencer", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == NotificationType.PAYMENT_RECEIVED,
                            onClick = { selectedFilter = if (selectedFilter == NotificationType.PAYMENT_RECEIVED) null else NotificationType.PAYMENT_RECEIVED },
                            label = { Text("🟢 Pagos", fontSize = 10.sp) }
                        )
                    }

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalButton(
                            onClick = onMarkAllAsRead,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Leer todas", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No hay notificaciones en esta categoría",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredList, key = { it.id }) { notif ->
                            val (icon, tint) = when (notif.type) {
                                NotificationType.OVERDUE -> Pair(Icons.Default.ErrorOutline, RedErrorLight)
                                NotificationType.DUE_SOON -> Pair(Icons.Default.WarningAmber, AmberAccent)
                                NotificationType.PAYMENT_RECEIVED -> Pair(Icons.Default.CheckCircle, CreditHomeEmerald)
                                NotificationType.SYSTEM_UPDATE -> Pair(Icons.Default.SystemUpdate, BlueAccent)
                                NotificationType.AUDIT_ALERT -> Pair(Icons.Default.Shield, PurpleLight)
                            }

                            val cardBackground = if (!notif.isRead) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                            }

                            val cardBorder = if (!notif.isRead) {
                                BorderStroke(1.dp, tint.copy(alpha = 0.45f))
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }

                            Surface(
                                color = cardBackground,
                                border = cardBorder,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Ícono con fondo de contenedor tintado suave
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(tint.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = notif.title,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (!notif.isRead) {
                                                        Surface(
                                                            color = tint.copy(alpha = 0.18f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "NUEVA",
                                                                color = tint,
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "✓ Leída",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }

                                                    // Botón para eliminar de una en una con un solo clic (directo sin modal)
                                                    IconButton(
                                                        onClick = { onDeleteNotification(notif.id) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = "Eliminar notificación",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(3.dp))

                                            Text(
                                                text = notif.message,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = timeFormat.format(Date(notif.timestamp)),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Fila de acciones inline: Link directo al cambio / Enviar correo / Marcar Leído
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // 1. Link / Botón inline para ir a la parte del cambio
                                        if (notif.targetClientId != null) {
                                            val targetText = when (notif.type) {
                                                NotificationType.PAYMENT_RECEIVED -> if (notif.targetMonth != null) "Ver Cuota #${notif.targetMonth}" else "Ver Comprobante"
                                                NotificationType.OVERDUE, NotificationType.DUE_SOON -> if (notif.targetMonth != null) "Ir a Cuota #${notif.targetMonth}" else "Ir a Cartera"
                                                else -> "Ver Cliente"
                                            }
                                            val targetIcon = when (notif.type) {
                                                NotificationType.PAYMENT_RECEIVED -> Icons.Default.Receipt
                                                NotificationType.OVERDUE, NotificationType.DUE_SOON -> Icons.Default.Payment
                                                else -> Icons.Default.OpenInNew
                                            }

                                            FilledTonalButton(
                                                onClick = { onNotificationClick(notif) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = tint.copy(alpha = 0.18f),
                                                    contentColor = tint
                                                )
                                            ) {
                                                Icon(targetIcon, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(targetText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(11.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else if (notif.type == NotificationType.SYSTEM_UPDATE) {
                                            FilledTonalButton(
                                                onClick = { onNotificationClick(notif) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Actualizar App", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        // 2. Botón Enviar Correo de Recordatorio (para vencidas / por vencer)
                                        if ((notif.type == NotificationType.DUE_SOON || notif.type == NotificationType.OVERDUE) && onSendEmailReminder != null) {
                                            FilledTonalButton(
                                                onClick = { onSendEmailReminder(notif) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Enviar Correo", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        // 3. Botón individual "Leído"
                                        if (!notif.isRead) {
                                            OutlinedButton(
                                                onClick = { onMarkAsRead(notif.id) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Leído", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (notifications.isNotEmpty()) {
                    TextButton(
                        onClick = { showDeleteBulkConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = RedError)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = RedError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedFilter == null) "Eliminar todas" else "Eliminar filtradas (${filteredList.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RedError
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(onClick = onDismiss) {
                    Text("Cerrar", fontSize = 12.sp)
                }
            }
        }
    )

    // Modal de confirmación para eliminación en masa
    if (showDeleteBulkConfirmDialog) {
        val isFiltered = selectedFilter != null
        val countToDelete = if (isFiltered) filteredList.size else notifications.size

        AlertDialog(
            onDismissRequest = { showDeleteBulkConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = RedError,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isFiltered) "¿Eliminar notificaciones filtradas?" else "¿Eliminar todas las notificaciones?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = if (isFiltered) {
                        "Se eliminarán permanentemente las $countToDelete notificaciones de esta categoría. Esta acción no se puede deshacer."
                    } else {
                        "Se eliminarán permanentemente todas las $countToDelete notificaciones del historial. Esta acción no se puede deshacer."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteBulkConfirmDialog = false
                        if (isFiltered) {
                            filteredList.forEach { onDeleteNotification(it.id) }
                        } else {
                            onClearAllNotifications()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sí, Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBulkConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
