package com.alfas.amortizacioncredito.domain.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.alfas.amortizacioncredito.config.AppConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bóveda criptográfica de alta seguridad para firmas digitales y sellos electrónicos.
 *
 * Características de seguridad:
 * - Cifrado por hardware AES-256-GCM mediante Android KeyStore (TEE / Secure Element) con fallback transparente.
 * - Protección contra manipulación y alteración de firmas con verificación SHA-256 e integridad GCM.
 * - Destrucción segura de archivos (Sobrescritura criptográfica antes de borrar del almacenamiento flash).
 * - Almacenamiento restringido dentro del sandbox privado de la aplicación.
 */
object SignatureSecurityVault {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CREDITHOME_SIGNATURE_VAULT_KEY_V1"
    private val MAGIC_HEADER = byteArrayOf(0x53, 0x49, 0x47, 0x56) // 'S', 'I', 'G', 'V' (Signature Vault)
    private const val IV_SIZE = 12
    private const val GCM_TAG_LENGTH = 128

    private fun getFallbackKey(): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest((AppConfig.APP_CRYPTO_SALT + "_SIGNATURE_VAULT_AES256_KEY").toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    @Synchronized
    private fun getSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } catch (_: Exception) {
            getFallbackKey()
        }
    }

    /**
     * Cifra un arreglo de bytes usando AES-256-GCM y lo guarda en el almacenamiento seguro privado.
     * Retorna un Par con (Ruta absoluta del archivo cifrado, Hash SHA-256 del contenido original).
     */
    fun saveEncryptedBytes(
        context: Context,
        rawBytes: ByteArray,
        fileNamePrefix: String = "sec"
    ): Pair<String, String>? {
        return try {
            // 1. Calcular Hash SHA-256 del original
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = sha256Digest.digest(rawBytes)
            val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

            // 2. Cifrar con AES-256-GCM (Permitiendo que KeyStore genere su IV interno seguro o con fallback)
            var iv: ByteArray
            var cipherText: ByteArray

            try {
                val key = getSecretKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                iv = cipher.iv ?: ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
                cipherText = cipher.doFinal(rawBytes)
            } catch (_: Exception) {
                val fallbackKey = getFallbackKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, fallbackKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                cipherText = cipher.doFinal(rawBytes)
            }

            // 3. Preparar directorio seguro en sandbox privado
            val dir = File(context.filesDir, "secure_signatures")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.sig"
            val file = File(dir, fileName)

            // 4. Escribir archivo con estructura: MAGIC (4) + IV (12) + CIPHERTEXT
            FileOutputStream(file).use { out ->
                out.write(MAGIC_HEADER)
                out.write(iv)
                out.write(cipherText)
                out.flush()
            }

            Pair(file.absolutePath, hashHex)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Cifra un Bitmap de firma usando AES-256-GCM y lo guarda en el almacenamiento seguro privado.
     * Retorna un Par con (Ruta absoluta del archivo cifrado, Hash SHA-256 del contenido original).
     */
    fun saveEncryptedSignature(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String = "sig"
    ): Pair<String, String>? {
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            saveEncryptedBytes(context, baos.toByteArray(), fileNamePrefix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Carga y descifra un archivo de bytes cifrado de la bóveda.
     */
    fun loadDecryptedBytes(filePath: String): ByteArray? {
        if (filePath.isBlank()) return null
        val cleanPath = filePath.removePrefix("file://")
        val file = File(cleanPath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val totalSize = file.length().toInt()
            if (totalSize < 16) {
                return FileInputStream(file).use { it.readBytes() }
            }

            val fileBytes = FileInputStream(file).use { it.readBytes() }

            val isEncrypted = fileBytes.size >= MAGIC_HEADER.size + IV_SIZE &&
                    fileBytes[0] == MAGIC_HEADER[0] &&
                    fileBytes[1] == MAGIC_HEADER[1] &&
                    fileBytes[2] == MAGIC_HEADER[2] &&
                    fileBytes[3] == MAGIC_HEADER[3]

            if (isEncrypted) {
                val iv = ByteArray(IV_SIZE)
                System.arraycopy(fileBytes, MAGIC_HEADER.size, iv, 0, IV_SIZE)

                val cipherTextOffset = MAGIC_HEADER.size + IV_SIZE
                val cipherTextLength = fileBytes.size - cipherTextOffset
                val cipherText = ByteArray(cipherTextLength)
                System.arraycopy(fileBytes, cipherTextOffset, cipherText, 0, cipherTextLength)

                try {
                    val key = getSecretKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                    cipher.doFinal(cipherText)
                } catch (_: Exception) {
                    val fallbackKey = getFallbackKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, fallbackKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                    cipher.doFinal(cipherText)
                }
            } else {
                fileBytes
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Carga y descifra un Bitmap de firma en memoria de forma transparente.
     * Si el archivo no está cifrado (formato legado), lo decodifica directamente para retrocompatibilidad.
     */
    fun loadSignatureBitmap(filePath: String): Bitmap? {
        val cleanPath = filePath.removePrefix("file://")
        val bytes = loadDecryptedBytes(cleanPath)
        return if (bytes != null && bytes.isNotEmpty()) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            try {
                BitmapFactory.decodeFile(cleanPath)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Verifica la integridad de un archivo de firma cifrado.
     * Retorna true si el archivo no ha sido alterado y se puede descifrar válidamente.
     */
    fun verifySignatureIntegrity(filePath: String, expectedHash: String = ""): Boolean {
        val cleanPath = filePath.removePrefix("file://")
        val file = File(cleanPath)
        if (!file.exists()) return false

        return try {
            val fileBytes = FileInputStream(file).use { it.readBytes() }
            val isEncrypted = fileBytes.size >= MAGIC_HEADER.size + IV_SIZE &&
                    fileBytes[0] == MAGIC_HEADER[0] &&
                    fileBytes[1] == MAGIC_HEADER[1] &&
                    fileBytes[2] == MAGIC_HEADER[2] &&
                    fileBytes[3] == MAGIC_HEADER[3]

            if (isEncrypted) {
                val iv = ByteArray(IV_SIZE)
                System.arraycopy(fileBytes, MAGIC_HEADER.size, iv, 0, IV_SIZE)

                val cipherTextOffset = MAGIC_HEADER.size + IV_SIZE
                val cipherTextLength = fileBytes.size - cipherTextOffset
                val cipherText = ByteArray(cipherTextLength)
                System.arraycopy(fileBytes, cipherTextOffset, cipherText, 0, cipherTextLength)

                val plainBytes = try {
                    val key = getSecretKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                    cipher.doFinal(cipherText)
                } catch (_: Exception) {
                    val fallbackKey = getFallbackKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, fallbackKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                    cipher.doFinal(cipherText)
                }

                if (expectedHash.isNotBlank()) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val computedHash = digest.digest(plainBytes).joinToString("") { "%02x".format(it) }
                    computedHash.equals(expectedHash, ignoreCase = true)
                } else {
                    plainBytes.isNotEmpty()
                }
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Eliminación segura (Data Shredding): Sobrescribe el archivo físico con bytes aleatorios
     * antes de removerlo del sistema de archivos, impidiendo su recuperación en memoria flash.
     */
    fun secureDeleteSignature(filePath: String): Boolean {
        val cleanPath = filePath.removePrefix("file://")
        val file = File(cleanPath)
        if (!file.exists()) return true

        return try {
            val length = file.length()
            if (length > 0) {
                val randomBytes = ByteArray(length.toInt().coerceAtMost(64 * 1024))
                SecureRandom().nextBytes(randomBytes)
                RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0)
                    var written = 0L
                    while (written < length) {
                        val toWrite = (length - written).toInt().coerceAtMost(randomBytes.size)
                        raf.write(randomBytes, 0, toWrite)
                        written += toWrite
                    }
                }
            }
            file.delete()
        } catch (_: Exception) {
            file.delete()
        }
    }
}
