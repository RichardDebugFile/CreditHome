package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import com.alfas.amortizacioncredito.data.model.AuditActionType
import com.alfas.amortizacioncredito.data.model.AuditLogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AuditRepository(private val context: Context) {

    private val file = File(context.filesDir, "company_audit_log.json")

    fun getAllLogs(): List<AuditLogEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<AuditLogEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AuditLogEntry(
                        id = obj.getString("id"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        userId = obj.optString("userId", "master"),
                        username = obj.optString("username", "Usuario Máster"),
                        userRole = obj.optString("userRole", "MASTER"),
                        companyName = obj.optString("companyName", "CreditHome"),
                        action = AuditActionType.valueOf(obj.optString("action", AuditActionType.CREATE_CLIENT.name)),
                        description = obj.getString("description"),
                        details = obj.optString("details", "")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun log(
        action: AuditActionType,
        description: String,
        username: String = "Usuario Máster",
        userRole: String = "MASTER",
        companyName: String = "CreditHome",
        details: String = ""
    ) {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = username.lowercase().replace(" ", "_"),
            username = username,
            userRole = userRole,
            companyName = companyName,
            action = action,
            description = description,
            details = details
        )
        val current = getAllLogs().toMutableList()
        current.add(0, entry)
        saveAll(current.take(300)) // Guardar los 300 eventos más recientes
    }

    fun clearLogs() {
        if (file.exists()) file.delete()
    }

    private fun saveAll(entries: List<AuditLogEntry>) {
        try {
            val jsonArray = JSONArray()
            for (e in entries) {
                val obj = JSONObject().apply {
                    put("id", e.id)
                    put("timestamp", e.timestamp)
                    put("userId", e.userId)
                    put("username", e.username)
                    put("userRole", e.userRole)
                    put("companyName", e.companyName)
                    put("action", e.action.name)
                    put("description", e.description)
                    put("details", e.details)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
