package com.alfas.amortizacioncredito.data.model

import org.json.JSONObject
import java.util.UUID

data class SavedSignatureItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val signerName: String,
    val signatureType: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val details: String = "",
    val sha256Hash: String = "",
    val isEncrypted: Boolean = true,
    val p12VaultPath: String = "",
    val p12Password: String = "",
    val isDefault: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("signerName", signerName)
            put("signatureType", signatureType)
            put("filePath", filePath)
            put("createdAt", createdAt)
            put("details", details)
            put("sha256Hash", sha256Hash)
            put("isEncrypted", isEncrypted)
            put("p12VaultPath", p12VaultPath)
            put("p12Password", p12Password)
            put("isDefault", isDefault)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SavedSignatureItem {
            return SavedSignatureItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Firma Guardada"),
                signerName = json.optString("signerName", "Administrador"),
                signatureType = json.optString("signatureType", "DIGITAL_CANVAS"),
                filePath = json.optString("filePath", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                details = json.optString("details", ""),
                sha256Hash = json.optString("sha256Hash", ""),
                isEncrypted = json.optBoolean("isEncrypted", true),
                p12VaultPath = json.optString("p12VaultPath", ""),
                p12Password = json.optString("p12Password", ""),
                isDefault = json.optBoolean("isDefault", false)
            )
        }
    }
}
