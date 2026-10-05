package com.alfas.amortizacioncredito.ui.dialogs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.SmtpConfig
import com.alfas.amortizacioncredito.ui.theme.AmberBadgeBg
import com.alfas.amortizacioncredito.ui.theme.AmberBadgeText
import com.alfas.amortizacioncredito.ui.theme.AmberTextDark
import com.alfas.amortizacioncredito.ui.theme.BlueBadgeBg
import com.alfas.amortizacioncredito.ui.theme.CreditHomeBlue
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeBg
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeText
import com.alfas.amortizacioncredito.ui.theme.RedBadgeBg
import com.alfas.amortizacioncredito.ui.theme.RedBadgeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmtpConfigDialog(
    initialConfig: SmtpConfig,
    onSaveConfig: (SmtpConfig) -> Unit,
    onTestEmail: (recipientEmail: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf(initialConfig.host) }
    var port by remember { mutableStateOf(initialConfig.port.toString()) }
    var senderEmail by remember { mutableStateOf(initialConfig.senderEmail) }
    var senderName by remember { mutableStateOf(initialConfig.senderName) }
    var appPassword by remember { mutableStateOf(initialConfig.appPassword) }
    var isEnabled by remember { mutableStateOf(initialConfig.isEnabled) }
    val selectedAlertDays = remember { mutableStateListOf<Int>().apply { addAll(initialConfig.alertDaysBefore) } }

    var testEmailInput by remember { mutableStateOf(initialConfig.senderEmail) }
    var isTesting by remember { mutableStateOf(false) }
    var testFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showHelpGuide by remember { mutableStateOf(false) }

    val availableDays = listOf(
        7 to "7 días antes",
        5 to "5 días antes",
        3 to "3 días antes",
        1 to "1 día antes",
        0 to "Día de Pago (Hoy)",
        -1 to "En Mora (Vencidas)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = CreditHomeBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Notificaciones por Correo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Alertas automáticas de cobro y comprobantes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Switch de activación
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEnabled) BlueBadgeBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Activar Notificaciones por Correo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Enviar recordatorios y comprobantes automáticos", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    }
                }

                // Credenciales Gmail
                Text("CREDENCIALES DE GMAIL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = senderEmail,
                    onValueChange = { senderEmail = it },
                    label = { Text("Correo Gmail del Emisor") },
                    placeholder = { Text("empresa@gmail.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Nombre del Remitente") },
                    placeholder = { Text("CreditHome Cobranzas") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = appPassword,
                    onValueChange = { appPassword = it },
                    label = { Text("Contraseña de Aplicación (16 dígitos)") },
                    placeholder = { Text("xxxx xxxx xxxx xxxx") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showHelpGuide = !showHelpGuide }) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "¿Cómo obtenerla?", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showHelpGuide) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberBadgeBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("💡 ¿Cómo obtener la Contraseña de Aplicación de Google?", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AmberBadgeText)
                            Text(
                                "1. Ve a tu Cuenta de Google (myaccount.google.com).\n" +
                                "2. En 'Seguridad', activa la Verificación en 2 pasos.\n" +
                                "3. Busca 'Contraseñas de aplicaciones'.\n" +
                                "4. Genera una nueva contraseña y pégala aquí (16 letras).",
                                fontSize = 10.5.sp,
                                color = AmberTextDark
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Días de alerta
                Text("REGLAS DE RECORDATORIO (DÍAS DE ALERTA)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Selecciona cuándo se deben enviar los recordatorios de cobro:", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableDays.forEach { (day, label) ->
                            val isSelected = selectedAlertDays.contains(day)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedAlertDays.remove(day) else selectedAlertDays.add(day)
                                },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Sección de Prueba
                Text("PROBAR CONEXIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = testEmailInput,
                        onValueChange = { testEmailInput = it },
                        placeholder = { Text("Correo de prueba") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            isTesting = true
                            testFeedback = null
                            val tempConfig = SmtpConfig(
                                host = host,
                                port = port.toIntOrNull() ?: 465,
                                senderEmail = senderEmail,
                                senderName = senderName,
                                appPassword = appPassword,
                                isEnabled = isEnabled,
                                alertDaysBefore = selectedAlertDays.toList()
                            )
                            onTestEmail(testEmailInput) { success, msg ->
                                isTesting = false
                                testFeedback = Pair(success, msg)
                            }
                        },
                        enabled = !isTesting && senderEmail.isNotBlank() && appPassword.isNotBlank() && testEmailInput.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Probar", fontSize = 11.sp)
                        }
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
                    val updated = SmtpConfig(
                        host = host.ifBlank { "smtp.gmail.com" },
                        port = port.toIntOrNull() ?: 465,
                        senderEmail = senderEmail.trim(),
                        senderName = senderName.trim(),
                        appPassword = appPassword.trim(),
                        isEnabled = isEnabled,
                        alertDaysBefore = selectedAlertDays.toList()
                    )
                    onSaveConfig(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CreditHomeBlue)
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
