package com.alfas.amortizacioncredito.domain.service

import android.content.Context
import com.alfas.amortizacioncredito.data.model.OutboxQueueMessage
import com.alfas.amortizacioncredito.data.model.RabbitMqConfig
import com.alfas.amortizacioncredito.data.repository.RabbitMqRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class RabbitMqQueueManager(
    private val context: Context,
    private val rabbitMqRepository: RabbitMqRepository = RabbitMqRepository(context)
) {

    fun getConfig(): RabbitMqConfig = rabbitMqRepository.getConfig()

    fun saveConfig(config: RabbitMqConfig) = rabbitMqRepository.saveConfig(config)

    fun getPendingCount(): Int = rabbitMqRepository.getOutboxMessages().count { it.status == "PENDING" }

    suspend fun testRabbitMqConnection(config: RabbitMqConfig): Result<String> = withContext(Dispatchers.IO) {
        if (config.host.isBlank()) {
            return@withContext Result.failure(Exception("Debes especificar la IP o dominio del host RabbitMQ."))
        }

        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(config.host, config.port), 6000)

            // Enviar cabecera de protocolo AMQP 0-9-1: 'A','M','Q','P', 0, 0, 9, 1
            val out = socket.getOutputStream()
            out.write(byteArrayOf('A'.code.toByte(), 'M'.code.toByte(), 'Q'.code.toByte(), 'P'.code.toByte(), 0, 0, 9, 1))
            out.flush()

            // Leer respuesta inicial
            val inp = socket.getInputStream()
            val buffer = ByteArray(8)
            val read = inp.read(buffer)

            Result.success("¡Conexión establecida exitosamente con el broker RabbitMQ en ${config.host}:${config.port}!")
        } catch (e: Exception) {
            Result.failure(Exception("No se pudo conectar con RabbitMQ en ${config.host}:${config.port}: ${e.localizedMessage ?: "Timeout"}"))
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun enqueueEmailJob(recipientEmail: String, clientName: String, month: Int, amount: Double, dueDate: String, companyName: String) {
        val payload = JSONObject().apply {
            put("type", "EMAIL_REMINDER")
            put("recipientEmail", recipientEmail)
            put("clientName", clientName)
            put("month", month)
            put("amount", amount)
            put("dueDate", dueDate)
            put("companyName", companyName)
            put("timestamp", System.currentTimeMillis())
        }

        val config = getConfig()
        val msg = OutboxQueueMessage(
            id = "msg_${UUID.randomUUID()}",
            queueName = config.emailQueueName,
            payloadJson = payload.toString()
        )
        rabbitMqRepository.enqueueMessage(msg)
    }

    fun enqueueSyncJob(entityType: String, action: String, entityId: String, dataJson: String) {
        val payload = JSONObject().apply {
            put("type", "DATA_SYNC")
            put("entityType", entityType)
            put("action", action)
            put("entityId", entityId)
            put("data", dataJson)
            put("timestamp", System.currentTimeMillis())
        }

        val config = getConfig()
        val msg = OutboxQueueMessage(
            id = "sync_${UUID.randomUUID()}",
            queueName = config.syncQueueName,
            payloadJson = payload.toString()
        )
        rabbitMqRepository.enqueueMessage(msg)
    }

    fun clearAllQueued(): Unit = rabbitMqRepository.clearQueue()
}
