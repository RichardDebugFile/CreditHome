package com.alfas.amortizacioncredito.domain.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*

data class P12CertInfo(
    val subjectName: String,
    val issuerName: String,
    val validFrom: Date,
    val validTo: Date,
    val serialNumber: String,
    val isExpired: Boolean
)

data class P12Credentials(
    val privateKey: PrivateKey,
    val certificateChain: Array<X509Certificate>,
    val certInfo: P12CertInfo,
    val rawBytes: ByteArray? = null,
    val password: String = ""
)

class P12SignatureHelper(private val context: Context) {

    companion object {
        /**
         * Credenciales criptográficas activas en memoria para la sesión de firmado.
         */
        var activeCredentials: P12Credentials? = null
    }

    fun extractP12Credentials(uri: Uri, password: String): Result<P12Credentials> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("No se pudo abrir el archivo .p12/.pfx"))
            val rawBytes = inputStream.use { it.readBytes() }
            extractP12CredentialsFromBytes(rawBytes, password)
        } catch (e: Exception) {
            val msg = if (e.message?.contains("password", ignoreCase = true) == true || e.cause?.message?.contains("password", ignoreCase = true) == true) {
                "Contraseña del certificado .p12 incorrecta."
            } else {
                "Error al procesar el certificado: ${e.localizedMessage ?: "Archivo no compatible"}"
            }
            Result.failure(Exception(msg))
        }
    }

    fun extractP12CredentialsFromBytes(rawBytes: ByteArray, password: String): Result<P12Credentials> {
        return try {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(ByteArrayInputStream(rawBytes), password.toCharArray())

            val aliases = keyStore.aliases()
            var privateKey: PrivateKey? = null
            var certChain: Array<X509Certificate>? = null
            var mainCert: X509Certificate? = null

            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (keyStore.isKeyEntry(alias)) {
                    val key = keyStore.getKey(alias, password.toCharArray()) as? PrivateKey
                    val chain = keyStore.getCertificateChain(alias)
                    if (key != null && chain != null && chain.isNotEmpty()) {
                        privateKey = key
                        certChain = chain.filterIsInstance<X509Certificate>().toTypedArray()
                        mainCert = chain[0] as? X509Certificate
                        break
                    }
                }
            }

            if (privateKey == null || certChain == null || certChain.isEmpty() || mainCert == null) {
                return Result.failure(Exception("No se encontró ningún certificado válido con clave privada en el archivo."))
            }

            val subject = mainCert.subjectDN.name
            val issuer = mainCert.issuerDN.name

            val commonName = extractField(subject, "CN") ?: subject
            val issuerName = extractField(issuer, "CN") ?: extractField(issuer, "O") ?: issuer

            val now = Date()
            val isExpired = now.before(mainCert.notBefore) || now.after(mainCert.notAfter)

            val info = P12CertInfo(
                subjectName = commonName,
                issuerName = issuerName,
                validFrom = mainCert.notBefore,
                validTo = mainCert.notAfter,
                serialNumber = mainCert.serialNumber.toString(16).uppercase(Locale.US),
                isExpired = isExpired
            )

            val creds = P12Credentials(
                privateKey = privateKey,
                certificateChain = certChain,
                certInfo = info,
                rawBytes = rawBytes,
                password = password
            )
            Result.success(creds)
        } catch (e: Exception) {
            val msg = if (e.message?.contains("password", ignoreCase = true) == true || e.cause?.message?.contains("password", ignoreCase = true) == true) {
                "Contraseña del certificado .p12 incorrecta."
            } else {
                "Error al procesar el certificado: ${e.localizedMessage ?: "Archivo no compatible"}"
            }
            Result.failure(Exception(msg))
        }
    }

    fun validateAndExtractP12Info(uri: Uri, password: String): Result<P12CertInfo> {
        return extractP12Credentials(uri, password).map { it.certInfo }
    }

    fun generateElectronicSignatureBadge(
        certInfo: P12CertInfo,
        voucherInfo: String
    ): String? {
        return try {
            val width = 760
            val height = 210
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Fondo blanco puro con borde corporativo azul y esquinas redondeadas
            val bgPaint = Paint().apply {
                color = AndroidColor.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val borderPaint = Paint().apply {
                color = AndroidColor.argb(255, 30, 58, 138)
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                isAntiAlias = true
            }

            val rect = RectF(4f, 4f, width - 4f, height - 4f)
            canvas.drawRoundRect(rect, 10f, 10f, bgPaint)
            canvas.drawRoundRect(rect, 10f, 10f, borderPaint)

            // Franja lateral azul izquierda
            val stripPaint = Paint().apply {
                color = AndroidColor.argb(255, 30, 58, 138)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(4f, 4f, 16f, height - 4f), 10f, 10f, stripPaint)

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentDate = sdf.format(Date())

            // 2. Generación del Código QR Oficial de Validación (Estándar FirmaEC Ecuador)
            val qrPayload = buildString {
                append("FIRMADO POR: ").append(certInfo.subjectName).append("\n")
                append("RAZON: ").append(voucherInfo.ifBlank { "Comprobante de Pago" }).append("\n")
                append("FECHA: ").append(currentDate).append("\n")
                append("LOCALIZACION: Ecuador\n")
                append("ENTIDAD: ").append(certInfo.issuerName).append("\n")
                append("VALIDAR CON: www.firmadigital.gob.ec")
            }

            val qrBitmap = QrCodeGenerator.generateQrBitmap(qrPayload, 260)
            if (qrBitmap != null) {
                val qrDest = RectF(24f, 15f, 195f, 195f)
                canvas.drawBitmap(qrBitmap, null, qrDest, Paint(Paint.ANTI_ALIAS_FLAG))

                // Separador vertical entre QR y texto
                val sepPaint = Paint().apply {
                    color = AndroidColor.argb(255, 226, 232, 240)
                    strokeWidth = 1.5f
                }
                canvas.drawLine(208f, 15f, 208f, 195f, sepPaint)
            }

            // 3. Textos según estándar oficial de firma electrónica (FirmaEC / Quipux / Banco Central)
            val textPaint = Paint().apply {
                isAntiAlias = true
            }

            val startX = 222f

            // Encabezado
            textPaint.color = AndroidColor.argb(255, 30, 58, 138)
            textPaint.textSize = 14f
            textPaint.isFakeBoldText = true
            canvas.drawText("FIRMADO DIGITALMENTE POR:", startX, 32f, textPaint)

            // Titular en mayúsculas destacado
            textPaint.color = AndroidColor.argb(255, 15, 23, 42)
            textPaint.textSize = 17.5f
            textPaint.isFakeBoldText = true
            canvas.drawText(certInfo.subjectName.uppercase(Locale.US), startX, 58f, textPaint)

            // Fecha
            textPaint.isFakeBoldText = false
            textPaint.textSize = 12f
            textPaint.color = AndroidColor.argb(255, 51, 65, 85)
            canvas.drawText("FECHA: $currentDate", startX, 84f, textPaint)

            // Razón / Referencia
            canvas.drawText("RAZÓN: ${voucherInfo.ifBlank { "Comprobante Oficial de Pago" }}", startX, 108f, textPaint)

            // Entidad Certificadora
            canvas.drawText("ENTIDAD: ${certInfo.issuerName}", startX, 132f, textPaint)

            // Localización y Validez
            canvas.drawText("LOCALIZACIÓN: Ecuador  |  SERIAL: ${certInfo.serialNumber.take(12)}...", startX, 156f, textPaint)

            // Enlace oficial de validación
            textPaint.color = AndroidColor.argb(255, 22, 101, 52)
            textPaint.textSize = 11.5f
            textPaint.isFakeBoldText = true
            canvas.drawText("VALIDAR CON: www.firmadigital.gob.ec (Ley de Comercio Electrónico)", startX, 184f, textPaint)

            // Guardar el sello de validación con cifrado AES-256-GCM y verificación SHA-256
            val result = SignatureSecurityVault.saveEncryptedSignature(
                context = context,
                bitmap = bitmap,
                fileNamePrefix = "p12_sig"
            )
            result?.first
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractField(dn: String, field: String): String? {
        val tokens = dn.split(",")
        for (t in tokens) {
            val pair = t.trim().split("=", limit = 2)
            if (pair.size == 2 && pair[0].trim().equals(field, ignoreCase = true)) {
                return pair[1].trim().removeSurrounding("\"")
            }
        }
        return null
    }
}
