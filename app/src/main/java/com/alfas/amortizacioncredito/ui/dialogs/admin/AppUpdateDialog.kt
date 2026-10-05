package com.alfas.amortizacioncredito.ui.dialogs.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.AppUpdateInfo
import com.alfas.amortizacioncredito.ui.theme.CreditHomeBlue

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDownload: (apkUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!updateInfo.isMandatory) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = CreditHomeBlue,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("¡Nueva versión disponible!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Hay una nueva versión de CreditHome lista para instalar desde el servidor.",
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("• Versión instalada: v${updateInfo.currentVersionName}", fontSize = 11.sp)
                        Text("• Nueva versión: v${updateInfo.latestVersionName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text("Novedades:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(updateInfo.releaseNotes, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDownload(updateInfo.apkUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = CreditHomeBlue)
            ) {
                Text("Descargar e Instalar APK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (!updateInfo.isMandatory) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Más tarde")
                }
            }
        } else null
    )
}
