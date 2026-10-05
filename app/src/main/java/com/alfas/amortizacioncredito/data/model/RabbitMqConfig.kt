package com.alfas.amortizacioncredito.data.model

import com.alfas.amortizacioncredito.config.AppConfig

data class RabbitMqConfig(
    val host: String = AppConfig.RabbitMq.DEFAULT_HOST,
    val port: Int = AppConfig.RabbitMq.DEFAULT_PORT,
    val virtualHost: String = AppConfig.RabbitMq.DEFAULT_VHOST,
    val username: String = AppConfig.RabbitMq.DEFAULT_USER,
    val password: String = AppConfig.RabbitMq.DEFAULT_PASSWORD,
    val isEnabled: Boolean = false,
    val emailQueueName: String = AppConfig.RabbitMq.DEFAULT_EMAIL_QUEUE,
    val syncQueueName: String = AppConfig.RabbitMq.DEFAULT_SYNC_QUEUE,
    val useSsl: Boolean = false
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && username.isNotBlank()
}

data class OutboxQueueMessage(
    val id: String,
    val queueName: String,
    val payloadJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val status: String = "PENDING" // PENDING, SENT, FAILED
)
