package com.alfas.amortizacioncredito.ui.dialogs.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.alfas.amortizacioncredito.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Libre", null),
    SQUARE("1:1", 1f),
    DOC_RECEIPT("Recibo (3:4)", 0.75f),
    PHOTO_4_3("4:3", 1.333f),
    WIDE_16_9("16:9", 1.777f)
}

/**
 * Diálogo interactivo para recortar y rotar imágenes seleccionadas desde la cámara o galería.
 */
@Composable
fun ImageCropDialog(
    sourceUri: Uri,
    title: String = "Recortar Imagen",
    subtitle: String = "Ajusta los bordes para encuadrar la zona deseada",
    onCropSuccess: (croppedUri: Uri, croppedBitmap: Bitmap) -> Unit,
    onUseOriginal: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var selectedRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var isCropping by remember { mutableStateOf(false) }

    // Posición del marco de recorte en coordenadas normalizadas [0f..1f]
    var cropRectNorm by remember {
        mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f))
    }

    // Cargar imagen con orientación EXIF corregida
    LaunchedEffect(sourceUri, rotationDegrees) {
        val bmp = withContext(Dispatchers.IO) {
            decodeAndOrientBitmap(context, sourceUri, rotationDegrees)
        }
        loadedBitmap = bmp
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = Slate900,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Barra Superior: Título + Rotar + Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Crop,
                                contentDescription = null,
                                tint = CreditHomeSky,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = subtitle,
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { rotationDegrees = (rotationDegrees + 90) % 360 }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = "Girar 90°",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                cropRectNorm = Rect(0.05f, 0.05f, 0.95f, 0.95f)
                                rotationDegrees = 0
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reiniciar",
                                tint = Slate400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Slate400,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Selector de Proporciones (Pills horizontales)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CropAspectRatio.entries.forEach { ratioItem ->
                        val isSelected = selectedRatio == ratioItem
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedRatio = ratioItem
                                val r = ratioItem.ratio
                                if (r != null) {
                                    // Ajustar el marco según la relación de aspecto
                                    val currentWidth = cropRectNorm.width
                                    val newHeight = (currentWidth / r).coerceIn(0.1f, 0.9f)
                                    val top = (0.5f - newHeight / 2f).coerceAtLeast(0.02f)
                                    val bottom = (top + newHeight).coerceAtMost(0.98f)
                                    cropRectNorm = Rect(cropRectNorm.left, top, cropRectNorm.right, bottom)
                                }
                            },
                            label = {
                                Text(
                                    ratioItem.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SkyBlueAction,
                                selectedLabelColor = Color.White,
                                containerColor = Slate800,
                                labelColor = Slate300
                            )
                        )
                    }
                }

                // Lienzo Interactivo de Recorte
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadedBitmap != null) {
                        InteractiveCropCanvas(
                            bitmap = loadedBitmap!!,
                            cropRectNorm = cropRectNorm,
                            onCropRectChange = { cropRectNorm = it },
                            aspectRatio = selectedRatio.ratio
                        )
                    } else {
                        CircularProgressIndicator(
                            color = CreditHomeSky,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra Inferior de Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onUseOriginal,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300)
                    ) {
                        Text("Usar Original", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val bmp = loadedBitmap ?: return@Button
                            isCropping = true
                            coroutineScope.launch {
                                val cropped = withContext(Dispatchers.IO) {
                                    cropBitmap(context, bmp, cropRectNorm)
                                }
                                isCropping = false
                                if (cropped != null) {
                                    onCropSuccess(cropped.first, cropped.second)
                                } else {
                                    onUseOriginal()
                                }
                            }
                        },
                        enabled = !isCropping && loadedBitmap != null,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        if (isCropping) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recortando...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Confirmar Recorte", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private enum class CropDragHandle {
    NONE,
    MOVE_BOX,
    CORNER_TOP_LEFT,
    CORNER_TOP_RIGHT,
    CORNER_BOTTOM_RIGHT,
    CORNER_BOTTOM_LEFT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT
}

/**
 * Canvas interactivo de alta precisión para recortar por esquinas, bordes y mover el encuadre.
 */
@Composable
private fun InteractiveCropCanvas(
    bitmap: Bitmap,
    cropRectNorm: Rect,
    onCropRectChange: (Rect) -> Unit,
    aspectRatio: Float?
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Calcular escala y bounds de la imagen dentro del canvas (Letterboxed / Fit)
    val imageRect = remember(canvasSize, bitmap) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
            Rect.Zero
        } else {
            val scale = min(
                canvasSize.width / bitmap.width.toFloat(),
                canvasSize.height / bitmap.height.toFloat()
            )
            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale
            val left = (canvasSize.width - drawW) / 2f
            val top = (canvasSize.height - drawH) / 2f
            Rect(left, top, left + drawW, top + drawH)
        }
    }

    val currentCropRect by rememberUpdatedState(cropRectNorm)
    val currentAspectRatio by rememberUpdatedState(aspectRatio)
    val currentOnCropRectChange by rememberUpdatedState(onCropRectChange)
    val currentImageRect by rememberUpdatedState(imageRect)

    var activeHandle by remember { mutableStateOf(CropDragHandle.NONE) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                canvasSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val imgRect = currentImageRect
                    if (imgRect.isEmpty) return@awaitEachGesture

                    var currentBox = currentCropRect
                    val curLeft = imgRect.left + currentBox.left * imgRect.width
                    val curTop = imgRect.top + currentBox.top * imgRect.height
                    val curRight = imgRect.left + currentBox.right * imgRect.width
                    val curBottom = imgRect.top + currentBox.bottom * imgRect.height

                    // Zonas de toque táctil generosas para dedos móviles (48dp esquinas, 32dp bordes)
                    val cornerRadius = 48.dp.toPx()
                    val edgeThickness = 32.dp.toPx()
                    val marginExtension = 20.dp.toPx()

                    val p = down.position
                    val dTL = (p - Offset(curLeft, curTop)).getDistance()
                    val dTR = (p - Offset(curRight, curTop)).getDistance()
                    val dBR = (p - Offset(curRight, curBottom)).getDistance()
                    val dBL = (p - Offset(curLeft, curBottom)).getDistance()
                    val minDistCorner = minOf(dTL, dTR, dBR, dBL)

                    val handle = if (minDistCorner <= cornerRadius) {
                        when (minDistCorner) {
                            dTL -> CropDragHandle.CORNER_TOP_LEFT
                            dTR -> CropDragHandle.CORNER_TOP_RIGHT
                            dBR -> CropDragHandle.CORNER_BOTTOM_RIGHT
                            else -> CropDragHandle.CORNER_BOTTOM_LEFT
                        }
                    } else {
                        val inHoriz = p.x in (curLeft - marginExtension)..(curRight + marginExtension)
                        val inVert = p.y in (curTop - marginExtension)..(curBottom + marginExtension)

                        val distTop = if (inHoriz) abs(p.y - curTop) else Float.MAX_VALUE
                        val distBottom = if (inHoriz) abs(p.y - curBottom) else Float.MAX_VALUE
                        val distLeft = if (inVert) abs(p.x - curLeft) else Float.MAX_VALUE
                        val distRight = if (inVert) abs(p.x - curRight) else Float.MAX_VALUE
                        val minDistEdge = minOf(distTop, distBottom, distLeft, distRight)

                        if (minDistEdge <= edgeThickness) {
                            when (minDistEdge) {
                                distTop -> CropDragHandle.EDGE_TOP
                                distBottom -> CropDragHandle.EDGE_BOTTOM
                                distLeft -> CropDragHandle.EDGE_LEFT
                                else -> CropDragHandle.EDGE_RIGHT
                            }
                        } else if (p.x in curLeft..curRight && p.y in curTop..curBottom) {
                            CropDragHandle.MOVE_BOX
                        } else {
                            CropDragHandle.NONE
                        }
                    }

                    if (handle == CropDragHandle.NONE) return@awaitEachGesture

                    activeHandle = handle
                    down.consume()
                    var lastPosition = p

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()

                        val currentPos = change.position
                        val dragDelta = currentPos - lastPosition
                        lastPosition = currentPos

                        val normDx = dragDelta.x / imgRect.width
                        val normDy = dragDelta.y / imgRect.height

                        var left = currentBox.left
                        var top = currentBox.top
                        var right = currentBox.right
                        var bottom = currentBox.bottom

                        val minSize = 0.05f
                        val aspRatio = currentAspectRatio

                        if (aspRatio == null) {
                            // MODO LIBRE: Ajuste independiente de esquinas y bordes
                            when (handle) {
                                CropDragHandle.MOVE_BOX -> {
                                    val w = right - left
                                    val h = bottom - top
                                    val newLeft = (left + normDx).coerceIn(0f, (1f - w).coerceAtLeast(0f))
                                    val newTop = (top + normDy).coerceIn(0f, (1f - h).coerceAtLeast(0f))
                                    left = newLeft
                                    right = newLeft + w
                                    top = newTop
                                    bottom = newTop + h
                                }
                                CropDragHandle.CORNER_TOP_LEFT -> {
                                    left = (left + normDx).coerceIn(0f, right - minSize)
                                    top = (top + normDy).coerceIn(0f, bottom - minSize)
                                }
                                CropDragHandle.CORNER_TOP_RIGHT -> {
                                    right = (right + normDx).coerceIn(left + minSize, 1f)
                                    top = (top + normDy).coerceIn(0f, bottom - minSize)
                                }
                                CropDragHandle.CORNER_BOTTOM_RIGHT -> {
                                    right = (right + normDx).coerceIn(left + minSize, 1f)
                                    bottom = (bottom + normDy).coerceIn(top + minSize, 1f)
                                }
                                CropDragHandle.CORNER_BOTTOM_LEFT -> {
                                    left = (left + normDx).coerceIn(0f, right - minSize)
                                    bottom = (bottom + normDy).coerceIn(top + minSize, 1f)
                                }
                                CropDragHandle.EDGE_TOP -> {
                                    top = (top + normDy).coerceIn(0f, bottom - minSize)
                                }
                                CropDragHandle.EDGE_BOTTOM -> {
                                    bottom = (bottom + normDy).coerceIn(top + minSize, 1f)
                                }
                                CropDragHandle.EDGE_LEFT -> {
                                    left = (left + normDx).coerceIn(0f, right - minSize)
                                }
                                CropDragHandle.EDGE_RIGHT -> {
                                    right = (right + normDx).coerceIn(left + minSize, 1f)
                                }
                                CropDragHandle.NONE -> {}
                            }
                        } else {
                            // MODO PROPORCIÓN FIJA (Aspect Ratio bloqueado)
                            when (handle) {
                                CropDragHandle.MOVE_BOX -> {
                                    val w = right - left
                                    val h = bottom - top
                                    val newLeft = (left + normDx).coerceIn(0f, (1f - w).coerceAtLeast(0f))
                                    val newTop = (top + normDy).coerceIn(0f, (1f - h).coerceAtLeast(0f))
                                    left = newLeft
                                    right = newLeft + w
                                    top = newTop
                                    bottom = newTop + h
                                }
                                CropDragHandle.CORNER_BOTTOM_RIGHT -> {
                                    val candidateRight = (right + normDx).coerceIn(left + minSize, 1f)
                                    val wPx = (candidateRight - left) * imgRect.width
                                    val hPx = wPx / aspRatio
                                    val targetBottom = top + (hPx / imgRect.height)
                                    if (targetBottom <= 1f) {
                                        right = candidateRight
                                        bottom = targetBottom
                                    } else {
                                        val maxHPx = (1f - top) * imgRect.height
                                        val maxWPx = maxHPx * aspRatio
                                        right = (left + maxWPx / imgRect.width).coerceIn(left + minSize, 1f)
                                        bottom = 1f
                                    }
                                }
                                CropDragHandle.CORNER_BOTTOM_LEFT -> {
                                    val candidateLeft = (left + normDx).coerceIn(0f, right - minSize)
                                    val wPx = (right - candidateLeft) * imgRect.width
                                    val hPx = wPx / aspRatio
                                    val targetBottom = top + (hPx / imgRect.height)
                                    if (targetBottom <= 1f) {
                                        left = candidateLeft
                                        bottom = targetBottom
                                    } else {
                                        val maxHPx = (1f - top) * imgRect.height
                                        val maxWPx = maxHPx * aspRatio
                                        left = (right - maxWPx / imgRect.width).coerceIn(0f, right - minSize)
                                        bottom = 1f
                                    }
                                }
                                CropDragHandle.CORNER_TOP_RIGHT -> {
                                    val candidateRight = (right + normDx).coerceIn(left + minSize, 1f)
                                    val wPx = (candidateRight - left) * imgRect.width
                                    val hPx = wPx / aspRatio
                                    val targetTop = bottom - (hPx / imgRect.height)
                                    if (targetTop >= 0f) {
                                        right = candidateRight
                                        top = targetTop
                                    } else {
                                        val maxHPx = bottom * imgRect.height
                                        val maxWPx = maxHPx * aspRatio
                                        right = (left + maxWPx / imgRect.width).coerceIn(left + minSize, 1f)
                                        top = 0f
                                    }
                                }
                                CropDragHandle.CORNER_TOP_LEFT -> {
                                    val candidateLeft = (left + normDx).coerceIn(0f, right - minSize)
                                    val wPx = (right - candidateLeft) * imgRect.width
                                    val hPx = wPx / aspRatio
                                    val targetTop = bottom - (hPx / imgRect.height)
                                    if (targetTop >= 0f) {
                                        left = candidateLeft
                                        top = targetTop
                                    } else {
                                        val maxHPx = bottom * imgRect.height
                                        val maxWPx = maxHPx * aspRatio
                                        right = (right - maxWPx / imgRect.width).coerceIn(0f, right - minSize)
                                        top = 0f
                                    }
                                }
                                CropDragHandle.EDGE_TOP, CropDragHandle.EDGE_BOTTOM -> {
                                    val deltaY = if (handle == CropDragHandle.EDGE_TOP) -normDy else normDy
                                    val curH = bottom - top
                                    val newH = (curH + deltaY).coerceIn(minSize, 1f)
                                    val newHPx = newH * imgRect.height
                                    val newWPx = newHPx * aspRatio
                                    val newNormW = (newWPx / imgRect.width).coerceIn(minSize, 1f)
                                    val centerX = (left + right) / 2f
                                    val newL = (centerX - newNormW / 2f).coerceIn(0f, 1f - newNormW)
                                    left = newL
                                    right = newL + newNormW
                                    if (handle == CropDragHandle.EDGE_TOP) {
                                        top = (bottom - newH).coerceAtLeast(0f)
                                    } else {
                                        bottom = (top + newH).coerceAtMost(1f)
                                    }
                                }
                                CropDragHandle.EDGE_LEFT, CropDragHandle.EDGE_RIGHT -> {
                                    val deltaX = if (handle == CropDragHandle.EDGE_LEFT) -normDx else normDx
                                    val curW = right - left
                                    val newW = (curW + deltaX).coerceIn(minSize, 1f)
                                    val newWPx = newW * imgRect.width
                                    val newHPx = newWPx / aspRatio
                                    val newNormH = (newHPx / imgRect.height).coerceIn(minSize, 1f)
                                    val centerY = (top + bottom) / 2f
                                    val newT = (centerY - newNormH / 2f).coerceIn(0f, 1f - newNormH)
                                    top = newT
                                    bottom = newT + newNormH
                                    if (handle == CropDragHandle.EDGE_LEFT) {
                                        left = (right - newW).coerceAtLeast(0f)
                                    } else {
                                        right = (left + newW).coerceAtMost(1f)
                                    }
                                }
                                CropDragHandle.NONE -> {}
                            }
                        }

                        currentBox = Rect(
                            left.coerceIn(0f, 1f),
                            top.coerceIn(0f, 1f),
                            right.coerceIn(0f, 1f),
                            bottom.coerceIn(0f, 1f)
                        )
                        currentOnCropRectChange(currentBox)
                    }

                    activeHandle = CropDragHandle.NONE
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageRect.isEmpty) return@Canvas

            // 1. Dibujar Imagen
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt()),
                dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
            )

            // Coordenadas absolutas de la caja de recorte
            val boxLeft = imageRect.left + cropRectNorm.left * imageRect.width
            val boxTop = imageRect.top + cropRectNorm.top * imageRect.height
            val boxRight = imageRect.left + cropRectNorm.right * imageRect.width
            val boxBottom = imageRect.top + cropRectNorm.bottom * imageRect.height
            val boxWidth = boxRight - boxLeft
            val boxHeight = boxBottom - boxTop

            // 2. Máscara oscura exterior envolvente
            val darkOverlay = BlackScrim
            // Superior
            drawRect(darkOverlay, Offset(0f, 0f), Size(size.width, boxTop))
            // Inferior
            drawRect(darkOverlay, Offset(0f, boxBottom), Size(size.width, size.height - boxBottom))
            // Izquierda
            drawRect(darkOverlay, Offset(0f, boxTop), Size(boxLeft, boxHeight))
            // Derecha
            drawRect(darkOverlay, Offset(boxRight, boxTop), Size(size.width - boxRight, boxHeight))

            // 3. Borde de la Caja de Recorte
            drawRect(
                color = CreditHomeSky,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. Guías de Tercios (Grid interior)
            val gridColor = WhiteOverlayLight
            val gridStroke = Stroke(width = 1.dp.toPx())
            drawLine(gridColor, Offset(boxLeft + boxWidth / 3f, boxTop), Offset(boxLeft + boxWidth / 3f, boxBottom), gridStroke.width)
            drawLine(gridColor, Offset(boxLeft + 2 * boxWidth / 3f, boxTop), Offset(boxLeft + 2 * boxWidth / 3f, boxBottom), gridStroke.width)
            drawLine(gridColor, Offset(boxLeft, boxTop + boxHeight / 3f), Offset(boxRight, boxTop + boxHeight / 3f), gridStroke.width)
            drawLine(gridColor, Offset(boxLeft, boxTop + 2 * boxHeight / 3f), Offset(boxRight, boxTop + 2 * boxHeight / 3f), gridStroke.width)

            // 5. Asas / Esquinas de agarre gruesas y visibles
            val cornerLen = 22.dp.toPx()
            val cornerStroke = 4.5.dp.toPx()
            val cornerColor = Color.White

            // TopLeft
            drawLine(cornerColor, Offset(boxLeft - 2, boxTop), Offset(boxLeft + cornerLen, boxTop), cornerStroke)
            drawLine(cornerColor, Offset(boxLeft, boxTop - 2), Offset(boxLeft, boxTop + cornerLen), cornerStroke)
            drawCircle(CreditHomeSky, radius = 5.dp.toPx(), center = Offset(boxLeft, boxTop))
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(boxLeft, boxTop))

            // TopRight
            drawLine(cornerColor, Offset(boxRight + 2, boxTop), Offset(boxRight - cornerLen, boxTop), cornerStroke)
            drawLine(cornerColor, Offset(boxRight, boxTop - 2), Offset(boxRight, boxTop + cornerLen), cornerStroke)
            drawCircle(CreditHomeSky, radius = 5.dp.toPx(), center = Offset(boxRight, boxTop))
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(boxRight, boxTop))

            // BottomRight
            drawLine(cornerColor, Offset(boxRight + 2, boxBottom), Offset(boxRight - cornerLen, boxBottom), cornerStroke)
            drawLine(cornerColor, Offset(boxRight, boxBottom + 2), Offset(boxRight, boxBottom - cornerLen), cornerStroke)
            drawCircle(CreditHomeSky, radius = 5.dp.toPx(), center = Offset(boxRight, boxBottom))
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(boxRight, boxBottom))

            // BottomLeft
            drawLine(cornerColor, Offset(boxLeft - 2, boxBottom), Offset(boxLeft + cornerLen, boxBottom), cornerStroke)
            drawLine(cornerColor, Offset(boxLeft, boxBottom + 2), Offset(boxLeft, boxBottom - cornerLen), cornerStroke)
            drawCircle(CreditHomeSky, radius = 5.dp.toPx(), center = Offset(boxLeft, boxBottom))
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(boxLeft, boxBottom))

            // 6. Indicadores sutiles en el centro de los 4 bordes
            val edgePillLen = 16.dp.toPx()
            val edgePillStroke = 3.dp.toPx()
            val edgePillColor = Color.White.copy(alpha = 0.85f)
            // Top edge
            drawLine(edgePillColor, Offset(boxLeft + boxWidth / 2f - edgePillLen / 2f, boxTop), Offset(boxLeft + boxWidth / 2f + edgePillLen / 2f, boxTop), edgePillStroke)
            // Bottom edge
            drawLine(edgePillColor, Offset(boxLeft + boxWidth / 2f - edgePillLen / 2f, boxBottom), Offset(boxLeft + boxWidth / 2f + edgePillLen / 2f, boxBottom), edgePillStroke)
            // Left edge
            drawLine(edgePillColor, Offset(boxLeft, boxTop + boxHeight / 2f - edgePillLen / 2f), Offset(boxLeft, boxTop + boxHeight / 2f + edgePillLen / 2f), edgePillStroke)
            // Right edge
            drawLine(edgePillColor, Offset(boxRight, boxTop + boxHeight / 2f - edgePillLen / 2f), Offset(boxRight, boxTop + boxHeight / 2f + edgePillLen / 2f), edgePillStroke)
        }
    }
}

/**
 * Diálogo rápido para seleccionar entre Cámara o Galería de Fotos.
 */
@Composable
fun ImageSourceSelectorSheet(
    title: String = "Adjuntar Imagen",
    subtitle: String = "Selecciona el origen de la foto o comprobante",
    onSelectCamera: () -> Unit,
    onSelectGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Opción 1: Cámara
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                        onSelectCamera()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CreditHomeBlue,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tomar Foto con Cámara", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Captura una nueva foto del documento o firma", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Opción 2: Galería
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                        onSelectGallery()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PurpleAccent,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Elegir de la Galería", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Selecciona una imagen guardada en tu dispositivo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

/**
 * Decodifica un Bitmap corrigiendo la orientación EXIF y aplicando rotación manual.
 */
private fun decodeAndOrientBitmap(context: Context, uri: Uri, extraRotation: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        // Downsample max 1600px para fluidez interactiva en el recorte
        var inSampleSize = 1
        val reqDim = 1600
        if (options.outHeight > reqDim || options.outWidth > reqDim) {
            val halfH = options.outHeight / 2
            val halfW = options.outWidth / 2
            while ((halfH / inSampleSize) >= reqDim && (halfW / inSampleSize) >= reqDim) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        // Detectar orientación EXIF
        val exifOrientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val exifRotation = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        val totalRotation = (exifRotation + extraRotation) % 360
        if (totalRotation != 0) {
            val matrix = Matrix().apply { postRotate(totalRotation.toFloat()) }
            Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
        } else {
            rawBmp
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Recorta la sub-región indicada del bitmap y la guarda en un archivo temporal de caché.
 */
private fun cropBitmap(
    context: Context,
    bitmap: Bitmap,
    cropRectNorm: Rect
): Pair<Uri, Bitmap>? {
    return try {
        val cropX = (cropRectNorm.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val cropY = (cropRectNorm.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val cropW = (cropRectNorm.width * bitmap.width).toInt().coerceIn(1, bitmap.width - cropX)
        val cropH = (cropRectNorm.height * bitmap.height).toInt().coerceIn(1, bitmap.height - cropY)

        val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)

        // Guardar en archivo temporal de caché como PNG sin pérdidas
        val tempFile = File(context.cacheDir, "crop_${System.currentTimeMillis()}.png")
        FileOutputStream(tempFile).use { out ->
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        Pair(uri, croppedBitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
