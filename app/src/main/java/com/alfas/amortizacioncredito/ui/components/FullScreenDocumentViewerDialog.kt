package com.alfas.amortizacioncredito.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfas.amortizacioncredito.ui.theme.Slate900

/**
 * Diálogo a pantalla completa para previsualizar documentos y páginas PDF generadas,
 * con controles de acercar/alejar, porcentaje de zoom, reinicio y paneo libre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDocumentViewerDialog(
    bitmap: Bitmap?,
    title: String = "Comprobante PDF Oficial",
    subtitle: String = "",
    onShare: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (bitmap == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var fullScale by remember { mutableFloatStateOf(1.2f) }
        var fullOffsetX by remember { mutableFloatStateOf(0f) }
        var fullOffsetY by remember { mutableFloatStateOf(0f) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (subtitle.isNotBlank()) {
                                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            fullScale = (fullScale - 0.25f).coerceAtLeast(0.75f)
                            if (fullScale <= 1f) {
                                fullOffsetX = 0f
                                fullOffsetY = 0f
                            }
                        }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Alejar")
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable {
                                fullScale = 1.2f
                                fullOffsetX = 0f
                                fullOffsetY = 0f
                            }
                        ) {
                            Text(
                                text = "${(fullScale * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        IconButton(onClick = {
                            fullScale = (fullScale + 0.25f).coerceAtMost(5f)
                        }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Acercar")
                        }

                        IconButton(onClick = {
                            fullScale = 1.2f
                            fullOffsetX = 0f
                            fullOffsetY = 0f
                        }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reiniciar Zoom")
                        }

                        if (onShare != null) {
                            IconButton(onClick = onShare) {
                                Icon(Icons.Default.Share, contentDescription = "Compartir Documento", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Slate900)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            fullScale = (fullScale * zoom).coerceIn(0.75f, 5f)
                            val maxOff = (fullScale - 0.75f) * 1200f
                            fullOffsetX = (fullOffsetX + pan.x * fullScale).coerceIn(-maxOff, maxOff)
                            fullOffsetY = (fullOffsetY + pan.y * fullScale).coerceIn(-maxOff * 1.6f, maxOff * 1.6f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = fullScale
                            scaleY = fullScale
                            translationX = fullOffsetX
                            translationY = fullOffsetY
                        }
                        .shadow(8.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
