package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import com.alfas.amortizacioncredito.data.model.AppNotification
import com.alfas.amortizacioncredito.data.model.NotificationType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NotificationRepository(private val context: Context) {

    private val file = File(context.filesDir, "app_notifications.json")

    fun getAllNotifications(): List<AppNotification> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<AppNotification>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AppNotification(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        message = obj.getString("message"),
                        type = NotificationType.valueOf(obj.optString("type", NotificationType.DUE_SOON.name)),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isRead = obj.optBoolean("isRead", false),
                        targetClientId = obj.optString("targetClientId").takeIf { it.isNotBlank() },
                        targetMonth = if (obj.has("targetMonth")) obj.getInt("targetMonth") else null
                    )
                )
            }
            
            // Deduplicación inteligente: si existen varias notificaciones del mismo evento/cuota/cliente, conservar la más reciente
            val distinctList = list
                .sortedByDescending { it.timestamp }
                .distinctBy { notif ->
                    if (notif.targetClientId != null && notif.targetMonth != null) {
                        "${notif.type}_${notif.targetClientId}_${notif.targetMonth}"
                    } else {
                        notif.id
                    }
                }

            if (distinctList.size != list.size) {
                saveAll(distinctList)
            }

            distinctList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private val dismissedFile = File(context.filesDir, "dismissed_notifications.json")

    private fun getDismissedIds(): MutableSet<String> {
        if (!dismissedFile.exists()) return mutableSetOf()
        return try {
            val content = dismissedFile.readText()
            val arr = JSONArray(content)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            set
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    private fun saveDismissedIds(ids: Set<String>) {
        try {
            val arr = JSONArray()
            ids.toList().takeLast(500).forEach { arr.put(it) }
            dismissedFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addNotification(notification: AppNotification) {
        val dismissed = getDismissedIds()
        if (notification.type == NotificationType.PAYMENT_RECEIVED) {
            // Un pago nuevo registrado legítimamente no debe ser bloqueado por descartes anteriores
            if (dismissed.remove(notification.id)) {
                saveDismissedIds(dismissed)
            }
        } else if (dismissed.contains(notification.id)) {
            return
        }

        val current = getAllNotifications().toMutableList()
        // Evitar duplicados por ID o por cuota/cliente
        val existingIndex = current.indexOfFirst {
            it.id == notification.id || (
                notification.targetClientId != null &&
                notification.targetMonth != null &&
                it.type == notification.type &&
                it.targetClientId == notification.targetClientId &&
                it.targetMonth == notification.targetMonth
            )
        }
        if (existingIndex != -1) {
            val existing = current[existingIndex]
            current[existingIndex] = notification.copy(
                isRead = false,
                timestamp = System.currentTimeMillis()
            )
        } else {
            current.add(0, notification)
        }
        saveAll(current.take(100))
    }

    fun markAsRead(notificationId: String) {
        val current = getAllNotifications().toMutableList()
        val index = current.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            current[index] = current[index].copy(isRead = true)
            saveAll(current)
        }
    }

    fun markAllAsRead() {
        val current = getAllNotifications().map { it.copy(isRead = true) }
        saveAll(current)
    }

    fun deleteNotification(notificationId: String) {
        val dismissed = getDismissedIds()
        dismissed.add(notificationId)
        saveDismissedIds(dismissed)

        val current = getAllNotifications().filterNot { it.id == notificationId }
        saveAll(current)
    }

    fun clearAll() {
        val dismissed = getDismissedIds()
        getAllNotifications().forEach { dismissed.add(it.id) }
        saveDismissedIds(dismissed)

        if (file.exists()) file.delete()
    }

    private fun saveAll(notifications: List<AppNotification>) {
        try {
            val jsonArray = JSONArray()
            for (n in notifications) {
                val obj = JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("message", n.message)
                    put("type", n.type.name)
                    put("timestamp", n.timestamp)
                    put("isRead", n.isRead)
                    if (n.targetClientId != null) put("targetClientId", n.targetClientId)
                    if (n.targetMonth != null) put("targetMonth", n.targetMonth)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
