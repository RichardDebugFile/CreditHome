package com.alfas.amortizacioncredito.ui.dialogs.business

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alfas.amortizacioncredito.data.model.SavedSignatureItem
import com.alfas.amortizacioncredito.data.repository.SavedSignatureRepository
import androidx.core.content.FileProvider
import com.alfas.amortizacioncredito.domain.security.P12CertInfo
import com.alfas.amortizacioncredito.domain.security.P12SignatureHelper
import com.alfas.amortizacioncredito.domain.security.SignatureSecurityVault
import com.alfas.amortizacioncredito.ui.dialogs.common.ImageCropDialog
import com.alfas.amortizacioncredito.ui.dialogs.common.ImageSourceSelectorSheet
import com.alfas.amortizacioncredito.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

enum class SignatureTab(val title: String, val shortTitle: String, val icon: ImageVector) {
    SAVED("Mis Firmas", "Guardadas", Icons.Default.Bookmarks),
    TACTIL("En Pantalla", "Pantalla", Icons.Default.Draw),
    P12_ECUADOR("Certificado .p12", "Certificado", Icons.Default.Key),
    FISICA_FOTO("Foto / Escaneo", "Escanear", Icons.Default.CameraAlt)
}

@Composable
fun SignatureCaptureDialog(
    signerName: String = "Administrador",
    voucherInfo: String = "",
    isStandaloneVault: Boolean = false,
    onSignatureSuccess: (signaturePath: String, signatureType: String, signer: String, date: String, details: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val p12Helper = remember { P12SignatureHelper(context) }
    val signatureRepo = remember { SavedSignatureRepository(context) }
    var savedSignatures by remember { mutableStateOf(signatureRepo.getAllSignatures()) }

    var selectedTab by remember {
        mutableStateOf(if (savedSignatures.isNotEmpty()) SignatureTab.SAVED else SignatureTab.TACTIL)
    }

    val currentFormattedDate = remember {
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }

    // Estado para vista previa de firma
    var previewSignatureItem by remember { mutableStateOf<SavedSignatureItem?>(null) }

    // Estado para pestaña P12
    var p12Uri by remember { mutableStateOf<Uri?>(null) }
    var p12FileName by remember { mutableStateOf("") }
    var p12Password by remember { mutableStateOf("") }
    var isP12PasswordVisible by remember { mutableStateOf(false) }
    var isVerifyingP12 by remember { mutableStateOf(false) }
    var p12CertInfo by remember { mutableStateOf<P12CertInfo?>(null) }
    var p12Error by remember { mutableStateOf<String?>(null) }

    // Launcher para archivo .p12/.pfx
    val p12PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            p12Uri = uri
            p12FileName = "Certificado_PKCS12.p12"
            p12CertInfo = null
            p12Error = null
        }
    }

    // Launcher para foto de firma física
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var removePhotoBackground by remember { mutableStateOf(true) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var savePhotoForFutureUse by remember { mutableStateOf(true) }
    var photoCustomTitle by remember(signerName) { mutableStateOf("Firma Escaneada ($signerName)") }

    var saveP12ForFutureUse by remember { mutableStateOf(true) }
    var p12CustomTitle by remember { mutableStateOf("") }
    var signatureToDelete by remember { mutableStateOf<SavedSignatureItem?>(null) }

    // Estados para selector de fuente (Cámara/Galería) y Recorte de firma
    var showPhotoSourcePicker by remember { mutableStateOf(false) }
    var pendingCropPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropPhotoUri = uri
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraPhotoUri != null) {
            pendingCropPhotoUri = tempCameraPhotoUri
        }
    }

    fun launchCameraForSignature() {
        try {
            val tempFile = File(context.cacheDir, "camera_sig_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            tempCameraPhotoUri = uri
            cameraPhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al abrir la cámara: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraForSignature()
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara para capturar la firma", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndLaunchCameraForSignature() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchCameraForSignature()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var photoPreviewBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoadingPhotoPreview by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPhotoUri) {
        val uri = selectedPhotoUri
        if (uri != null) {
            isLoadingPhotoPreview = true
            val bmp = withContext(Dispatchers.IO) {
                try {
                    decodeSampledBitmapFromUri(context, uri, 600, 300)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
            photoPreviewBmp = bmp
            isLoadingPhotoPreview = false
        } else {
            photoPreviewBmp = null
            isLoadingPhotoPreview = false
        }
    }

    // Selector de Cámara o Galería para Foto de Firma
    if (showPhotoSourcePicker) {
        ImageSourceSelectorSheet(
            title = "Foto de Firma en Papel",
            subtitle = "Selecciona cómo deseas capturar la firma manuscrita",
            onSelectCamera = { checkAndLaunchCameraForSignature() },
            onSelectGallery = { galleryPhotoPickerLauncher.launch("image/*") },
            onDismiss = { showPhotoSourcePicker = false }
        )
    }

    // Modal de Recorte de Firma
    if (pendingCropPhotoUri != null) {
        ImageCropDialog(
            sourceUri = pendingCropPhotoUri!!,
            title = "Recortar Área de Firma",
            subtitle = "Encuadra únicamente el trazo manuscrito en el papel",
            onCropSuccess = { croppedUri, _ ->
                selectedPhotoUri = croppedUri
                pendingCropPhotoUri = null
            },
            onUseOriginal = {
                selectedPhotoUri = pendingCropPhotoUri
                pendingCropPhotoUri = null
            },
            onDismiss = { pendingCropPhotoUri = null }
        )
    }

    // Modal de carga pesada "Cargando... Por favor espere"
    if (isProcessingPhoto || isVerifyingP12) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                        .fillMaxWidth(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = if (isProcessingPhoto) "Procesando firma..." else "Verificando certificado .p12...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cargando... Por favor espere",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Modal de Vista Previa de Firma
    if (previewSignatureItem != null) {
        val previewItem = previewSignatureItem!!
        SignaturePreviewDialog(
            item = previewItem,
            isStandaloneVault = isStandaloneVault,
            onStamp = {
                if (previewItem.signatureType == "ELECTRONIC_P12" && previewItem.p12VaultPath.isNotBlank() && previewItem.p12Password.isNotBlank()) {
                    try {
                        val p12Bytes = SignatureSecurityVault.loadDecryptedBytes(previewItem.p12VaultPath)
                        if (p12Bytes != null) {
                            p12Helper.extractP12CredentialsFromBytes(p12Bytes, previewItem.p12Password).onSuccess { creds ->
                                P12SignatureHelper.activeCredentials = creds
                            }
                        }
                    } catch (_: Exception) {}
                }
                val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(previewItem.createdAt))
                previewSignatureItem = null
                onSignatureSuccess(
                    previewItem.filePath,
                    previewItem.signatureType,
                    previewItem.signerName,
                    dateStr,
                    previewItem.details.ifBlank { "Firma guardada: ${previewItem.title}" }
                )
            },
            onDelete = {
                signatureToDelete = previewItem
                previewSignatureItem = null
            },
            onDismiss = {
                previewSignatureItem = null
            }
        )
    }

    // Modal de confirmación para eliminar firma
    if (signatureToDelete != null) {
        AlertDialog(
            onDismissRequest = { signatureToDelete = null },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "¿Eliminar Firma Guardada?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar permanentemente la firma '${signatureToDelete?.title ?: ""}' de tu bóveda de firmas?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        signatureToDelete?.let { sig ->
                            signatureRepo.deleteSignature(sig.id)
                            savedSignatures = signatureRepo.getAllSignatures()
                        }
                        signatureToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { signatureToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Firmar Comprobante Oficial", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Bóveda segura con cifrado hardware AES-256", style = MaterialTheme.typography.bodySmall, color = GreenSuccess, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector de Pestañas elegante con Scroll Horizontal y Pills Claros
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SignatureTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val activeBg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            val activeTextColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                            Surface(
                                color = activeBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable { selectedTab = tab }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = activeTextColor,
                                        maxLines = 1
                                    )
                                    if (tab == SignatureTab.SAVED && savedSignatures.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "${savedSignatures.size}",
                                                color = Color.White,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                when (selectedTab) {
                    SignatureTab.SAVED -> {
                        if (savedSignatures.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmarks,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "No tienes firmas guardadas aún",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Crea una firma en pantalla, sube tu certificado .p12 o escanea una firma en papel. Se almacenará cifrada en la bóveda de seguridad privada.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { selectedTab = SignatureTab.TACTIL },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Crear Firma en Pantalla", fontSize = 11.5.sp)
                                    }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = GreenSuccess,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Bóveda Segura (Toca para vista previa):",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "${savedSignatures.size} firma(s)",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 380.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(savedSignatures, key = { it.id }) { sigItem ->
                                        val sigBitmap = remember(sigItem.filePath) {
                                            SignatureSecurityVault.loadSignatureBitmap(sigItem.filePath)?.asImageBitmap()
                                        }
                                        val dateStr = remember(sigItem.createdAt) {
                                            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sigItem.createdAt))
                                        }

                                        OutlinedCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    previewSignatureItem = sigItem
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Miniatura de firma descifrada en memoria con fondo de transparencia
                                                TransparencyCheckerboardBox(
                                                    modifier = Modifier
                                                        .size(width = 80.dp, height = 50.dp)
                                                        .border(BorderStroke(1.dp, Slate200), RoundedCornerShape(6.dp)),
                                                    cornerRadius = 6.dp
                                                ) {
                                                    if (sigBitmap != null) {
                                                        Image(
                                                            bitmap = sigBitmap,
                                                            contentDescription = sigItem.title,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(3.dp),
                                                            contentScale = ContentScale.Fit
                                                        )
                                                    } else {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Default.Draw,
                                                                contentDescription = null,
                                                                tint = Color.LightGray,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                // Datos de la firma y badge de cifrado
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = sigItem.title,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        if (sigItem.isDefault) {
                                                            Surface(
                                                                color = AmberBadgeBg,
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(Icons.Default.Star, contentDescription = null, tint = AmberWarningDark, modifier = Modifier.size(10.dp))
                                                                    Spacer(modifier = Modifier.width(2.dp))
                                                                    Text("Predeterminada", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = AmberWarningDark)
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                        Surface(
                                                            color = GreenBadgeBg,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.Security, contentDescription = null, tint = GreenBadgeText, modifier = Modifier.size(10.dp))
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text("AES-256", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenBadgeText)
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = "Firmante: ${sigItem.signerName}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "📅 $dateStr",
                                                        fontSize = 10.sp,
                                                        color = Slate500
                                                    )
                                                }

                                                // Botón marcar como firma predeterminada (⭐)
                                                IconButton(
                                                    onClick = {
                                                        signatureRepo.setDefaultSignature(sigItem.id)
                                                        savedSignatures = signatureRepo.getAllSignatures()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (sigItem.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = if (sigItem.isDefault) "Firma predeterminada" else "Marcar como predeterminada",
                                                        tint = if (sigItem.isDefault) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                // Botón eliminar seguro
                                                IconButton(
                                                    onClick = {
                                                        signatureToDelete = sigItem
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Eliminar firma",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SignatureTab.TACTIL -> {
                        DigitalSignaturePad(
                            signerName = signerName,
                            isStandaloneVault = isStandaloneVault,
                            onSignatureCaptured = { path, saveToCatalog, customTitle ->
                                if (saveToCatalog || isStandaloneVault) {
                                    val item = SavedSignatureItem(
                                        title = customTitle.ifBlank { "Firma Pantalla ($signerName)" },
                                        signerName = signerName,
                                        signatureType = "DIGITAL_CANVAS",
                                        filePath = path,
                                        details = "Firma digital táctil registrada por $signerName",
                                        isEncrypted = true
                                    )
                                    signatureRepo.saveSignature(item)
                                    savedSignatures = signatureRepo.getAllSignatures()
                                    Toast.makeText(context, "✅ Firma guardada exitosamente en la bóveda", Toast.LENGTH_SHORT).show()
                                    selectedTab = SignatureTab.SAVED
                                } else {
                                    onSignatureSuccess(
                                        path,
                                        "DIGITAL_CANVAS",
                                        signerName,
                                        currentFormattedDate,
                                        "Firma digital táctil registrada por $signerName"
                                    )
                                }
                            },
                            onCancel = {
                                if (savedSignatures.isNotEmpty()) {
                                    selectedTab = SignatureTab.SAVED
                                } else {
                                    onDismiss()
                                }
                            }
                        )
                    }

                    SignatureTab.P12_ECUADOR -> {
                        val p12ScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(p12ScrollState)
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Firma Electrónica con Certificado Digital (.p12 / .pfx)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Compatible con certificados de Security Data, Banco Central del Ecuador, ANFAC, Consejo de la Judicatura y Uanataca.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Archivo .p12 / .pfx:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (p12Uri != null) p12FileName else "Ningún archivo seleccionado",
                                            fontSize = 11.5.sp,
                                            color = if (p12Uri != null) MaterialTheme.colorScheme.primary else Slate400,
                                            fontWeight = if (p12Uri != null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    Button(
                                        onClick = { p12PickerLauncher.launch(arrayOf("application/x-pkcs12", "*/*")) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Examinar", fontSize = 11.sp)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = p12Password,
                                onValueChange = { p12Password = it; p12Error = null },
                                label = { Text("Contraseña del Certificado .p12") },
                                singleLine = true,
                                visualTransformation = if (isP12PasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { isP12PasswordVisible = !isP12PasswordVisible }) {
                                        Icon(
                                            imageVector = if (isP12PasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isP12PasswordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Opción para guardar la firma .p12 para usos posteriores
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { saveP12ForFutureUse = !saveP12ForFutureUse },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = saveP12ForFutureUse,
                                            onCheckedChange = { saveP12ForFutureUse = it }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Guardar firma .p12 para usos posteriores",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Se guardará cifrada en tu catálogo para usarla con 1 toque",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (saveP12ForFutureUse) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = p12CustomTitle,
                                            onValueChange = { p12CustomTitle = it },
                                            label = { Text("Nombre / Etiqueta de la firma", fontSize = 11.sp) },
                                            placeholder = { Text(if (p12CertInfo != null) "Certificado .p12 (${p12CertInfo!!.subjectName})" else "Mi Certificado .p12", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp)
                                        )
                                    }
                                }
                            }

                            p12Error?.let { err ->
                                Text(
                                    text = "⚠️ $err",
                                    color = RedError,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            p12CertInfo?.let { cert ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = GreenBadgeLightBg),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("✅ Certificado Válido:", fontWeight = FontWeight.Bold, color = GreenBadgeText, fontSize = 12.sp)
                                        Text("Titular: ${cert.subjectName}", fontSize = 11.sp)
                                        Text("Entidad: ${cert.issuerName}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val currentP12Uri = p12Uri
                                    if (currentP12Uri == null) {
                                        p12Error = "Por favor selecciona tu archivo de firma .p12"
                                        return@Button
                                    }
                                    if (p12Password.isBlank()) {
                                        p12Error = "Ingresa la contraseña del certificado"
                                        return@Button
                                    }
                                    
                                    coroutineScope.launch {
                                        isVerifyingP12 = true
                                        val result = withContext(Dispatchers.IO) {
                                            p12Helper.extractP12Credentials(currentP12Uri, p12Password)
                                        }
                                        isVerifyingP12 = false

                                        result.onSuccess { creds ->
                                            p12CertInfo = creds.certInfo
                                            p12Error = null
                                            P12SignatureHelper.activeCredentials = creds

                                            val badgePath = withContext(Dispatchers.IO) {
                                                p12Helper.generateElectronicSignatureBadge(creds.certInfo, voucherInfo)
                                            }
                                            if (badgePath != null) {
                                                if (saveP12ForFutureUse || isStandaloneVault) {
                                                    val resolvedTitle = p12CustomTitle.ifBlank { "Certificado .p12 (${creds.certInfo.subjectName})" }
                                                    var vaultPath = ""
                                                    if (creds.rawBytes != null) {
                                                        val vaultResult = withContext(Dispatchers.IO) {
                                                            SignatureSecurityVault.saveEncryptedBytes(
                                                                context = context,
                                                                rawBytes = creds.rawBytes,
                                                                fileNamePrefix = "p12_cert"
                                                            )
                                                        }
                                                        vaultPath = vaultResult?.first ?: ""
                                                    }

                                                    val item = SavedSignatureItem(
                                                        title = resolvedTitle,
                                                        signerName = creds.certInfo.subjectName,
                                                        signatureType = "ELECTRONIC_P12",
                                                        filePath = badgePath,
                                                        details = "Firma Electrónica: ${creds.certInfo.subjectName} (${creds.certInfo.issuerName})",
                                                        isEncrypted = true,
                                                        p12VaultPath = vaultPath,
                                                        p12Password = p12Password
                                                    )
                                                    signatureRepo.saveSignature(item)
                                                    savedSignatures = signatureRepo.getAllSignatures()
                                                    p12Password = ""
                                                    Toast.makeText(context, "✅ Certificado .p12 guardado en la bóveda", Toast.LENGTH_SHORT).show()
                                                    selectedTab = SignatureTab.SAVED
                                                } else {
                                                    onSignatureSuccess(
                                                        badgePath,
                                                        "ELECTRONIC_P12",
                                                        creds.certInfo.subjectName,
                                                        currentFormattedDate,
                                                        "Firma Electrónica: ${creds.certInfo.subjectName} (${creds.certInfo.issuerName})"
                                                    )
                                                }
                                            } else {
                                                p12Error = "No se pudo generar el sello de firma"
                                            }
                                        }.onFailure { err ->
                                            p12Error = err.localizedMessage ?: "Error al validar certificado"
                                        }
                                    }
                                },
                                enabled = !isVerifyingP12,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CreditHomeBlue)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isStandaloneVault || saveP12ForFutureUse) "Validar y Guardar en Bóveda" else "Validar y Estampar Firma", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    SignatureTab.FISICA_FOTO -> {
                        val photoScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(photoScrollState)
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Foto de Firma en Papel / Documento Físico",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Sube una foto de la firma manuscrita realizada en papel para estamparla en el comprobante.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (selectedPhotoUri == null) {
                                Button(
                                    onClick = { showPhotoSourcePicker = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Capturar / Elegir Foto de Firma", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                if (isLoadingPhotoPreview) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                } else if (photoPreviewBmp != null) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Image(
                                                bitmap = photoPreviewBmp!!,
                                                contentDescription = "Vista previa firma",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }

                                // Switch para remover fondo blanco
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { removePhotoBackground = !removePhotoBackground }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "🪄 Quitar fondo de papel (Firma transparente)",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (removePhotoBackground) "Se elimina el papel blanco para estampar la tinta nítida" else "Se conserva el fondo original / blanco tal cual",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = removePhotoBackground,
                                            onCheckedChange = { removePhotoBackground = it }
                                        )
                                    }
                                }

                                // Opción para guardar la foto de firma para usos posteriores
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { savePhotoForFutureUse = !savePhotoForFutureUse },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = savePhotoForFutureUse,
                                                onCheckedChange = { savePhotoForFutureUse = it }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Guardar firma escaneada para usos posteriores",
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
                                        if (savePhotoForFutureUse) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = photoCustomTitle,
                                                onValueChange = { photoCustomTitle = it },
                                                label = { Text("Nombre / Etiqueta de la firma", fontSize = 11.sp) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showPhotoSourcePicker = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Cambiar", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { pendingCropPhotoUri = selectedPhotoUri },
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .heightIn(min = 44.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PurpleAccent),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Recortar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = {
                                            val photoUri = selectedPhotoUri ?: return@Button
                                            coroutineScope.launch {
                                                isProcessingPhoto = true
                                                val savedPath = processPhotoSignature(context, photoUri, removePhotoBackground)
                                                isProcessingPhoto = false

                                                if (savedPath != null) {
                                                    val type = if (removePhotoBackground) "FOTO_TRANSPARENTE" else "FOTO_ORIGINAL"
                                                    val details = "Firma física procesada (${if (removePhotoBackground) "Fondo transparente" else "Fondo original"})"
                                                    
                                                    if (savePhotoForFutureUse || isStandaloneVault) {
                                                        val resolvedTitle = photoCustomTitle.ifBlank { "Firma Escaneada ($signerName)" }
                                                        val item = SavedSignatureItem(
                                                            title = resolvedTitle,
                                                            signerName = signerName,
                                                            signatureType = type,
                                                            filePath = savedPath,
                                                            details = details,
                                                            isEncrypted = true
                                                        )
                                                        signatureRepo.saveSignature(item)
                                                        savedSignatures = signatureRepo.getAllSignatures()
                                                        selectedPhotoUri = null
                                                        Toast.makeText(context, "✅ Firma escaneada guardada en la bóveda", Toast.LENGTH_SHORT).show()
                                                        selectedTab = SignatureTab.SAVED
                                                    } else {
                                                        onSignatureSuccess(
                                                            savedPath,
                                                            type,
                                                            signerName,
                                                            currentFormattedDate,
                                                            details
                                                        )
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Error al procesar imagen de firma", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        enabled = !isProcessingPhoto,
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 44.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isStandaloneVault || savePhotoForFutureUse) "Guardar" else "Aceptar", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

/**
 * Contenedor visual que muestra el clásico patrón de cuadrícula de transparencia (Checkerboard).
 * Demuestra de forma inmediata e inequívoca que la firma tiene fondo 100% transparente.
 */
@Composable
private fun TransparencyCheckerboardBox(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White)
            .drawBehind {
                val squareSize = 8.dp.toPx()
                val cols = (size.width / squareSize).toInt() + 1
                val rows = (size.height / squareSize).toInt() + 1
                val checkColor = Slate200
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if ((r + c) % 2 == 1) {
                            drawRect(
                                color = checkColor,
                                topLeft = androidx.compose.ui.geometry.Offset(c * squareSize, r * squareSize),
                                size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Diálogo interactivo de Vista Previa para firmas guardadas en la bóveda.
 */
@Composable
private fun SignaturePreviewDialog(
    item: SavedSignatureItem,
    isStandaloneVault: Boolean,
    onStamp: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sigBitmap = remember(item.filePath) {
        SignatureSecurityVault.loadSignatureBitmap(item.filePath)?.asImageBitmap()
    }
    val dateStr = remember(item.createdAt) {
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabecera con Título y Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Vista Previa de Firma",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Área visual de la firma en alta resolución con fondo de transparencia visible
                TransparencyCheckerboardBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .border(BorderStroke(1.dp, Slate300), RoundedCornerShape(12.dp)),
                    cornerRadius = 12.dp
                ) {
                    if (sigBitmap != null) {
                        Image(
                            bitmap = sigBitmap,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("No se pudo cargar la imagen", color = Slate400, fontSize = 12.sp)
                        }
                    }
                }

                // Badges de Seguridad y Tipo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = GreenBadgeBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = GreenBadgeText, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cifrado AES-256-GCM", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = GreenBadgeText)
                        }
                    }

                    val typeLabel = when (item.signatureType) {
                        "DIGITAL_CANVAS" -> "✍️ Táctil"
                        "ELECTRONIC_P12" -> "🔑 Certificado .p12"
                        "FOTO_TRANSPARENTE" -> "🪄 Escaneo Nítido"
                        else -> "📷 Foto Manuscrita"
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Información Detallada
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👤 Firmante: ", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(item.signerName, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅 Registrada: ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.details.isNotBlank()) {
                            Text(
                                text = "📝 ${item.details}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(if (isStandaloneVault) 1f else 0.9f)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eliminar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }

                    if (!isStandaloneVault) {
                        Button(
                            onClick = onStamp,
                            colors = ButtonDefaults.buttonColors(containerColor = CreditHomeBlue),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Estampar Firma", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cerrar", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun decodeSampledBitmapFromUri(
    context: android.content.Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        var inSampleSize = 1
        val height = options.outHeight
        val width = options.outWidth

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private suspend fun processPhotoSignature(
    context: android.content.Context,
    uri: Uri,
    removeBackground: Boolean
): String? = withContext(Dispatchers.IO) {
    try {
        val originalBitmap = decodeSampledBitmapFromUri(context, uri, 1280, 960) ?: return@withContext null
        val width = originalBitmap.width
        val height = originalBitmap.height
        val pixels = IntArray(width * height)
        originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val lums = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lums[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        // Transformación Morfológica Black Top-Hat: Closing(I) - I
        // Radio R adaptativo para borrar trazos de pluma y modelar el papel 100% fiel
        val radius = (width / 48).coerceIn(14, 26)

        // 1. Dilatación en escala de grises (Separable)
        val tempDil = IntArray(width * height)
        val dilated = IntArray(width * height)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var maxVal = lums[row + x]
                val startX = (x - radius).coerceAtLeast(0)
                val endX = (x + radius).coerceAtMost(width - 1)
                for (kx in startX..endX) {
                    val v = lums[row + kx]
                    if (v > maxVal) maxVal = v
                }
                tempDil[row + x] = maxVal
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height) {
                var maxVal = tempDil[y * width + x]
                val startY = (y - radius).coerceAtLeast(0)
                val endY = (y + radius).coerceAtMost(height - 1)
                for (ky in startY..endY) {
                    val v = tempDil[ky * width + x]
                    if (v > maxVal) maxVal = v
                }
                dilated[y * width + x] = maxVal
            }
        }

        // 2. Erosión en escala de grises sobre la imagen dilatada para obtener la Clausura (Closing)
        val tempEro = IntArray(width * height)
        val closed = IntArray(width * height)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var minVal = dilated[row + x]
                val startX = (x - radius).coerceAtLeast(0)
                val endX = (x + radius).coerceAtMost(width - 1)
                for (kx in startX..endX) {
                    val v = dilated[row + kx]
                    if (v < minVal) minVal = v
                }
                tempEro[row + x] = minVal
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height) {
                var minVal = tempEro[y * width + x]
                val startY = (y - radius).coerceAtLeast(0)
                val endY = (y + radius).coerceAtMost(height - 1)
                for (ky in startY..endY) {
                    val v = tempEro[ky * width + x]
                    if (v < minVal) minVal = v
                }
                closed[y * width + x] = minVal
            }
        }

        // 3. Extracción de Tinta Top-Hat: Ink(x,y) = Closed(x,y) - Lum(x,y)
        val rawAlpha = IntArray(width * height)
        val borderMargin = 6
        val noiseFloor = 18f
        val solidInk = 42f

        for (y in 0 until height) {
            val row = y * width
            val isBorderY = y < borderMargin || y >= height - borderMargin
            for (x in 0 until width) {
                val idx = row + x
                if (isBorderY || x < borderMargin || x >= width - borderMargin) {
                    rawAlpha[idx] = 0
                    continue
                }

                val inkDelta = (closed[idx] - lums[idx]).toFloat()
                if (inkDelta <= noiseFloor) {
                    rawAlpha[idx] = 0
                } else if (inkDelta >= solidInk) {
                    rawAlpha[idx] = 255
                } else {
                    val t = (inkDelta - noiseFloor) / (solidInk - noiseFloor)
                    rawAlpha[idx] = (Math.pow(t.toDouble(), 0.65) * 255.0).toInt().coerceIn(0, 255)
                }
            }
        }

        // 4. Filtro Despeckle (Eliminación de ruido y motas aisladas de sensor)
        val cleanAlpha = IntArray(width * height)
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                val a = rawAlpha[idx]
                if (a == 0) {
                    cleanAlpha[idx] = 0
                    continue
                }

                if (a < 180 && x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                    var activeNeighbors = 0
                    if (rawAlpha[idx - 1] > 20) activeNeighbors++
                    if (rawAlpha[idx + 1] > 20) activeNeighbors++
                    if (rawAlpha[idx - width] > 20) activeNeighbors++
                    if (rawAlpha[idx + width] > 20) activeNeighbors++
                    if (rawAlpha[idx - width - 1] > 20) activeNeighbors++
                    if (rawAlpha[idx - width + 1] > 20) activeNeighbors++
                    if (rawAlpha[idx + width - 1] > 20) activeNeighbors++
                    if (rawAlpha[idx + width + 1] > 20) activeNeighbors++

                    if (activeNeighbors < 2) {
                        cleanAlpha[idx] = 0
                        continue
                    }
                }

                cleanAlpha[idx] = a
                if (a >= 35) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // 5. Renderizado de píxeles: Fondo 100% transparente y trazo sólido Slate #0F172A
        val outPixels = IntArray(width * height)
        val inkR = 15
        val inkG = 23
        val inkB = 42

        for (i in cleanAlpha.indices) {
            val a = cleanAlpha[i]
            if (removeBackground) {
                if (a > 10) {
                    outPixels[i] = android.graphics.Color.argb(a, inkR, inkG, inkB)
                } else {
                    outPixels[i] = android.graphics.Color.TRANSPARENT
                }
            } else {
                if (a > 10) {
                    val factor = a / 255f
                    val r = (255 * (1f - factor) + inkR * factor).toInt().coerceIn(0, 255)
                    val g = (255 * (1f - factor) + inkG * factor).toInt().coerceIn(0, 255)
                    val b = (255 * (1f - factor) + inkB * factor).toInt().coerceIn(0, 255)
                    outPixels[i] = android.graphics.Color.rgb(r, g, b)
                } else {
                    outPixels[i] = android.graphics.Color.WHITE
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)

        // 6. Auto-recorte centrado con margen de respiro de 20px
        val outputBitmap = if (maxX > minX && maxY > minY) {
            val pad = 20
            val startX = (minX - pad).coerceAtLeast(0)
            val startY = (minY - pad).coerceAtLeast(0)
            val endX = (maxX + pad).coerceAtMost(width - 1)
            val endY = (maxY + pad).coerceAtMost(height - 1)
            val cropW = (endX - startX + 1).coerceAtLeast(1)
            val cropH = (endY - startY + 1).coerceAtLeast(1)
            Bitmap.createBitmap(result, startX, startY, cropW, cropH)
        } else {
            result
        }

        // Cifrado de alta seguridad por hardware AES-256-GCM y verificación SHA-256
        val res = SignatureSecurityVault.saveEncryptedSignature(
            context = context,
            bitmap = outputBitmap,
            fileNamePrefix = "sig_photo"
        )
        res?.first
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
