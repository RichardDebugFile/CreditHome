package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import com.alfas.amortizacioncredito.data.model.SavedSignatureItem
import com.alfas.amortizacioncredito.domain.security.SecurityManager
import com.alfas.amortizacioncredito.domain.security.SignatureSecurityVault
import org.json.JSONArray
import java.io.File

class SavedSignatureRepository(private val context: Context) {

    private val securityManager = SecurityManager(context)
    private val file = File(context.filesDir, "saved_signatures.json")

    fun getAllSignatures(): List<SavedSignatureItem> {
        if (!file.exists()) return emptyList()
        return try {
            val rawContent = file.readText()
            // Descifrar si está cifrado con el vault de la app o procesar directo
            val decryptedContent = securityManager.decryptString(rawContent)
            val jsonArray = JSONArray(decryptedContent)
            val list = mutableListOf<SavedSignatureItem>()
            for (i in 0 until jsonArray.length()) {
                val item = SavedSignatureItem.fromJson(jsonArray.getJSONObject(i))
                // Solo incluir si el archivo de firma existe físicamente en el sandbox privado
                if (item.filePath.isNotBlank()) {
                    val cleanPath = item.filePath.removePrefix("file://")
                    if (File(cleanPath).exists()) {
                        list.add(item)
                    }
                }
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveSignature(item: SavedSignatureItem) {
        val current = getAllSignatures().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            current[index] = item
        } else {
            current.add(0, item)
        }
        saveAll(current)
    }

    fun deleteSignature(id: String) {
        val current = getAllSignatures().toMutableList()
        val found = current.find { it.id == id }
        if (found != null) {
            // Destrucción criptográfica segura del archivo de imagen
            SignatureSecurityVault.secureDeleteSignature(found.filePath)
            current.removeAll { it.id == id }
            saveAll(current)
        }
    }

    fun getDefaultSignature(): SavedSignatureItem? {
        val list = getAllSignatures()
        return list.firstOrNull { it.isDefault } ?: list.firstOrNull()
    }

    fun setDefaultSignature(id: String) {
        val current = getAllSignatures().map {
            it.copy(isDefault = (it.id == id))
        }
        saveAll(current)
    }

    private fun saveAll(list: List<SavedSignatureItem>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                jsonArray.put(item.toJson())
            }
            val plainJson = jsonArray.toString(2)
            // Cifrar el archivo de metadatos con AES-256-GCM
            val encryptedJson = securityManager.encryptString(plainJson)
            file.writeText(encryptedJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
