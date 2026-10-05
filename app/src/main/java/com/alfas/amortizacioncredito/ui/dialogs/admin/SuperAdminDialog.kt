package com.alfas.amortizacioncredito.ui.dialogs.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.ui.theme.Amber700
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeBg
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeText
import com.alfas.amortizacioncredito.ui.theme.PurpleAccent
import com.alfas.amortizacioncredito.ui.theme.RedBadgeBg
import com.alfas.amortizacioncredito.ui.theme.RedBadgeText

@Composable
fun SuperAdminDialog(
    masterUsers: List<AppUser>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = PurpleAccent,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Panel de Superusuario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Auditoría Global de Empresas Vinculadas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Empresas en la plataforma (${masterUsers.size}):",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurpleAccent
                )

                if (masterUsers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No hay empresas registradas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(masterUsers, key = { it.id }) { companyMaster ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(companyMaster.companyName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Surface(
                                            color = if (companyMaster.allowSuperadminAudit) GreenBadgeBg else RedBadgeBg,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (companyMaster.allowSuperadminAudit) "🔓 Auditoría Autorizada" else "🔒 Privada (Sin Acceso)",
                                                color = if (companyMaster.allowSuperadminAudit) GreenBadgeText else RedBadgeText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text("👑 Administrador: ${companyMaster.displayName} (@${companyMaster.username})", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (companyMaster.companyRucOrId.isNotBlank()) {
                                        Text("📄 RUC/ID: ${companyMaster.companyRucOrId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    if (!companyMaster.allowSuperadminAudit) {
                                        Text(
                                            text = "ℹ️ Por políticas de privacidad, los registros de esta empresa no son visibles a menos que el usuario máster active el permiso de soporte.",
                                            fontSize = 9.sp,
                                            color = Amber700
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
