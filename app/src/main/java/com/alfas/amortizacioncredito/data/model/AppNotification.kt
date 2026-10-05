package com.alfas.amortizacioncredito.data.model

enum class NotificationType {
    DUE_SOON,        // Cuota vence en los próximos días
    OVERDUE,         // Cuota vencida
    PAYMENT_RECEIVED,// Pago registrado
    SYSTEM_UPDATE,   // Actualización de APK disponible
    AUDIT_ALERT      // Cambio importante en cartera
}

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val targetClientId: String? = null,
    val targetMonth: Int? = null
)
