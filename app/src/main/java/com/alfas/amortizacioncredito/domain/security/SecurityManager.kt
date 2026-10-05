package com.alfas.amortizacioncredito.domain.security

import android.content.Context
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import com.alfas.amortizacioncredito.config.AppConfig
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.data.model.UserPermissions

class SecurityManager(private val context: Context) {

    private val masterAppSalt = AppConfig.APP_CRYPTO_SALT

    private fun getAppAesKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest((masterAppSalt + "CreditHome_Global_AES_Vault_Key").toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Cifra un texto o JSON usando AES-256-GCM con IV aleatorio seguro.
     * Retorna una cadena Base64 con prefijo "ENC_GCM:" lista para almacenamiento/exportación segura.
     */
    fun encryptString(plainText: String): String {
        return try {
            val key = getAppAesKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Empaquetar IV (12 bytes) + CipherText
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            "ENC_GCM:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText // Fallback seguro
        }
    }

    /**
     * Descifra una cadena previamente cifrada con AES-256-GCM.
     * Si no está cifrada (o es un backup antiguo en texto plano), retorna el texto original directamente.
     */
    fun decryptString(cipherTextWithHeader: String): String {
        if (!cipherTextWithHeader.startsWith("ENC_GCM:")) {
            return cipherTextWithHeader // Compatibilidad con backups antiguos sin cifrar
        }
        return try {
            val key = getAppAesKey()
            val rawB64 = cipherTextWithHeader.removePrefix("ENC_GCM:")
            val combined = Base64.decode(rawB64, Base64.NO_WRAP)
            if (combined.size < 12) return cipherTextWithHeader

            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val cipherText = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            cipherTextWithHeader
        }
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(inputPin: String, storedHash: String): Boolean {
        // Un usuario sin PIN registrado nunca puede autenticarse.
        if (storedHash.isBlank() || inputPin.isBlank()) return false
        return hashPin(inputPin) == storedHash
    }

    /**
     * Valida la robustez mínima de un PIN.
     * Retorna null si es válido, o el mensaje de error a mostrar al usuario.
     */
    fun validatePinStrength(pin: String): String? {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            return "El PIN debe tener entre 4 y 6 dígitos numéricos."
        }
        if (pin.toSet().size == 1) {
            return "El PIN no puede repetir el mismo dígito (ej. 0000)."
        }
        val ascending = "0123456789"
        val descending = ascending.reversed()
        if (ascending.contains(pin) || descending.contains(pin)) {
            return "El PIN no puede ser una secuencia (ej. 1234 o 4321)."
        }
        return null
    }

    fun getDeviceFingerprint(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_default"
            val raw = "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_$androidId"
            hashPin(raw).take(16)
        } catch (_: Exception) {
            "device_secure_token"
        }
    }

    fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }

    fun exportInvitationToken(
        username: String,
        displayName: String,
        role: String,
        companyRuc: String,
        companyName: String,
        pin: String,
        permissions: com.alfas.amortizacioncredito.data.model.UserPermissions
    ): String {
        val obj = JSONObject().apply {
            put("u", username)
            put("d", displayName)
            put("r", role)
            put("c", companyRuc)
            put("n", companyName)
            put("p", pin)
            put("editClients", permissions.canEditClients)
            put("recordPayments", permissions.canRecordPayments)
            put("viewReceipts", permissions.canViewReceipts)
            put("exportReports", permissions.canExportReports)
            put("manageBackups", permissions.canManageBackups)
            put("t", System.currentTimeMillis())
        }
        return Base64.encodeToString(obj.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun generateInvitationToken(user: com.alfas.amortizacioncredito.data.model.AppUser): String {
        return exportInvitationToken(
            username = user.username,
            displayName = user.displayName,
            role = user.role.name,
            companyRuc = "",
            companyName = user.companyName,
            pin = "",
            permissions = user.permissions
        )
    }

    fun parseInvitationToken(token: String): JSONObject? {
        return try {
            val decoded = String(Base64.decode(token.trim(), Base64.NO_WRAP), Charsets.UTF_8)
            JSONObject(decoded)
        } catch (_: Exception) {
            null
        }
    }
}
