package com.alfas.amortizacioncredito.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfas.amortizacioncredito.domain.security.SignatureSecurityVault
import com.alfas.amortizacioncredito.ui.theme.Slate900
import java.io.File

/**
 * Diálogo reutilizable para visualización interactiva de comprobantes, recibos y firmas.
 * Cuenta con zoom táctil (pinch-to-zoom), desplazamiento (pan), doble toque y barra de control.
 */
@Composable
fun ZoomableImageViewerDialog(
    imagePath: String?,
    title: String = "Vista de Comprobante",
    onDismiss: () -> Unit
) {
    if (imagePath.isNullOrBlank()) return

    val cleanPath = imagePath.removePrefix("file://")
    val file = File(cleanPath)
    if (!file.exists()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var zoomScale by remember { mutableFloatStateOf(1f) }
        var zoomOffset by remember { mutableStateOf(Offset.Zero) }

        val fullBmp = remember(cleanPath) {
            try {
                SignatureSecurityVault.loadSignatureBitmap(cleanPath)?.asImageBitmap()
                    ?: BitmapFactory.decodeFile(cleanPath)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra superior del visor con controles de Zoom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Botón Zoom Out (-)
                        IconButton(
                            onClick = {
                                zoomScale = (zoomScale - 0.5f).coerceAtLeast(1f)
                                if (zoomScale <= 1f) zoomOffset = Offset.Zero
                            },
                            enabled = zoomScale > 1f,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ZoomOut,
                                contentDescription = "Alejar",
                                tint = if (zoomScale > 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Indicador de Porcentaje / Botón Reset a 100%
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable {
                                zoomScale = 1f
                                zoomOffset = Offset.Zero
                            }
                        ) {
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        // Botón Zoom In (+)
                        IconButton(
                            onClick = {
                                zoomScale = (zoomScale + 0.5f).coerceAtMost(5f)
                            },
                            enabled = zoomScale < 5f,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ZoomIn,
                                contentDescription = "Acercar",
                                tint = if (zoomScale < 5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Botón Cerrar (X)
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Lienzo de Imagen con Soporte de Gestos (Pinch-to-zoom, Pan y Doble toque)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate900)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.2f) {
                                        zoomScale = 1f
                                        zoomOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.5f
                                        zoomOffset = Offset.Zero
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                if (zoomScale > 1f) {
                                    val maxOffsetX = (zoomScale - 1f) * 600f
                                    val maxOffsetY = (zoomScale - 1f) * 800f
                                    zoomOffset = Offset(
                                        x = (zoomOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (zoomOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    zoomOffset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (fullBmp != null) {
                        Image(
                            bitmap = fullBmp,
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = zoomOffset.x
                                    translationY = zoomOffset.y
                                },
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "No se pudo cargar la imagen",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar Imagen")
                }
            }
        }
    }
}
