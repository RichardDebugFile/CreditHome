package com.alfas.amortizacioncredito.ui.dialogs.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.alfas.amortizacioncredito.domain.usecase.BackupManagerUseCase
import com.alfas.amortizacioncredito.domain.usecase.BackupManifestInfo
import com.alfas.amortizacioncredito.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreDialog(
    onDataRestored: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupUseCase = remember { BackupManagerUseCase(context) }

    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(true) }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingManifestInfo by remember { mutableStateOf<BackupManifestInfo?>(null) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }

    val defaultBackupName = remember {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        "Backup_CreditHome_${sdf.format(Date())}.zip"
    }

    // Launcher para Guardar Copia en Google Drive / Memoria
    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            isExporting = true
            statusMessage = null
            coroutineScope.launch {
                try {
                    val success = context.contentResolver.openOutputStream(destUri)?.use { out ->
                        backupUseCase.exportBackup(out)
                    } ?: false

                    if (success) {
                        isSuccessStatus = true
                        statusMessage = "✅ Copia de seguridad guardada con éxito en Google Drive / Dispositivo."
                    } else {
                        isSuccessStatus = false
                        statusMessage = "❌ Error al generar la copia de seguridad."
                    }
                } catch (e: Exception) {
                    isSuccessStatus = false
                    statusMessage = "❌ Error al exportar: ${e.localizedMessage}"
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // Launcher para Seleccionar archivo de Backup desde Google Drive
    val selectBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            isImporting = true
            statusMessage = null
            coroutineScope.launch {
                try {
                    val manifest = context.contentResolver.openInputStream(fileUri)?.use { input ->
                        backupUseCase.inspectBackup(input)
                    }
                    if (manifest != null) {
                        pendingRestoreUri = fileUri
                        pendingManifestInfo = manifest
                        showConfirmRestoreDialog = true
                    } else {
                        // Si no tiene manifest, intentar restaurar directo
                        val result = context.contentResolver.openInputStream(fileUri)?.use { input ->
                            backupUseCase.restoreBackup(input)
                        }
                        if (result != null && result.success) {
                            isSuccessStatus = true
                            statusMessage = "✅ ${result.message} (${result.restoredClients} clientes restaurados)."
                            onDataRestored()
                        } else {
                            isSuccessStatus = false
                            statusMessage = "❌ ${result?.message ?: "Archivo de copia de seguridad inválido"}"
                        }
                    }
                } catch (e: Exception) {
                    isSuccessStatus = false
                    statusMessage = "❌ Error al inspeccionar archivo: ${e.localizedMessage}"
                } finally {
                    isImporting = false
                }
            }
        }
    }

    // Modal de Confirmación de Restauración (Migración / Histórico)
    if (showConfirmRestoreDialog && pendingManifestInfo != null && pendingRestoreUri != null) {
        val manifest = pendingManifestInfo!!
        val uri = pendingRestoreUri!!

        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Restaurar Copia de Seguridad", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Se encontró una copia de seguridad válida creada el ${manifest.exportDate}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("📱 Origen: ${manifest.appName} (Copia portable)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("👥 Clientes incluidos: ${manifest.totalClients}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("📄 Pagos registrados: ${manifest.totalPayments}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            if (manifest.businessName.isNotBlank()) {
                                Text("🏢 Empresa: ${manifest.businessName}", fontSize = 11.sp)
                            }
                            if (manifest.issuerName.isNotBlank()) {
                                Text("👤 Emisor: ${manifest.issuerName}", fontSize = 11.sp)
                            }
                        }
                    }

                    Text(
                        text = "Elige cómo deseas aplicar este respaldo a tu teléfono actual:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Opción A: Reemplazar todo
                    Surface(
                        color = AmberBadgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "🔄 Reemplazar todo:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberBadgeText
                            )
                            Text(
                                text = "Sobrescribe todos los datos con los del respaldo. Ideal para migrar de teléfono o restaurar un punto histórico anterior.",
                                fontSize = 10.sp,
                                color = AmberBadgeText
                            )
                        }
                    }

                    // Opción B: Fusionar datos
                    Surface(
                        color = GreenBadgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "➕ Fusionar / Unir datos:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenBadgeText
                            )
                            Text(
                                text = "Conserva tus clientes locales actuales e incorpora los clientes nuevos del respaldo sin borrar nada.",
                                fontSize = 10.sp,
                                color = GreenBadgeText
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            showConfirmRestoreDialog = false
                            isImporting = true
                            coroutineScope.launch {
                                try {
                                    val res = context.contentResolver.openInputStream(uri)?.use { input ->
                                        backupUseCase.restoreBackup(input, mergeWithExisting = false)
                                    }
                                    if (res != null && res.success) {
                                        isSuccessStatus = true
                                        statusMessage = "🎉 ¡Restauración completa! Se cargaron ${res.restoredClients} clientes y ${res.restoredReceipts} comprobantes."
                                        onDataRestored()
                                    } else {
                                        isSuccessStatus = false
                                        statusMessage = "❌ ${res?.message ?: "Error al restaurar copia"}"
                                    }
                                } catch (e: Exception) {
                                    isSuccessStatus = false
                                    statusMessage = "❌ Error: ${e.localizedMessage}"
                                } finally {
                                    isImporting = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🔄 Reemplazar Todo (Punto Anterior / Nuevo Teléfono)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showConfirmRestoreDialog = false
                            isImporting = true
                            coroutineScope.launch {
                                try {
                                    val res = context.contentResolver.openInputStream(uri)?.use { input ->
                                        backupUseCase.restoreBackup(input, mergeWithExisting = true)
                                    }
                                    if (res != null && res.success) {
                                        isSuccessStatus = true
                                        statusMessage = "🎉 ¡Fusión exitosa! Total actual: ${res.restoredClients} clientes y ${res.restoredReceipts} comprobantes."
                                        onDataRestored()
                                    } else {
                                        isSuccessStatus = false
                                        statusMessage = "❌ ${res?.message ?: "Error al fusionar copia"}"
                                    }
                                } catch (e: Exception) {
                                    isSuccessStatus = false
                                    statusMessage = "❌ Error: ${e.localizedMessage}"
                                } finally {
                                    isImporting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("➕ Fusionar / Unir con mis Clientes Actuales", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = { showConfirmRestoreDialog = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancelar", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = null
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = CreditHomeBlue,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Copia de Seguridad & Nube",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Google Drive & Respaldo Seguro",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mensaje de estado
                statusMessage?.let { msg ->
                    Surface(
                        color = if (isSuccessStatus) GreenBadgeBg else RedBadgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = if (isSuccessStatus) GreenBadgeText else RedBadgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // SECCIÓN 1: EXPORTAR / GUARDAR COPIA MANUALMENTE
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1. Guardar / Exportar Copia Manual", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = "Genera un archivo seguro (.zip) con todos tus clientes, créditos, comprobantes OCR y logotipo para guardarlo en Google Drive o tu celular.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "💡 Para guardar en Google Drive: En la ventana que se abrirá, toca el menú lateral (☰) y selecciona tu cuenta de Google Drive.",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { saveBackupLauncher.launch(defaultBackupName) },
                                enabled = !isExporting && !isImporting,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Guardando...", fontSize = 10.sp)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Guardar en Drive / Celular", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    isExporting = true
                                    statusMessage = null
                                    coroutineScope.launch {
                                        try {
                                            val tempFile = File(context.cacheDir, defaultBackupName)
                                            FileOutputStream(tempFile).use { out ->
                                                backupUseCase.exportBackup(out)
                                            }
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, "Copia de Seguridad CreditHome")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Compartir Copia de Seguridad"))
                                            isSuccessStatus = true
                                            statusMessage = "Copia generada lista para compartir."
                                        } catch (e: Exception) {
                                            isSuccessStatus = false
                                            statusMessage = "Error al compartir: ${e.localizedMessage}"
                                        } finally {
                                            isExporting = false
                                        }
                                    }
                                },
                                enabled = !isExporting && !isImporting,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Compartir", fontSize = 10.sp)
                            }
                        }
                    }
                }

                // SECCIÓN 2: SUBIR / RESTAURAR COPIA MANUALMENTE
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("2. Cargar Respaldo (De este u otro teléfono)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GreenSuccess)
                        }
                        Text(
                            text = "Sube cualquier archivo .zip de respaldo (guardado en Google Drive, Descargas, recibido por WhatsApp o de otro celular) para restaurar o fusionar clientes.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                selectBackupLauncher.launch(
                                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")
                                )
                            },
                            enabled = !isExporting && !isImporting,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Subiendo y procesando copia...", fontSize = 10.sp)
                            } else {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Subir / Cargar Archivo de Backup (.zip)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // SECCIÓN 3: INFORMACIÓN DE AUTO BACKUP ANDROID
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto Backup de Android (Google One)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Android respalda los datos del sistema en tu cuenta de Google automáticamente en segundo plano (con Wi-Fi y cargador conectado). Para transferencias inmediatas o guardar una copia puntual, utiliza el botón de guardar en Drive de arriba.",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
