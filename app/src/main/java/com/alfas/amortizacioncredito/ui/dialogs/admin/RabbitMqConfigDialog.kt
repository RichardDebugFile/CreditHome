package com.alfas.amortizacioncredito.ui.dialogs.admin

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.RabbitMqConfig
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeBg
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeText
import com.alfas.amortizacioncredito.ui.theme.OrangeAccent
import com.alfas.amortizacioncredito.ui.theme.OrangeBadgeBg
import com.alfas.amortizacioncredito.ui.theme.OrangeBadgeText
import com.alfas.amortizacioncredito.ui.theme.OrangeDark
import com.alfas.amortizacioncredito.ui.theme.OrangeDeep
import com.alfas.amortizacioncredito.ui.theme.RedBadgeBg
import com.alfas.amortizacioncredito.ui.theme.RedBadgeText
import com.alfas.amortizacioncredito.ui.theme.RedError

@Composable
fun RabbitMqConfigDialog(
    initialConfig: RabbitMqConfig,
    pendingMessagesCount: Int,
    onSaveConfig: (RabbitMqConfig) -> Unit,
    onTestConnection: (RabbitMqConfig, onResult: (Boolean, String) -> Unit) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf(initialConfig.host) }
    var port by remember { mutableStateOf(initialConfig.port.toString()) }
    var virtualHost by remember { mutableStateOf(initialConfig.virtualHost) }
    var username by remember { mutableStateOf(initialConfig.username) }
    var password by remember { mutableStateOf(initialConfig.password) }
    var isEnabled by remember { mutableStateOf(initialConfig.isEnabled) }
    var emailQueueName by remember { mutableStateOf(initialConfig.emailQueueName) }
    var syncQueueName by remember { mutableStateOf(initialConfig.syncQueueName) }

    var isTesting by remember { mutableStateOf(false) }
    var testFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Sincronización en la Nube", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Conexión con servidor central en tiempo real", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Estado de la cola local (Outbox)
                Card(
                    colors = CardDefaults.cardColors(containerColor = OrangeBadgeBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Cambios pendientes por sincronizar:", fontSize = 11.5.sp, color = OrangeBadgeText)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$pendingMessagesCount mensaje(s) pendientes", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = OrangeDeep)
                        }
                        if (pendingMessagesCount > 0) {
                            TextButton(onClick = onClearQueue) {
                                Text("Limpiar", fontSize = 11.5.sp, color = RedError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Switch de activación
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEnabled) OrangeBadgeBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Activar Sincronización Automática", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Envía transacciones y alertas al servidor central", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    }
                }

                Text(
                    text = "CONFIGURACIÓN DEL SERVIDOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = OrangeDark
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP del VPS") },
                    placeholder = { Text("192.168.1.100 o vps.midominio.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto AMQP") },
                        placeholder = { Text("5672") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = virtualHost,
                        onValueChange = { virtualHost = it },
                        label = { Text("VHost") },
                        placeholder = { Text("/") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario RabbitMQ") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "NOMBRES DE COLAS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = OrangeDark
                )

                OutlinedTextField(
                    value = emailQueueName,
                    onValueChange = { emailQueueName = it },
                    label = { Text("Cola de Correos") },
                    placeholder = { Text("credithome_email_queue") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = syncQueueName,
                    onValueChange = { syncQueueName = it },
                    label = { Text("Cola de Sincronización") },
                    placeholder = { Text("credithome_sync_queue") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        isTesting = true
                        testFeedback = null
                        val temp = RabbitMqConfig(
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 5672,
                            virtualHost = virtualHost.trim(),
                            username = username.trim(),
                            password = password.trim(),
                            isEnabled = isEnabled,
                            emailQueueName = emailQueueName.trim(),
                            syncQueueName = syncQueueName.trim()
                        )
                        onTestConnection(temp) { success, msg ->
                            isTesting = false
                            testFeedback = Pair(success, msg)
                        }
                    },
                    enabled = !isTesting && host.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Probar Conexión con Broker", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                testFeedback?.let { (success, msg) ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (success) GreenBadgeBg else RedBadgeBg
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (success) "✅ $msg" else "❌ $msg",
                            color = if (success) GreenBadgeText else RedBadgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = RabbitMqConfig(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 5672,
                        virtualHost = virtualHost.trim().ifBlank { "/" },
                        username = username.trim(),
                        password = password.trim(),
                        isEnabled = isEnabled,
                        emailQueueName = emailQueueName.trim().ifBlank { "credithome_email_queue" },
                        syncQueueName = syncQueueName.trim().ifBlank { "credithome_sync_queue" }
                    )
                    onSaveConfig(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangeDark)
            ) {
                Text("Guardar Configuración", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
