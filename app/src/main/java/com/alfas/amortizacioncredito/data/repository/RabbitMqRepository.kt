package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.alfas.amortizacioncredito.data.model.OutboxQueueMessage
import com.alfas.amortizacioncredito.data.model.RabbitMqConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RabbitMqRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("rabbitmq_settings", Context.MODE_PRIVATE)
    private val outboxFile: File = File(context.filesDir, "rabbitmq_outbox_queue.json")

    fun getConfig(): RabbitMqConfig {
        return RabbitMqConfig(
            host = prefs.getString("host", "") ?: "",
            port = prefs.getInt("port", 5672),
            virtualHost = prefs.getString("virtualHost", "/") ?: "/",
            username = prefs.getString("username", "") ?: "",
            password = prefs.getString("password", "") ?: "",
            isEnabled = prefs.getBoolean("isEnabled", false),
            emailQueueName = prefs.getString("emailQueueName", "credithome_email_queue") ?: "credithome_email_queue",
            syncQueueName = prefs.getString("syncQueueName", "credithome_sync_queue") ?: "credithome_sync_queue",
            useSsl = prefs.getBoolean("useSsl", false)
        )
    }

    fun saveConfig(config: RabbitMqConfig) {
        prefs.edit()
            .putString("host", config.host)
            .putInt("port", config.port)
            .putString("virtualHost", config.virtualHost)
            .putString("username", config.username)
            .putString("password", config.password)
            .putBoolean("isEnabled", config.isEnabled)
            .putString("emailQueueName", config.emailQueueName)
            .putString("syncQueueName", config.syncQueueName)
            .putBoolean("useSsl", config.useSsl)
            .apply()
    }

    @Synchronized
    fun getOutboxMessages(): List<OutboxQueueMessage> {
        if (!outboxFile.exists()) return emptyList()
        return try {
            val content = outboxFile.readText()
            val arr = JSONArray(content)
            val list = mutableListOf<OutboxQueueMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    OutboxQueueMessage(
                        id = obj.getString("id"),
                        queueName = obj.getString("queueName"),
                        payloadJson = obj.getString("payloadJson"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        attempts = obj.optInt("attempts", 0),
                        status = obj.optString("status", "PENDING")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun enqueueMessage(message: OutboxQueueMessage) {
        val current = getOutboxMessages().toMutableList()
        current.add(message)
        saveOutbox(current)
    }

    @Synchronized
    fun removeMessage(messageId: String) {
        val current = getOutboxMessages().filterNot { it.id == messageId }
        saveOutbox(current)
    }

    @Synchronized
    fun clearQueue() {
        if (outboxFile.exists()) outboxFile.delete()
    }

    private fun saveOutbox(list: List<OutboxQueueMessage>) {
        try {
            val arr = JSONArray()
            for (m in list) {
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("queueName", m.queueName)
                    put("payloadJson", m.payloadJson)
                    put("timestamp", m.timestamp)
                    put("attempts", m.attempts)
                    put("status", m.status)
                }
                arr.put(obj)
            }
            outboxFile.writeText(arr.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
