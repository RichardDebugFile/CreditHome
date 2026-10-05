package com.alfas.amortizacioncredito.ui.dialogs.business

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfas.amortizacioncredito.ui.theme.Slate100
import com.alfas.amortizacioncredito.ui.theme.Slate200
import com.alfas.amortizacioncredito.ui.theme.Slate400
import com.alfas.amortizacioncredito.ui.theme.Slate900

@Composable
fun DigitalSignaturePad(
    signerName: String,
    isStandaloneVault: Boolean = false,
    onSignatureCaptured: (imagePath: String, saveToCatalog: Boolean, customTitle: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val completedStrokes = remember { mutableStateListOf<Path>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var saveToCatalog by remember { mutableStateOf(true) }
    var signatureTitle by remember(signerName) { mutableStateOf("Firma Pantalla ($signerName)") }
    var isFullscreen by remember { mutableStateOf(false) }

    val hasDrawn = completedStrokes.isNotEmpty() || currentPoints.isNotEmpty()

    // Diálogo inmersivo de Pantalla Completa
    if (isFullscreen) {
        FullScreenSignatureDialog(
            signerName = signerName,
            completedStrokes = completedStrokes,
            onClose = { isFullscreen = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Draw,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Firma Táctil en Pantalla",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Firmante: $signerName",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Pantalla Completa
                OutlinedButton(
                    onClick = { isFullscreen = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Pantalla Completa",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Ampliar", fontSize = 10.5.sp)
                }

                // Botón Deshacer último trazo (Retroceso)
                OutlinedButton(
                    onClick = {
                        if (completedStrokes.isNotEmpty()) {
                            completedStrokes.removeAt(completedStrokes.size - 1)
                        }
                    },
                    enabled = completedStrokes.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Deshacer trazo",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Deshacer", fontSize = 10.5.sp)
                }

                // Botón Limpiar todo
                TextButton(
                    onClick = {
                        completedStrokes.clear()
                        currentPoints = emptyList()
                    },
                    enabled = hasDrawn,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Limpiar", fontSize = 10.5.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Lienzo táctil interactivo en tiempo real con cuadrícula de transparencia y trazado fluido
        SignatureCanvasBox(
            completedStrokes = completedStrokes,
            currentPoints = currentPoints,
            onCurrentPointsChange = { currentPoints = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Opción para guardar la firma para usos posteriores
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { saveToCatalog = !saveToCatalog },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveToCatalog,
                        onCheckedChange = { saveToCatalog = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Guardar firma para usos posteriores",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Se guardará cifrada en tu catálogo para reutilizarla",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (saveToCatalog) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = signatureTitle,
                        onValueChange = { signatureTitle = it },
                        label = { Text("Nombre / Etiqueta de la firma", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Text("Cancelar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = {
                    if (completedStrokes.isNotEmpty()) {
                        val savedPath = saveSignatureBitmap(context, completedStrokes.toList())
                        if (savedPath != null) {
                            onSignatureCaptured(savedPath, saveToCatalog, signatureTitle)
                        }
                    }
                },
                enabled = completedStrokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = com.alfas.amortizacioncredito.ui.theme.GreenSuccess),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(46.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isStandaloneVault || saveToCatalog) "Guardar en Bóveda" else "Aceptar Firma",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Diálogo inmersivo a Pantalla Completa para permitir al usuario trazar firmas cómodamente con máximo espacio.
 */
@Composable
private fun FullScreenSignatureDialog(
    signerName: String,
    completedStrokes: SnapshotStateList<Path>,
    onClose: () -> Unit
) {
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val hasDrawn = completedStrokes.isNotEmpty() || currentPoints.isNotEmpty()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(12.dp)
            ) {
                // Barra superior de herramientas en pantalla completa
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.FullscreenExit,
                                contentDescription = "Salir de pantalla completa",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "✍️ Firma en Pantalla Completa",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Firmante: $signerName • Espacio ampliado (Puedes girar tu teléfono)",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón Deshacer
                        OutlinedButton(
                            onClick = {
                                if (completedStrokes.isNotEmpty()) {
                                    completedStrokes.removeAt(completedStrokes.size - 1)
                                }
                            },
                            enabled = completedStrokes.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Deshacer trazo",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deshacer", fontSize = 11.sp)
                        }

                        // Botón Limpiar
                        TextButton(
                            onClick = {
                                completedStrokes.clear()
                                currentPoints = emptyList()
                            },
                            enabled = hasDrawn,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Limpiar", fontSize = 11.sp)
                        }

                        // Botón Listo / Aplicar
                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(containerColor = com.alfas.amortizacioncredito.ui.theme.GreenSuccess),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Listo", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Lienzo táctil gigante que ocupa todo el resto de la pantalla
                SignatureCanvasBox(
                    completedStrokes = completedStrokes,
                    currentPoints = currentPoints,
                    onCurrentPointsChange = { currentPoints = it },
                    isFullScreen = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Componente de lienzo de dibujo con cuadrícula de transparencia, línea de base y trazado reactivo en tiempo real a 120Hz.
 */
@Composable
private fun SignatureCanvasBox(
    completedStrokes: SnapshotStateList<Path>,
    currentPoints: List<Offset>,
    onCurrentPointsChange: (List<Offset>) -> Unit,
    isFullScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasDrawn = completedStrokes.isNotEmpty() || currentPoints.isNotEmpty()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .drawBehind {
                val squareSize = if (isFullScreen) 10.dp.toPx() else 8.dp.toPx()
                val cols = (size.width / squareSize).toInt() + 1
                val rows = (size.height / squareSize).toInt() + 1
                val checkColor = Slate100
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if ((r + c) % 2 == 1) {
                            drawRect(
                                color = checkColor,
                                topLeft = Offset(c * squareSize, r * squareSize),
                                size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
                            )
                        }
                    }
                }
            }
            .border(
                1.5.dp,
                if (hasDrawn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val points = mutableListOf(down.position)
                        onCurrentPointsChange(points.toList())

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                change.consume()
                                points.add(change.position)
                                onCurrentPointsChange(points.toList())
                            } else {
                                change.consume()
                                break
                            }
                        }

                        // Al soltar el trazo, consolidar en Path suavizado con curvas Bézier
                        if (points.isNotEmpty()) {
                            val strokePath = Path()
                            strokePath.moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                val p0 = points[i - 1]
                                val p1 = points[i]
                                strokePath.quadraticTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                            }
                            if (points.size > 1) {
                                val last = points.last()
                                strokePath.lineTo(last.x, last.y)
                            }
                            completedStrokes.add(strokePath)
                        }
                        onCurrentPointsChange(emptyList())
                    }
                }
        ) {
            // Línea guía de firma
            val guideY = if (isFullScreen) size.height - 45f else size.height - 26f
            drawLine(
                color = Slate400.copy(alpha = 0.45f),
                start = Offset(x = 24f, y = guideY),
                end = Offset(x = size.width - 24f, y = guideY),
                strokeWidth = if (isFullScreen) 2f else 1.5f
            )

            val strokeWidthVal = if (isFullScreen) 5.5f else 4.5f

            // 1. Trazos completados
            for (p in completedStrokes) {
                drawPath(
                    path = p,
                    color = Slate900,
                    style = Stroke(width = strokeWidthVal, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 2. Trazo activo en tiempo real a 120Hz
            if (currentPoints.isNotEmpty()) {
                val livePath = Path()
                livePath.moveTo(currentPoints[0].x, currentPoints[0].y)
                for (i in 1 until currentPoints.size) {
                    val p0 = currentPoints[i - 1]
                    val p1 = currentPoints[i]
                    livePath.quadraticTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                }
                if (currentPoints.size > 1) {
                    val last = currentPoints.last()
                    livePath.lineTo(last.x, last.y)
                }
                drawPath(
                    path = livePath,
                    color = Slate900,
                    style = Stroke(width = strokeWidthVal, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        if (!hasDrawn) {
            Text(
                text = if (isFullScreen) "✍️ Dibuja libremente tu firma aquí... (Gira la pantalla si deseas mayor anchura)" else "✍️ Dibuja tu firma aquí...",
                color = Slate400,
                fontSize = if (isFullScreen) 14.sp else 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun saveSignatureBitmap(
    context: Context,
    paths: List<Path>
): String? {
    return try {
        if (paths.isEmpty()) return null

        val combinedAndroidPath = android.graphics.Path()
        for (p in paths) {
            combinedAndroidPath.addPath(p.asAndroidPath())
        }

        val bounds = RectF()
        combinedAndroidPath.computeBounds(bounds, true)

        val padding = 20f
        val strokeWidth = 8f

        // Bounding box calculado con los trazos reales y margen de seguridad
        val left = bounds.left - strokeWidth - padding
        val top = bounds.top - strokeWidth - padding
        val right = bounds.right + strokeWidth + padding
        val bottom = bounds.bottom + strokeWidth + padding

        val targetWidth = (right - left).toInt().coerceAtLeast(100)
        val targetHeight = (bottom - top).toInt().coerceAtLeast(50)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)

        val paint = Paint().apply {
            color = android.graphics.Color.argb(255, 15, 23, 42)
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // Trasladar la matriz para que la firma quede perfectamente encuadrada y centrada
        canvas.translate(-left, -top)
        canvas.drawPath(combinedAndroidPath, paint)

        // Guardar de forma ultra-segura cifrada con AES-256-GCM y verificación SHA-256
        val result = com.alfas.amortizacioncredito.domain.security.SignatureSecurityVault.saveEncryptedSignature(
            context = context,
            bitmap = bitmap,
            fileNamePrefix = "sig_pad"
        )
        result?.first
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
